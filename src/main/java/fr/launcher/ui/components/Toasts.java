// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui.components;

import fr.launcher.ui.Fx;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Notifications discrètes qui descendent en haut au centre et disparaissent seules. Cet espace sépare le titre
 * de chaque écran (à gauche) de ses actions (à droite) : une notification ne masque jamais un bouton.
 */
public final class Toasts extends VBox {

    public enum Kind { INFO, SUCCESS, ERROR }

    public Toasts() {
        getStyleClass().add("toasts");
        setAlignment(Pos.TOP_CENTER);
        setSpacing(10);
        setPadding(new Insets(0, 24, 24, 0));
        setPickOnBounds(false);
    }

    public void show(Kind kind, String title, String message) {
        Region dot = new Region();
        dot.getStyleClass().addAll("toast-dot", kind.name().toLowerCase());
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("toast-title");
        Label messageLabel = new Label(message);
        messageLabel.getStyleClass().add("toast-message");
        messageLabel.setWrapText(true);
        VBox text = new VBox(2, titleLabel, messageLabel);
        HBox toast = new HBox(12, dot, text);
        toast.getStyleClass().add("toast");
        toast.setAlignment(Pos.TOP_LEFT);
        toast.setMaxWidth(340);
        toast.setOnMouseClicked(e -> getChildren().remove(toast));

        toast.setOpacity(0);
        toast.setTranslateY(-16);
        getChildren().add(toast);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toast);
        fadeIn.setToValue(1);
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(450), toast);
        slideIn.setToY(0);
        ParallelTransition in = new ParallelTransition(fadeIn, slideIn);
        in.setInterpolator(Fx.EASE_OUT);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), toast);
        fadeOut.setToValue(0);
        TranslateTransition slideOut = new TranslateTransition(Duration.millis(300), toast);
        slideOut.setToY(-12);
        ParallelTransition out = new ParallelTransition(fadeOut, slideOut);

        double seconds = kind == Kind.ERROR ? 7 : 4;
        SequentialTransition life = new SequentialTransition(in, new PauseTransition(Duration.seconds(seconds)), out);
        life.setOnFinished(e -> getChildren().remove(toast));
        life.play();
    }
}
