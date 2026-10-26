package fr.launcher.ui;

import fr.launcher.LauncherConfig;
import fr.launcher.core.LauncherCore;
import fr.launcher.core.auth.Account;
import fr.launcher.core.log.Log;
import fr.launcher.ui.components.Toasts;
import fr.launcher.ui.shell.AppShell;
import fr.launcher.ui.theme.Branding;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/** État partagé de l'interface : services métier, compte connecté, fenêtre, notifications. */
public final class AppContext {

    private static final Logger LOG = Log.get(AppContext.class);

    public final LauncherCore core;
    public final Stage stage;
    public final HostServices host;
    public final Branding branding;
    public final ObjectProperty<Account> account = new SimpleObjectProperty<>();
    public final PlayController play = new PlayController(this);
    public final NewsStore news;
    private AppShell shell;

    public AppContext(LauncherCore core, Stage stage, HostServices host) {
        this.core = core;
        this.stage = stage;
        this.host = host;
        this.branding = new Branding(core.dataDir);
        this.news = new NewsStore(core.news);
    }

    public void attach(AppShell shell) {
        this.shell = shell;
    }

    public AppShell shell() {
        return shell;
    }

    public void toast(Toasts.Kind kind, String title, String message) {
        shell.toasts().show(kind, title, message);
    }

    /** Erreur montrée à l'utilisateur et toujours consignée dans le journal, avec sa trace complète. */
    public void error(String title, Throwable t) {
        LOG.log(Level.WARNING, title, t);
        toast(Toasts.Kind.ERROR, title, Fx.message(t));
    }

    public void login(Account account) {
        try {
            core.accounts.save(account);
        } catch (IOException e) {
            error("Compte non mémorisé", e);
        }
        LOG.info("Connecté : " + account.name() + " (" + account.type() + ")");
        this.account.set(account);
        shell.showMain();
        toast(Toasts.Kind.SUCCESS, "Connecté", "Bienvenue " + account.name() + " !");
    }

    public void logout() {
        try {
            core.accounts.clear();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Fichier de compte non supprimé", e);
        }
        LOG.info("Déconnexion");
        account.set(null);
        shell.showLogin();
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

    /**
     * Ouvre une page web, uniquement en HTTPS et sur un domaine de confiance
     * ({@link LauncherConfig#TRUSTED_HOSTS}).
     */
    public void open(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            uri = null;
        }
        String host = uri == null || uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (uri == null || !"https".equals(uri.getScheme()) || !LauncherConfig.TRUSTED_HOSTS.contains(host)) {
            LOG.warning("Lien refusé (domaine non autorisé) : " + url);
            toast(Toasts.Kind.ERROR, "Lien bloqué", "Ce lien ne fait pas partie des sites autorisés.");
            return;
        }
        LOG.info("Ouverture du lien " + url);
        this.host.showDocument(uri.toString());
    }

    /** Ouvre un dossier local dans l'explorateur, en le créant au besoin. */
    public void openFolder(Path dir) {
        try {
            Files.createDirectories(dir);
            host.showDocument(dir.toUri().toString());
        } catch (IOException e) {
            error("Dossier inaccessible", e);
        }
    }
}
