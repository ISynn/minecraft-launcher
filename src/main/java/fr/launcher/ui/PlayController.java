package fr.launcher.ui;

import fr.launcher.core.auth.Account;
import fr.launcher.core.download.Progress;
import fr.launcher.core.game.GameInstaller;
import fr.launcher.core.game.GameWindowWatcher;
import fr.launcher.core.log.Log;
import fr.launcher.ui.components.Toasts;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.util.Duration;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestration du bouton « Jouer » : installation, lancement, suivi du jeu.
 *
 * <p>La phase affichée découle de ce qui se passe réellement : étape de l'installation, octets reçus
 * du réseau, processus du jeu et apparition de sa fenêtre.</p>
 */
public final class PlayController {

    private static final Logger LOG = Log.get(PlayController.class);
    /** Au-delà, on considère le jeu lancé même sans avoir vu sa fenêtre (sortie console inhabituelle). */
    private static final java.time.Duration WINDOW_TIMEOUT = java.time.Duration.ofMinutes(4);

    public enum Phase {
        /** Prêt à jouer. */
        READY,
        /** Recherche des mises à jour, vérification des fichiers déjà présents. */
        PREPARING,
        /** Première installation : des fichiers sont téléchargés. */
        DOWNLOADING,
        /** Installation existante dont des fichiers changent (jeu, Java, mods du serveur). */
        UPDATING,
        /** Le processus du jeu démarre, sa fenêtre n'est pas encore ouverte. */
        LAUNCHING,
        /** La fenêtre du jeu est ouverte. */
        RUNNING,
        /** La dernière tentative a échoué : le bouton propose de réessayer. */
        ERROR;

        public boolean busy() {
            return this != READY && this != ERROR;
        }
    }

    private final AppContext app;
    private final Progress progress = new Progress();
    private final ReadOnlyObjectWrapper<Phase> phase = new ReadOnlyObjectWrapper<>(Phase.READY);
    private final ReadOnlyStringWrapper error = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper versionLabel = new ReadOnlyStringWrapper("");
    private final BooleanBinding busy = Bindings.createBooleanBinding(() -> phase.get().busy(), phase);
    /** Pendant l'installation, la phase est recalculée quelques fois par seconde à partir de la progression. */
    private final Timeline installTicker = new Timeline(new KeyFrame(Duration.millis(150), e -> refreshInstallPhase()));
    private boolean installedBefore;

    PlayController(AppContext app) {
        this.app = app;
        installTicker.setCycleCount(Timeline.INDEFINITE);
        phase.addListener((o, old, now) -> LOG.info("Phase : " + old + " → " + now));
    }

    public void play() {
        run(true, false);
    }

    /** Revérifie chaque fichier par SHA-1 et retélécharge ce qui est abîmé, sans lancer le jeu. */
    public void verify() {
        run(false, true);
    }

    private void run(boolean launch, boolean deepVerify) {
        if (phase.get().busy()) return;
        progress.reset();
        error.set("");
        installedBefore = app.core.installer.hasInstallation();
        phase.set(Phase.PREPARING);
        installTicker.play();
        LOG.info(launch ? "Jouer : préparation (installation existante : " + installedBefore + ")"
                : "Vérification approfondie des fichiers");

        Fx.async(() -> {
            Account account = app.freshAccount();
            GameInstaller.Installation installation = app.core.installer.install(progress, deepVerify);
            Platform.runLater(() -> versionLabel.set(installation.profile().displayName()));
            if (!launch) return null;
            Platform.runLater(() -> {
                installTicker.stop();
                phase.set(Phase.LAUNCHING);
            });
            return app.core.launcher.launch(installation, account, app.core.settings);
        }, process -> {
            installTicker.stop();
            if (process == null) {
                phase.set(Phase.READY);
                app.toast(Toasts.Kind.SUCCESS, "Fichiers vérifiés", "Tout est en ordre.");
            } else {
                onStarted(process);
            }
        }, failure -> {
            installTicker.stop();
            LOG.log(Level.WARNING, launch ? "Lancement impossible" : "Vérification impossible", failure);
            error.set(Fx.message(failure));
            phase.set(Phase.ERROR);
        });
    }

    private void refreshInstallPhase() {
        if (!phase.get().busy() || phase.get() == Phase.LAUNCHING) return;
        Phase next;
        if (!progress.isDownloading()) {
            next = Phase.PREPARING;
        } else if (installedBefore) {
            next = Phase.UPDATING;
        } else {
            next = Phase.DOWNLOADING;
        }
        if (next != phase.get()) phase.set(next);
    }

    private void onStarted(Process process) {
        phase.set(Phase.LAUNCHING);
        LOG.info("Minecraft démarré, pid " + process.pid());
        process.onExit().thenAccept(p -> Platform.runLater(() -> onExit(p.exitValue())));
        Thread.ofVirtual().name("game-window-watcher").start(() -> {
            try {
                boolean window = GameWindowWatcher.waitForWindow(app.core.launcher.outputLog(), process, WINDOW_TIMEOUT);
                if (process.isAlive()) Platform.runLater(() -> onWindow(window));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void onWindow(boolean detected) {
        if (phase.get() != Phase.LAUNCHING) return;
        LOG.info(detected ? "Fenêtre du jeu ouverte" : "Fenêtre du jeu non détectée, jeu considéré comme lancé");
        phase.set(Phase.RUNNING);
        // Comme le launcher officiel : on se cache quand le joueur voit le jeu, pas avant.
        if (app.core.settings.hideWhilePlaying) app.shell().hideForGame();
    }

    private void onExit(int code) {
        LOG.info("Minecraft fermé, code " + code);
        app.shell().restoreAfterGame();
        if (code == 0) {
            phase.set(Phase.READY);
        } else {
            error.set("Le jeu s'est arrêté de façon inattendue (code " + code + "). Détails : logs/game-output.log");
            phase.set(Phase.ERROR);
        }
    }

    public Progress progress() {
        return progress;
    }

    public ReadOnlyObjectProperty<Phase> phaseProperty() {
        return phase.getReadOnlyProperty();
    }

    public BooleanBinding busyProperty() {
        return busy;
    }

    /** Message de la dernière erreur (vide si aucune). */
    public ReadOnlyStringProperty errorProperty() {
        return error.getReadOnlyProperty();
    }

    /** « Minecraft 26.3 · Fabric 0.20.1 » une fois la version résolue, vide avant. */
    public ReadOnlyStringProperty versionLabelProperty() {
        return versionLabel.getReadOnlyProperty();
    }
}
