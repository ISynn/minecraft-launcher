package fr.launcher.ui.components;

import fr.launcher.LauncherConfig;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/** Pastille de logo en dégradé avec l'initiale du launcher. Remplaçable par une image plus tard. */
public final class Logo {

    private Logo() {
    }

    public static StackPane mark(double size) {
        Label letter = new Label(LauncherConfig.NAME.substring(0, 1).toUpperCase());
        letter.getStyleClass().add("logo-letter");
        letter.setStyle("-fx-font-size: " + size * 0.5 + "px;");
        StackPane mark = new StackPane(letter);
        mark.getStyleClass().add("logo-mark");
        mark.setStyle("-fx-background-radius: " + size * 0.3 + "px;");
        mark.setMinSize(size, size);
        mark.setPrefSize(size, size);
        mark.setMaxSize(size, size);
        mark.setCache(true); // son ombre colorée n'est calculée qu'une fois
        return mark;
    }
}
