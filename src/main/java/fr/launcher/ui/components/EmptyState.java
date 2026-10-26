package fr.launcher.ui.components;

import fr.launcher.ui.Fx;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Écran vide ou en erreur : une icône, un titre, une explication et des actions. Jamais une zone blanche muette. */
public final class EmptyState extends VBox {

    public EmptyState(String icon, String title, String message, Node... actions) {
        this(false, icon, title, message, actions);
    }

    public static EmptyState error(String icon, String title, String message, Node... actions) {
        return new EmptyState(true, icon, title, message, actions);
    }

    private EmptyState(boolean error, String icon, String title, String message, Node... actions) {
        super(10);
        getStyleClass().add("empty-state");
        if (error) getStyleClass().add("empty-state-error");
        setAlignment(Pos.CENTER);
        setFillWidth(false);

        StackPane badge = new StackPane(Fx.icon(icon, 28));
        badge.getStyleClass().add("empty-icon");
        Label t = new Label(title);
        t.getStyleClass().add("empty-title");
        Label m = new Label(message);
        m.getStyleClass().add("empty-message");
        m.setWrapText(true);
        m.setMaxWidth(440);
        getChildren().addAll(badge, t, m);
        if (actions.length > 0) {
            HBox row = new HBox(10, actions);
            row.setAlignment(Pos.CENTER);
            row.getStyleClass().add("empty-actions");
            getChildren().add(row);
        }
    }
}
