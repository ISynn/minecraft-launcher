// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui;

import fr.launcher.core.LauncherCore;
import fr.launcher.core.auth.Account;
import fr.launcher.core.log.Log;
import fr.launcher.core.news.NewsArticle;
import fr.launcher.ui.shell.AppShell;
import fr.launcher.ui.shell.ScreenId;
import fr.launcher.util.Json;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Démarrage réel de l'interface (fenêtre, thème, rail, tous les écrans) dans un dossier temporaire :
 * chaque écran doit se construire sans afficher d'état d'erreur. Aucun accès à Internet.
 */
class LauncherStartupTest {

    private static boolean fxAvailable;

    @TempDir
    Path dir;

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
            fxAvailable = true;
        } catch (IllegalStateException alreadyStarted) {
            fxAvailable = true;
        } catch (RuntimeException | Error headless) {
            fxAvailable = false; // pas d'affichage (serveur d'intégration) : test ignoré
        }
    }

    @AfterEach
    void closeLog() {
        Log.close(); // sinon Windows refuse de supprimer le dossier temporaire (fichier de journal ouvert)
    }

    @Test
    void tousLesEcransSeConstruisentSansErreur() throws Exception {
        assumeTrue(fxAvailable, "affichage graphique indisponible");
        // Actualités déjà en cache (récentes) : l'accueil ne contacte pas le blog pendant le test.
        NewsArticle article = new NewsArticle("Article de test", "Info", "Test", "2026-10-26", "Résumé",
                "https://pokeisland.fr/blog/test/", "", false);
        Json.write(dir.resolve("launcher/cache/news.json"),
                Map.of("fetchedAt", Instant.now().toString(), "articles", List.of(article)));
        LauncherCore core = new LauncherCore(dir);

        List<String> problems = onFxThread(() -> {
            Stage stage = new Stage();
            AppContext app = new AppContext(core, stage, null);
            AppShell shell = new AppShell(app);
            app.attach(shell);
            LauncherUi.registerScreens(app, shell);
            List<String> errors = new ArrayList<>();

            shell.showLogin();
            check(stage, "connexion", errors);
            app.account.set(Account.offline("Testeur"));
            shell.showMain();
            for (ScreenId id : ScreenId.values()) {
                shell.navigate(id);
                check(stage, id.label, errors);
            }
            return errors;
        });
        assertEquals(List.of(), problems);
        assertTrue(core.dataDir.startsWith(dir), "le test n'a jamais touché au dossier du joueur");
    }

    private static void check(Stage stage, String screen, List<String> errors) {
        Parent root = stage.getScene().getRoot();
        root.applyCss();
        root.layout();
        if (root.lookup(".empty-state-error") != null) errors.add(screen + " affiche un état d'erreur");
    }

    private interface FxWork<T> {
        T run() throws Exception;
    }

    private static <T> T onFxThread(FxWork<T> work) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(work.run());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result.get(30, TimeUnit.SECONDS);
    }
}
