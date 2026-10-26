package fr.launcher.ui.shell;

import fr.launcher.ui.Fx;
import fr.launcher.ui.components.Ui;
import javafx.animation.TranslateTransition;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Rail de navigation vertical, à droite comme sur le launcher de référence.
 * En haut les écrans du launcher, en bas les liens externes (site, boutique, Discord).
 * Un indicateur jaune glisse vers l'écran actif.
 */
public final class NavRail extends StackPane {

    /** Lien externe affiché dans le rail. */
    public record External(String icon, String label, Runnable action) {
    }

    private static final double INDICATOR_HEIGHT = 20;

    private final Map<ScreenId, ToggleButton> buttons = new EnumMap<>(ScreenId.class);
    private final VBox internal = new VBox(6);
    private final Region separator = new Region();
    private final Region indicator = new Region();
    private final Pane overlay = new Pane(indicator);
    private ToggleButton selected;
    private double indicatorY = Double.NaN;

    public NavRail(ObservableValue<ScreenId> current, Consumer<ScreenId> onNavigate, External... externals) {
        getStyleClass().add("nav-rail");
        setMaxHeight(Region.USE_PREF_SIZE);

        ToggleGroup group = new ToggleGroup();
        internal.setAlignment(Pos.TOP_CENTER);
        for (ScreenId id : ScreenId.values()) {
            ToggleButton button = new ToggleButton();
            button.setGraphic(Fx.icon(id.icon, 22));
            button.getStyleClass().add("nav-item");
            button.setToggleGroup(group);
            button.setTooltip(Ui.tooltip(id.label));
            button.setAccessibleText(id.label);
            button.setOnAction(e -> onNavigate.accept(id));
            buttons.put(id, button);
            internal.getChildren().add(button);
        }
        // Un bouton de navigation ne se désélectionne jamais en recliquant dessus.
        group.selectedToggleProperty().addListener((o, old, now) -> {
            if (now == null && old != null && selected == old) old.setSelected(true);
        });

        separator.getStyleClass().add("nav-separator");

        VBox links = new VBox(6);
        links.setAlignment(Pos.TOP_CENTER);
        for (External link : externals) {
            Button button = new Button();
            button.setGraphic(Fx.icon(link.icon(), 22));
            button.getStyleClass().addAll("nav-item", "nav-external");
            button.setTooltip(Ui.tooltip(link.label() + " ↗"));
            button.setAccessibleText(link.label() + " (ouvre le navigateur)");
            button.setOnAction(e -> link.action().run());
            links.getChildren().add(button);
        }

        VBox content = new VBox(10, internal, separator, links);
        content.setAlignment(Pos.TOP_CENTER);

        indicator.getStyleClass().add("nav-indicator");
        indicator.setManaged(false);
        indicator.resize(3, INDICATOR_HEIGHT);
        indicator.setOpacity(0);
        overlay.setMouseTransparent(true);
        overlay.setManaged(false);
        getChildren().addAll(content, overlay);

        current.addListener((o, old, id) -> select(id));
        select(current.getValue());
    }

    /** Masque les écrans internes (écran de connexion) ; les liens externes restent accessibles. */
    public void setInternalVisible(boolean visible) {
        internal.setVisible(visible);
        internal.setManaged(visible);
        separator.setVisible(visible);
        separator.setManaged(visible);
        indicator.setVisible(visible);
    }

    private void select(ScreenId id) {
        selected = id == null ? null : buttons.get(id);
        buttons.values().forEach(b -> b.setSelected(b == selected));
        indicator.setOpacity(selected == null ? 0 : 1);
        requestLayout();
    }

    /** Place l'indicateur après la mise en page, quand la position réelle du bouton est connue. */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        overlay.resizeRelocate(0, 0, getWidth(), getHeight());
        if (selected == null || selected.getHeight() == 0) return;
        Bounds b = overlay.sceneToLocal(selected.localToScene(selected.getBoundsInLocal()));
        indicator.setLayoutX(1);
        double target = b.getMinY() + (b.getHeight() - INDICATOR_HEIGHT) / 2;
        if (target == indicatorY) return;
        if (Double.isNaN(indicatorY)) {
            indicator.setTranslateY(target);
        } else {
            TranslateTransition slide = new TranslateTransition(Duration.millis(300), indicator);
            slide.setToY(target);
            slide.setInterpolator(Fx.EASE_OUT);
            slide.play();
        }
        indicatorY = target;
    }
}
