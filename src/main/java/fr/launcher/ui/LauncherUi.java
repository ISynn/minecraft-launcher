package fr.launcher.ui;

import fr.launcher.core.auth.Account;
import fr.launcher.util.Http;
import javafx.application.HostServices;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;

/** Démarrage de l'interface : polices, fenêtre, puis restauration de la session précédente. */
public final class LauncherUi {

    private static final String[] FONTS = {"Regular", "Medium", "SemiBold", "Bold", "ExtraBold"};

    private LauncherUi() {
    }

    public static void start(Stage stage, HostServices host) {
        loadFonts();
        AppContext app = new AppContext(stage, host);
        AppWindow window = new AppWindow(app);

        Account saved = app.core.accounts.load().orElse(null);
        if (saved == null) {
            window.showLogin();
        } else {
            app.account.set(saved);
            window.showMain();
            refreshInBackground(app, saved);
        }
        window.show();
    }

    /** Valide la session au démarrage ; si Microsoft la refuse, on renvoie vers l'écran de connexion. */
    private static void refreshInBackground(AppContext app, Account saved) {
        if (!saved.needsRefresh()) return;
        Fx.async(app::freshAccount, account -> { }, error -> {
            if (error instanceof Http.HttpException http && http.status / 100 == 4) {
                app.logout();
                app.error("Session expirée", new IOException("Reconnecte-toi avec ton compte Microsoft."));
            }
        });
    }

    private static void loadFonts() {
        for (String weight : FONTS) {
            try (InputStream in = LauncherUi.class.getResourceAsStream(
                    "/fr/launcher/assets/fonts/Poppins-" + weight + ".ttf")) {
                if (in != null) Font.loadFont(in, 13);
            } catch (IOException ignored) {
                // police système en secours
            }
        }
    }
}
