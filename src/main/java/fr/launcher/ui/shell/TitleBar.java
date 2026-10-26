package fr.launcher.ui.shell;

import fr.launcher.LauncherConfig;
import fr.launcher.ui.Fx;
import fr.launcher.ui.Icons;
import fr.launcher.ui.components.Ui;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/** Barre de titre personnalisée : nom au centre, mention « prototype », réduire et fermer. Glisser pour déplacer. */
final class TitleBar extends StackPane {

    private double dragX;
    private double dragY;

    TitleBar(Stage stage, Runnable onClose) {
        getStyleClass().add("title-bar");

        Label title = new Label(LauncherConfig.WINDOW_TITLE.toUpperCase());
        title.getStyleClass().add("title-text");
        title.setMouseTransparent(true);

        Label disclaimer = Ui.badge(LauncherConfig.DISCLAIMER, Ui.Tone.DEMO);
        disclaimer.setTooltip(Ui.tooltip("Projet indépendant, ni édité ni approuvé par PokeIsland, Mojang ou Microsoft."));
        HBox left = new HBox(disclaimer);
        left.setAlignment(Pos.CENTER_LEFT);
        left.setPickOnBounds(false);

        Button minimize = windowButton(Icons.MINIMIZE, "Réduire", "minimize");
        minimize.setOnAction(e -> stage.setIconified(true));
        Button close = windowButton(Icons.CLOSE, "Fermer", "close");
        close.setOnAction(e -> onClose.run());
        HBox right = new HBox(2, minimize, close);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setPickOnBounds(false);

        HBox sides = new HBox(left, Fx.spacer(), right);
        sides.setAlignment(Pos.CENTER);
        sides.setPickOnBounds(false);
        getChildren().addAll(title, sides);

        setOnMousePressed(e -> {
            dragX = stage.getX() - e.getScreenX();
            dragY = stage.getY() - e.getScreenY();
        });
        setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() + dragX);
            stage.setY(e.getScreenY() + dragY);
        });
    }

    private static Button windowButton(String icon, String label, String kind) {
        Button button = new Button();
        button.setGraphic(Fx.icon(icon, 16));
        button.getStyleClass().addAll("window-button", kind);
        button.setFocusTraversable(false);
        button.setAccessibleText(label);
        return button;
    }
}
