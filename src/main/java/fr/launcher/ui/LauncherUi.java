// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui;

import fr.launcher.LauncherConfig;
import fr.launcher.core.LauncherCore;
import fr.launcher.core.auth.Account;
import fr.launcher.core.log.Log;
import fr.launcher.ui.components.Toasts;
import fr.launcher.ui.screens.account.AccountScreen;
import fr.launcher.ui.screens.help.HelpScreen;
import fr.launcher.ui.screens.home.HomeScreen;
import fr.launcher.ui.screens.install.InstallScreen;
import fr.launcher.ui.screens.login.LoginScreen;
import fr.launcher.ui.screens.news.NewsScreen;
import fr.launcher.ui.screens.settings.SettingsScreen;
import fr.launcher.ui.shell.AppShell;
import fr.launcher.ui.shell.ScreenId;
import fr.launcher.ui.theme.Theme;
import fr.launcher.util.Http;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Démarrage de l'interface : journal, thème, fenêtre, écrans, puis restauration de la session précédente. */
public final class LauncherUi {

    private static final Logger LOG = Log.get(LauncherUi.class);

    private LauncherUi() {
    }

    public static void start(Stage stage, HostServices host) {
        long started = System.nanoTime();
        LauncherCore core;
        try {
            core = new LauncherCore();
        } catch (RuntimeException e) {
            fatal(e);
            return;
        }
        LOG.info(LauncherConfig.WINDOW_TITLE + " " + LauncherConfig.VERSION + " · Java " + Runtime.version()
                + " · " + System.getProperty("os.name") + " · jeu dans " + core.gameDir);
        if (!core.instanceLock.acquire()) {
            LOG.info("Le launcher est déjà ouvert : cette seconde instance se ferme");
            dialog(Alert.AlertType.INFORMATION, "Le launcher est déjà ouvert",
                    "Une seule fenêtre à la fois : deux launchers installeraient le jeu en même temps "
                            + "dans le même dossier. Retrouve la fenêtre ouverte dans ta barre des tâches.");
            Platform.exit();
            return;
        }

        long coreReady = System.nanoTime();
        try {
            Theme.loadFonts();
            long fontsReady = System.nanoTime();
            AppContext app = new AppContext(core, stage, host);
            AppShell shell = new AppShell(app);
            app.attach(shell);
            installErrorHandler(app);

            registerScreens(app, shell);

            Account saved = core.accounts.load().orElse(null);
            if (saved == null) {
                shell.showLogin();
            } else {
                app.account.set(saved);
                shell.showMain();
                refreshInBackground(app, saved);
            }
            long screensReady = System.nanoTime();
            shell.show();
            long shown = System.nanoTime();
            LOG.info(String.format("Fenêtre affichée en %d ms (services %d, polices %d, écrans %d, affichage %d)",
                    ms(shown - started), ms(coreReady - started), ms(fontsReady - coreReady),
                    ms(screensReady - fontsReady), ms(shown - screensReady)));
        } catch (RuntimeException e) {
            fatal(e);
        }
    }

    /** Tous les écrans du launcher (utilisé aussi par le test de démarrage). */
    static void registerScreens(AppContext app, AppShell shell) {
        shell.register(ScreenId.HOME, () -> new HomeScreen(app));
        shell.register(ScreenId.NEWS, () -> new NewsScreen(app));
        shell.register(ScreenId.INSTALL, () -> new InstallScreen(app));
        shell.register(ScreenId.ACCOUNT, () -> new AccountScreen(app));
        shell.register(ScreenId.SETTINGS, () -> new SettingsScreen(app));
        shell.register(ScreenId.HELP, () -> new HelpScreen(app));
        shell.setLoginScreen(() -> new LoginScreen(app));
    }

    /** Boîte de dialogue système dont le texte s'affiche en entier (retour à la ligne, hauteur adaptée). */
    private static void dialog(Alert.AlertType type, String header, String text) {
        Alert alert = new Alert(type);
        alert.setTitle(LauncherConfig.WINDOW_TITLE);
        alert.setHeaderText(header);
        Label content = new Label(text);
        content.setWrapText(true);
        content.setMaxWidth(440);
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.showAndWait();
    }

    private static long ms(long nanos) {
        return nanos / 1_000_000;
    }

    /** Toute exception non rattrapée est consignée ; sur le thread graphique, l'utilisateur en est informé. */
    private static void installErrorHandler(AppContext app) {
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            LOG.log(Level.SEVERE, "Erreur non gérée sur " + thread.getName(), e);
            Platform.runLater(() -> app.toast(Toasts.Kind.ERROR, "Erreur inattendue",
                    Fx.message(e) + " — détails dans le journal du launcher."));
        });
    }

    /** Valide la session au démarrage ; si Microsoft la refuse, on renvoie vers l'écran de connexion. */
    private static void refreshInBackground(AppContext app, Account saved) {
        if (!saved.needsRefresh()) return;
        Fx.async(app::freshAccount, account -> LOG.info("Session rafraîchie"), error -> {
            if (error instanceof Http.HttpException http && http.status / 100 == 4) {
                app.logout();
                app.error("Session expirée", new IOException("Reconnecte-toi avec ton compte Microsoft."));
            } else {
                LOG.log(Level.WARNING, "Session non rafraîchie (réseau ?) : nouvel essai au clic sur Jouer", error);
            }
        });
    }

    /** Le launcher n'a pas pu démarrer : on le dit clairement plutôt que de fermer en silence. */
    private static void fatal(Throwable e) {
        LOG.log(Level.SEVERE, "Démarrage impossible", e);
        dialog(Alert.AlertType.ERROR, "Le launcher n'a pas pu démarrer",
                Fx.message(e) + "\n\nLes détails sont dans le journal : %APPDATA%\\." + LauncherConfig.DIR_NAME
                        + "\\launcher\\logs");
        Platform.exit();
    }
}
