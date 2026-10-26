package fr.launcher.ui.components;

import fr.launcher.ui.Fx;
import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Cursor;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/** Interrupteur on/off animé, utilisable à la souris et au clavier (Tab puis Espace ou Entrée). */
public final class Switch extends StackPane {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final double TRAVEL = 18;

    private final BooleanProperty selected = new SimpleBooleanProperty();
    private final Region thumb = new Region();

    public Switch(boolean initial, String accessibleLabel) {
        getStyleClass().add("switch");
        Region track = new Region();
        track.getStyleClass().add("track");
        thumb.getStyleClass().add("thumb");
        thumb.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(thumb, Pos.CENTER_LEFT);
        StackPane.setMargin(thumb, new Insets(0, 0, 0, 3));
        getChildren().addAll(track, thumb);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        setCursor(Cursor.HAND);
        setFocusTraversable(true);
        setAccessibleRole(AccessibleRole.CHECK_BOX);
        setAccessibleText(accessibleLabel);

        selected.addListener((o, was, now) -> {
            pseudoClassStateChanged(SELECTED, now);
            TranslateTransition move = new TranslateTransition(Duration.millis(220), thumb);
            move.setToX(now ? TRAVEL : 0);
            move.setInterpolator(Fx.EASE_OUT);
            move.play();
        });
        selected.set(initial);
        thumb.setTranslateX(initial ? TRAVEL : 0);
        pseudoClassStateChanged(SELECTED, initial);
        setOnMouseClicked(e -> {
            requestFocus();
            toggle();
        });
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
                toggle();
                e.consume();
            }
        });
    }

    private void toggle() {
        selected.set(!selected.get());
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }
}
