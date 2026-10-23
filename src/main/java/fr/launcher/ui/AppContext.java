package fr.launcher.ui;

import fr.launcher.core.LauncherCore;
import fr.launcher.core.auth.Account;
import fr.launcher.ui.components.Toasts;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.stage.Stage;

import java.io.IOException;

/** État partagé de l'interface : compte connecté, fenêtre, notifications. */
public final class AppContext {

    public final LauncherCore core = new LauncherCore();
    public final Stage stage;
    public final HostServices host;
    public final ObjectProperty<Account> account = new SimpleObjectProperty<>();
    public final PlayController play = new PlayController(this);
    AppWindow window;

    public AppContext(Stage stage, HostServices host) {
        this.stage = stage;
        this.host = host;
    }

    public AppWindow window() {
        return window;
    }

    public void toast(Toasts.Kind kind, String title, String message) {
        window.toasts().show(kind, title, message);
    }

    public void error(String title, Throwable t) {
        toast(Toasts.Kind.ERROR, title, Fx.message(t));
    }

    public void login(Account account) {
        try {
            core.accounts.save(account);
        } catch (IOException e) {
            error("Compte non mémorisé", e);
        }
        this.account.set(account);
        window.showMain();
        toast(Toasts.Kind.SUCCESS, "Connecté", "Bienvenue " + account.name() + " !");
    }

    public void logout() {
        try {
            core.accounts.clear();
        } catch (IOException ignored) {
            // le fichier sera réécrit à la prochaine connexion
        }
        account.set(null);
        window.showLogin();
    }

    /**
     * Renvoie un compte avec un jeton valide, en le rafraîchissant si besoin.
     * À appeler hors du thread graphique (accès réseau).
     */
    public Account freshAccount() throws IOException, InterruptedException {
        Account current = account.get();
        if (current == null) throw new IOException("Aucun compte connecté.");
        if (!current.needsRefresh()) return current;
        Account refreshed = core.auth.refresh(current);
        core.accounts.save(refreshed);
        Platform.runLater(() -> account.set(refreshed));
        return refreshed;
    }

    public void open(String uri) {
        host.showDocument(uri);
    }
}
