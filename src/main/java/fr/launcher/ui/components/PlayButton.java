// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui.components;

import fr.launcher.ui.PlayController.Phase;
import javafx.animation.AnimationTimer;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

/**
 * Le bouton principal. Son libellé suit la phase réelle (JOUER, PRÉPARATION, TÉLÉCHARGEMENT, MISE À JOUR,
 * LANCEMENT, EN JEU, RÉESSAYER) et, pendant une installation, il se remplit comme une barre de progression.
 */
public final class PlayButton extends StackPane {

    private static final PseudoClass BUSY = PseudoClass.getPseudoClass("busy");
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
    private static final double RADIUS = 22;

    private final Button button = new Button();
    private final Region fill = new Region();
    private final Label title = new Label("JOUER");
    private final Label subtitle = new Label();
    private double target = -1;
    private double shown;

    /** Le remplissage glisse vers sa cible ; le minuteur ne tourne que pendant une installation. */
    private final AnimationTimer timer = new AnimationTimer() {
        private long last;

        @Override
        public void handle(long now) {
            double dt = last == 0 ? 0.016 : Math.min(0.05, (now - last) / 1e9);
            last = now;
            if (target >= 0) shown += (target - shown) * (1 - Math.exp(-dt * 6));
            requestLayout();
        }

        @Override
        public void stop() {
            super.stop();
            last = 0;
        }
    };

    public PlayButton(Runnable onPlay) {
        getStyleClass().add("play");
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        button.getStyleClass().add("play-base");
        button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        button.setOnAction(e -> onPlay.run());
        button.setAccessibleText("Jouer");

        fill.getStyleClass().add("play-fill");
        fill.setManaged(false);
        fill.setMouseTransparent(true);
        Rectangle clip = new Rectangle();
        clip.setArcWidth(RADIUS * 2);
        clip.setArcHeight(RADIUS * 2);
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        StackPane fillLayer = new StackPane(fill);
        fillLayer.setMouseTransparent(true);
        fillLayer.setClip(clip);

        title.getStyleClass().add("play-title");
        subtitle.getStyleClass().add("play-subtitle");
        subtitle.managedProperty().bind(subtitle.visibleProperty());
        VBox labels = new VBox(0, title, subtitle);
        labels.setAlignment(Pos.CENTER);
        labels.setMouseTransparent(true);

        getChildren().addAll(button, fillLayer, labels);
    }

    /**
     * @param phase    phase réelle du lancement
     * @param detail   ligne secondaire (vide = aucune)
     */
    public void show(Phase phase, String detail) {
        boolean busy = phase.busy();
        button.setDisable(busy);
        pseudoClassStateChanged(BUSY, busy);
        pseudoClassStateChanged(ERROR, phase == Phase.ERROR);
        title.setText(switch (phase) {
            case READY -> "JOUER";
            case PREPARING -> "PRÉPARATION";
            case DOWNLOADING -> "TÉLÉCHARGEMENT";
            case UPDATING -> "MISE À JOUR";
            case LAUNCHING -> "LANCEMENT";
            case RUNNING -> "EN JEU";
            case ERROR -> "RÉESSAYER";
        });
        button.setAccessibleText(title.getText() + (detail.isEmpty() ? "" : ", " + detail));
        subtitle.setText(detail);
        subtitle.setVisible(!detail.isEmpty());
        if (busy) {
            timer.start();
        } else {
            timer.stop();
            setProgress(-1);
        }
    }

    /** @param value entre 0 et 1, ou négatif pour masquer le remplissage */
    public void setProgress(double value) {
        if (value >= 0 && target < 0) shown = 0;
        target = value;
        if (value < 0) {
            shown = 0;
            requestLayout();
        }
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        fill.resizeRelocate(0, 0, target < 0 ? 0 : getWidth() * Math.min(1, shown), getHeight());
    }
}
