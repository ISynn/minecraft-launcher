package fr.launcher.ui.screens.home;

import fr.launcher.core.download.Progress;
import fr.launcher.ui.Fx;
import fr.launcher.ui.Icons;
import fr.launcher.ui.PlayController.Phase;
import fr.launcher.ui.components.SmoothProgressBar;
import fr.launcher.ui.components.Ui;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Locale;

/**
 * Bandeau sous les boutons : ce que fait le launcher en ce moment, en clair.
 * Prêt → version ; installation → étape, pourcentage, fichiers, débit ; lancement ; erreur → message et journal.
 */
final class StatusStrip extends VBox {

    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    private final Label headline = new Label();
    private final Label percent = new Label();
    private final Label detail = new Label();
    private final SmoothProgressBar bar = new SmoothProgressBar();
    private final Button logs;

    StatusStrip(Runnable openLogs) {
        super(6);
        getStyleClass().add("status-strip");
        headline.getStyleClass().add("strip-headline");
        percent.getStyleClass().add("strip-percent");
        detail.getStyleClass().add("strip-detail");
        logs = Ui.ghost("Ouvrir le journal", Icons.CONSOLE);
        logs.getStyleClass().add("strip-action");
        logs.setOnAction(e -> openLogs.run());
        logs.managedProperty().bind(logs.visibleProperty());

        HBox top = new HBox(10, headline, Fx.spacer(), percent, logs);
        top.setAlignment(Pos.CENTER_LEFT);
        // Hauteur fixe (CSS) et contenu centré : le bandeau ne change pas de taille d'un état à l'autre.
        setAlignment(Pos.CENTER_LEFT);
        bar.managedProperty().bind(bar.visibleProperty());
        detail.managedProperty().bind(detail.visibleProperty());
        getChildren().addAll(top, bar, detail);
    }

    /** État hors installation. */
    void show(Phase phase, String version, String error) {
        pseudoClassStateChanged(ERROR, phase == Phase.ERROR);
        logs.setVisible(phase == Phase.ERROR);
        percent.setText("");
        switch (phase) {
            case READY -> {
                headline.setText("Prêt à jouer");
                detail.setText(version);
                bar.setVisible(false);
                bar.stop();
            }
            case LAUNCHING -> {
                headline.setText("Démarrage de Minecraft…");
                detail.setText("Le chargement des mods peut prendre une minute. Le launcher se masquera à l'ouverture du jeu.");
                bar.setVisible(true);
                bar.start(); // indéterminé
            }
            case RUNNING -> {
                headline.setText("Minecraft est ouvert. Bon jeu !");
                detail.setText(version);
                bar.setVisible(false);
                bar.stop();
            }
            case ERROR -> {
                headline.setText("Une erreur est survenue");
                detail.setText(error);
                bar.setVisible(false);
                bar.stop();
            }
            default -> {
                // Préparation → téléchargement → mise à jour : la barre continue sans repartir de zéro.
                if (!bar.isVisible()) {
                    bar.setVisible(true);
                    bar.start();
                }
            }
        }
        detail.setVisible(!detail.getText().isBlank());
    }

    /** Pendant l'installation : appelé quelques fois par seconde. */
    void update(Phase phase, Progress p, double bytesPerSecond) {
        String verb = switch (phase) {
            case DOWNLOADING -> "Téléchargement";
            case UPDATING -> "Mise à jour";
            default -> p.stageIndex() <= 1 ? "Recherche des mises à jour" : "Vérification";
        };
        String stage = p.stageIndex() <= 1 ? "" : " · " + p.step();
        String count = p.stageCount() > 0 ? "  (" + p.stageIndex() + "/" + p.stageCount() + ")" : "";
        headline.setText(verb + stage + count);
        double f = p.fraction();
        bar.setProgress(f);
        percent.setText(f < 0 ? "" : Math.round(f * 100) + " %");

        StringBuilder sb = new StringBuilder();
        if (p.totalFiles() > 1) {
            sb.append(String.format(Locale.FRANCE, "%,d / %,d fichiers", p.doneFiles(), p.totalFiles()));
        }
        if (bytesPerSecond > 50_000) {
            if (!sb.isEmpty()) sb.append("  ·  ");
            sb.append(String.format(Locale.FRANCE, "%.1f Mo/s", bytesPerSecond / 1_048_576));
        }
        detail.setText(sb.toString());
        detail.setVisible(!sb.isEmpty());
    }
}
