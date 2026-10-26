package fr.launcher.ui;

import fr.launcher.LauncherConfig;
import fr.launcher.core.auth.Account;
import fr.launcher.core.download.Progress;
import fr.launcher.core.log.Log;
import fr.launcher.core.game.GameInstaller;
import fr.launcher.ui.components.Toasts;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

import java.util.logging.Logger;

/** Orchestration du bouton « Jouer » : installation, lancement, suivi du processus du jeu. */
public final class PlayController {

    private static final Logger LOG = Log.get(PlayController.class);

    public enum State { IDLE, INSTALLING, RUNNING }

    private final AppContext app;
    private final Progress progress = new Progress();
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.IDLE);
    private final ReadOnlyStringWrapper versionLabel = new ReadOnlyStringWrapper(
            "Minecraft " + LauncherConfig.MINECRAFT_VERSION + " · Fabric");

    PlayController(AppContext app) {
        this.app = app;
    }

    public void play() {
        run(true, false);
    }

    /** Revérifie chaque fichier par SHA-1 et retélécharge ce qui est abîmé, sans lancer le jeu. */
    public void verify() {
        run(false, true);
    }

    private void run(boolean launch, boolean deepVerify) {
        if (state.get() != State.IDLE) return;
        state.set(State.INSTALLING);
        LOG.info(launch ? "Jouer : préparation de l'installation" : "Vérification approfondie des fichiers");

        Fx.async(() -> {
            Account account = app.freshAccount();
            GameInstaller.Installation installation = app.core.installer.install(progress, deepVerify);
            Platform.runLater(() -> versionLabel.set(installation.profile().displayName()));
            if (!launch) return null;
            return app.core.launcher.launch(installation, account, app.core.settings);
        }, process -> {
            if (process == null) {
                state.set(State.IDLE);
                app.toast(Toasts.Kind.SUCCESS, "Fichiers vérifiés", "Tout est en ordre.");
            } else {
                onStarted(process);
            }
        }, error -> {
            state.set(State.IDLE);
            app.error(launch ? "Lancement impossible" : "Vérification impossible", error);
        });
    }

    private void onStarted(Process process) {
        state.set(State.RUNNING);
        LOG.info("Minecraft démarré, pid " + process.pid());
        process.onExit().thenAccept(p -> Platform.runLater(() -> onExit(p.exitValue())));

        if (app.core.settings.hideWhilePlaying) {
            app.shell().hideForGame();
        } else {
            app.toast(Toasts.Kind.SUCCESS, "Bon jeu !", "Minecraft est en cours de démarrage.");
        }
    }

    private void onExit(int code) {
        state.set(State.IDLE);
        LOG.info("Minecraft fermé, code " + code);
        app.shell().restoreAfterGame();
        if (code != 0) {
            app.toast(Toasts.Kind.ERROR, "Le jeu a planté",
                    "Code " + code + ". Les détails sont dans le dossier logs du jeu.");
        }
    }

    public Progress progress() {
        return progress;
    }

    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty versionLabelProperty() {
        return versionLabel.getReadOnlyProperty();
    }
}
