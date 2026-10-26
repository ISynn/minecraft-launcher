package fr.launcher.ui.screens.install;

import fr.launcher.LauncherConfig;
import fr.launcher.core.download.Progress;
import fr.launcher.core.game.GameInstaller;
import fr.launcher.ui.AppContext;
import fr.launcher.ui.Fx;
import fr.launcher.ui.Icons;
import fr.launcher.ui.PlayController;
import fr.launcher.ui.PlayController.Phase;
import fr.launcher.ui.PlayController.RunResult;
import fr.launcher.ui.components.SmoothProgressBar;
import fr.launcher.ui.components.Ui;
import fr.launcher.ui.shell.Screen;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Installation et mises à jour : étapes en direct, détails de l'installation, bilan de la dernière opération,
 * vérification complète et réparation.
 */
public final class InstallScreen implements Screen {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.FRANCE)
            .withZone(ZoneId.systemDefault());

    private final AppContext app;
    private final PlayController play;
    private final ScrollPane root;
    private final HBox statusBadge = new HBox();
    private final Label headline = new Label();
    private final Label percent = new Label();
    private final Label detail = Ui.muted("");
    private final SmoothProgressBar bar = new SmoothProgressBar();
    private final List<StageRow> stages = new ArrayList<>();
    private final VBox lastRun = new VBox(6);
    private final Label version = value("");
    private final Label java = value("…");
    private final Label disk = value("Calcul…");
    /** Rafraîchit les étapes 4 fois par seconde, seulement pendant une opération et quand l'écran est visible. */
    private final Timeline ticker = new Timeline(new KeyFrame(Duration.millis(250), e -> refresh()));
    private boolean shown;
    private boolean barRunning;

    public InstallScreen(AppContext app) {
        this.app = app;
        this.play = app.play;
        ticker.setCycleCount(Timeline.INDEFINITE);

        Button repair = Ui.primary("Vérifier et réparer", Icons.VERIFY);
        repair.setTooltip(Ui.tooltip(
                "Recalcule l'empreinte de chaque fichier et retélécharge ceux qui sont absents ou abîmés."));
        repair.setOnAction(e -> play.verify());
        repair.disableProperty().bind(play.busyProperty());
        Button cancel = Ui.secondary("Annuler", Icons.CLOSE);
        cancel.setOnAction(e -> play.cancel());
        cancel.visibleProperty().bind(play.phaseProperty().isEqualTo(Phase.PREPARING)
                .or(play.phaseProperty().isEqualTo(Phase.DOWNLOADING))
                .or(play.phaseProperty().isEqualTo(Phase.UPDATING)));
        cancel.managedProperty().bind(cancel.visibleProperty());
        statusBadge.setAlignment(Pos.CENTER);

        root = Ui.page(
                Ui.pageHeader("Installation", "État des fichiers du jeu, téléchargements et vérifications.",
                        statusBadge, cancel, repair),
                Ui.columns(progressCard(), new VBox(18, detailsCard(), lastRunCard())));

        play.phaseProperty().addListener((o, a, b) -> refresh());
        play.lastResultProperty().addListener((o, a, b) -> {
            refresh();
            refreshDetails();
        });
        play.versionLabelProperty().addListener((o, a, b) -> refreshDetails());
        refresh();
    }

    private Node progressCard() {
        headline.getStyleClass().add("strip-headline");
        percent.getStyleClass().add("strip-percent");
        HBox top = new HBox(10, headline, Fx.spacer(), percent);
        top.setAlignment(Pos.CENTER_LEFT);
        VBox list = new VBox(4);
        for (int i = 0; i < GameInstaller.STAGE_NAMES.size(); i++) {
            StageRow row = new StageRow(i + 1, GameInstaller.STAGE_NAMES.get(i));
            stages.add(row);
            list.getChildren().add(row);
        }
        bar.managedProperty().bind(bar.visibleProperty());
        return Ui.card(Icons.DOWNLOAD, "Étapes", top, bar, detail, list);
    }

    private Node detailsCard() {
        Button open = Ui.ghost("Ouvrir le dossier", Icons.FOLDER);
        open.setOnAction(e -> app.openFolder(app.core.gameDir));
        Label path = new Label(app.core.gameDir.toString());
        path.getStyleClass().add("path-label");
        path.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);
        grid.addRow(0, key("Version"), version);
        grid.addRow(1, key("Java"), java);
        grid.addRow(2, key("Espace utilisé"), disk);
        return Ui.card(Icons.FOLDER, "Installation", grid, path, open);
    }

    private Node lastRunCard() {
        return Ui.card(Icons.HOURGLASS, "Dernière opération", lastRun);
    }

    @Override
    public void onShow() {
        shown = true;
        refreshDetails();
        refresh();
    }

    @Override
    public void onHide() {
        shown = false;
        ticker.stop();
    }

    /** Détails lents à obtenir (parcours du disque) : calculés en arrière-plan à chaque affichage. */
    private void refreshDetails() {
        String label = play.versionLabelProperty().get();
        version.setText(label.isEmpty() ? "Minecraft " + LauncherConfig.MINECRAFT_VERSION + " · Fabric" : label);
        Fx.async(app.core.installer::installedRuntimes, runtimes -> java.setText(runtimes.isEmpty()
                ? "Pas encore installé" : "Mojang · " + String.join(", ", runtimes)), error -> java.setText("Inconnu"));
        // Le parcours du disque est plus long : calculé à part pour ne pas retarder le reste.
        Fx.async(app.core.installer::diskUsage, usage -> {
            long total = usage.values().stream().mapToLong(Long::longValue).sum();
            StringBuilder parts = new StringBuilder();
            usage.forEach((name, bytes) -> {
                if (bytes == 0) return;
                parts.append(parts.isEmpty() ? "" : " · ").append(name).append(' ').append(Fx.size(bytes));
            });
            disk.setText(total == 0 ? "Rien d'installé" : Fx.size(total) + "\n" + parts);
        }, error -> disk.setText("Indisponible"));
    }

    private void refresh() {
        Phase phase = play.phaseProperty().get();
        boolean installing = phase == Phase.PREPARING || phase == Phase.DOWNLOADING || phase == Phase.UPDATING;
        if (installing && shown) ticker.play();
        else ticker.stop();

        RunResult result = play.lastResultProperty().get();
        Progress p = play.progress();
        statusBadge.getChildren().setAll(statusBadge(phase, result));

        int current = p.stageIndex();
        for (StageRow row : stages) {
            StageRow.State state;
            String text;
            if (installing) {
                state = row.index < current ? StageRow.State.DONE
                        : row.index == current ? StageRow.State.RUNNING : StageRow.State.PENDING;
                text = row.index == current ? currentText(phase, p) : row.index < current ? "Terminé" : "En attente";
            } else if (result == null) {
                state = StageRow.State.PENDING;
                text = "Pas encore vérifié";
            } else if (result.success()) {
                state = StageRow.State.DONE;
                text = "Vérifié";
            } else if (result.cancelled()) {
                state = row.index < result.failedStage() ? StageRow.State.DONE : StageRow.State.PENDING;
                text = row.index < result.failedStage() ? "Vérifié"
                        : row.index == result.failedStage() ? "Interrompu" : "Non atteint";
            } else {
                state = row.index < result.failedStage() ? StageRow.State.DONE
                        : row.index == result.failedStage() ? StageRow.State.FAILED : StageRow.State.PENDING;
                text = row.index == result.failedStage() ? "Échec"
                        : row.index < result.failedStage() ? "Vérifié" : "Non atteint";
            }
            row.show(state, text);
        }

        if (installing) {
            bar.setVisible(true);
            if (!barRunning) {
                bar.start();
                barRunning = true;
            }
            double f = p.fraction();
            bar.setProgress(f);
            headline.setText((p.isOffline() ? "Hors ligne · " : "") + verb(phase, p) + " · étape " + current
                    + " sur " + p.stageCount());
            percent.setText(f < 0 ? "" : Math.round(f * 100) + " %");
            String downloaded = p.totalNetworkBytes() == 0
                    ? "aucun téléchargement pour l'instant"
                    : Fx.size(p.totalNetworkBytes()) + " téléchargés";
            detail.setText(p.totalFiles() > 1
                    ? String.format(Locale.FRANCE, "%,d / %,d fichiers · ", p.doneFiles(), p.totalFiles()) + downloaded
                    : downloaded);
        } else {
            bar.stop();
            barRunning = false;
            bar.setVisible(false);
            percent.setText("");
            headline.setText(switch (phase) {
                case LAUNCHING -> "Installation à jour · lancement du jeu en cours";
                case RUNNING -> "Installation à jour · Minecraft est ouvert";
                case ERROR -> "La dernière opération a échoué";
                default -> result == null
                        ? "Les fichiers sont vérifiés à chaque clic sur Jouer"
                        : "Installation à jour";
            });
            detail.setText(result == null
                    ? "Lance une vérification complète pour contrôler l'empreinte de chaque fichier."
                    : "");
        }
        detail.setVisible(!detail.getText().isEmpty());
        detail.setManaged(detail.isVisible());
        renderLastRun(result);
    }

    private static String verb(Phase phase, Progress p) {
        return switch (phase) {
            case DOWNLOADING -> "Téléchargement";
            case UPDATING -> "Mise à jour";
            default -> p.stageIndex() <= 1 ? "Recherche des mises à jour" : "Vérification";
        };
    }

    private static String currentText(Phase phase, Progress p) {
        String verb = phase == Phase.DOWNLOADING || phase == Phase.UPDATING ? "Téléchargement" : "Vérification";
        return p.totalFiles() > 1 ? String.format(Locale.FRANCE, "%s · %,d / %,d", verb, p.doneFiles(), p.totalFiles())
                : verb + "…";
    }

    private static Node statusBadge(Phase phase, RunResult result) {
        boolean installing = phase == Phase.PREPARING || phase == Phase.DOWNLOADING || phase == Phase.UPDATING;
        if (installing) return Ui.badge("En cours", Ui.Tone.ACCENT);
        if (result != null && result.cancelled() && phase == Phase.READY) return Ui.badge("Annulée", Ui.Tone.WARNING);
        if (phase == Phase.ERROR || (result != null && !result.success())) return Ui.badge("Erreur", Ui.Tone.DANGER);
        if (result != null) return Ui.badge("À jour", Ui.Tone.SUCCESS);
        return Ui.badge("Non vérifiée depuis l'ouverture", Ui.Tone.NEUTRAL);
    }

    private void renderLastRun(RunResult r) {
        if (r == null) {
            lastRun.getChildren().setAll(Ui.muted("Aucune vérification depuis l'ouverture du launcher. "
                    + "Clique sur Jouer ou sur « Vérifier et réparer »."));
            return;
        }
        String kind = r.deepVerify() ? "Vérification complète" : "Préparation avant lancement";
        Label title = new Label(kind + (r.success() ? " réussie" : r.cancelled() ? " annulée" : " échouée"));
        title.getStyleClass().add(r.success() ? "result-ok" : "result-failed");
        String downloaded = r.downloadedBytes() > 0
                ? Fx.size(r.downloadedBytes()) + " téléchargés"
                : "aucun téléchargement";
        Label facts = Ui.muted("À " + TIME.format(r.finishedAt()) + " · durée " + seconds(r.duration())
                + " · " + downloaded);
        lastRun.getChildren().setAll(title, facts);
        if (!r.success() && !r.cancelled()) {
            List<String> names = GameInstaller.STAGE_NAMES;
            String stage = names.get(Math.clamp(r.failedStage(), 1, names.size()) - 1);
            Label error = Ui.muted("Étape « " + stage + " » : " + r.error());
            error.getStyleClass().add("result-error");
            Button logs = Ui.ghost("Ouvrir le journal", Icons.CONSOLE);
            logs.setOnAction(e -> app.openFolder(app.core.logsDir));
            lastRun.getChildren().addAll(error, logs);
        }
    }

    private static String seconds(java.time.Duration d) {
        long s = Math.max(1, Math.round(d.toMillis() / 1000.0));
        return s < 60 ? s + " s" : (s / 60) + " min " + (s % 60) + " s";
    }

    private static Label key(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("detail-key");
        return label;
    }

    private static Label value(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("detail-value");
        label.setWrapText(true);
        return label;
    }

    /** Une étape : pastille d'état, nom, état en clair. */
    private static final class StageRow extends HBox {

        enum State { PENDING, RUNNING, DONE, FAILED }

        private static final PseudoClass RUNNING = PseudoClass.getPseudoClass("running");
        private static final PseudoClass DONE = PseudoClass.getPseudoClass("done");
        private static final PseudoClass FAILED = PseudoClass.getPseudoClass("failed");

        final int index;
        private final StackPane mark = new StackPane();
        private final Label status = new Label();

        StageRow(int index, String name) {
            super(12);
            this.index = index;
            getStyleClass().add("stage-row");
            setAlignment(Pos.CENTER_LEFT);
            mark.getStyleClass().add("stage-mark");
            Label label = new Label(name);
            label.getStyleClass().add("stage-name");
            status.getStyleClass().add("stage-status");
            getChildren().addAll(mark, label, Fx.spacer(), status);
        }

        void show(State state, String text) {
            pseudoClassStateChanged(RUNNING, state == State.RUNNING);
            pseudoClassStateChanged(DONE, state == State.DONE);
            pseudoClassStateChanged(FAILED, state == State.FAILED);
            mark.getChildren().setAll(Fx.icon(switch (state) {
                case DONE -> Icons.CHECK;
                case FAILED -> Icons.CLOSE;
                default -> Icons.DOT;
            }, 16));
            status.setText(text);
        }
    }

    @Override
    public Node view() {
        return root;
    }
}
