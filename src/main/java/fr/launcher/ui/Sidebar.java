package fr.launcher.ui;

import fr.launcher.ui.components.Avatar;
import javafx.animation.TranslateTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/** Barre de navigation verticale avec un indicateur lumineux qui glisse vers la page active. */
final class Sidebar extends StackPane {

    private static final double INDICATOR_HEIGHT = 22;

    private final Map<Page, ToggleButton> buttons = new EnumMap<>(Page.class);
    private final Region indicator = new Region();
    private final Pane overlay = new Pane(indicator);
    private ToggleButton selected;
    private double indicatorY = Double.NaN;

    Sidebar(AppContext app, Consumer<Page> onNavigate) {
        getStyleClass().add("sidebar");
        setMinWidth(76);
        setPrefWidth(76);
        setMaxWidth(76);

        ToggleGroup group = new ToggleGroup();
        VBox nav = new VBox(10);
        nav.setAlignment(Pos.TOP_CENTER);
        for (Page page : Page.values()) {
            ToggleButton button = new ToggleButton();
            button.setGraphic(Fx.icon(page.icon, 22));
            button.getStyleClass().add("nav-button");
            button.setToggleGroup(group);
            button.setTooltip(tooltip(page.label));
            button.setOnAction(e -> onNavigate.accept(page));
            buttons.put(page, button);
            nav.getChildren().add(button);
        }
        // Un bouton de navigation ne doit jamais pouvoir être désélectionné en recliquant dessus.
        group.selectedToggleProperty().addListener((o, old, now) -> {
            if (now == null && old != null) old.setSelected(true);
        });

        Avatar avatar = new Avatar(38, 10);
        Tooltip avatarTip = tooltip("");
        Tooltip.install(avatar, avatarTip);
        Consumer<fr.launcher.core.auth.Account> showAccount = account -> {
            avatar.setAccount(account);
            avatarTip.setText(account != null ? account.name() : "");
        };
        app.account.addListener((o, old, account) -> showAccount.accept(account));
        showAccount.accept(app.account.get());

        Button logout = new Button();
        logout.setGraphic(Fx.icon(Icons.LOGOUT, 19));
        logout.getStyleClass().addAll("nav-button", "logout-button");
        logout.setTooltip(tooltip("Se déconnecter"));
        logout.setOnAction(e -> app.logout());

        VBox content = new VBox(14, nav, Fx.spacer(), avatar, logout);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(18, 0, 18, 0));

        indicator.getStyleClass().add("nav-indicator");
        indicator.setManaged(false);
        indicator.resize(4, INDICATOR_HEIGHT);
        indicator.setOpacity(0);
        overlay.setMouseTransparent(true);
        getChildren().addAll(content, overlay);
    }

    void select(Page page) {
        selected = buttons.get(page);
        selected.setSelected(true);
        requestLayout();
    }

    /** Positionne l'indicateur après la mise en page, quand la position réelle du bouton est connue. */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (selected == null || selected.getHeight() == 0) return;
        Bounds b = overlay.sceneToLocal(selected.localToScene(selected.getBoundsInLocal()));
        double target = b.getMinY() + (b.getHeight() - INDICATOR_HEIGHT) / 2;
        if (target == indicatorY) return;
        if (Double.isNaN(indicatorY)) {
            indicator.setTranslateY(target);
            indicator.setOpacity(1);
        } else {
            TranslateTransition slide = new TranslateTransition(Duration.millis(380), indicator);
            slide.setToY(target);
            slide.setInterpolator(Fx.EASE_OUT);
            slide.play();
        }
        indicatorY = target;
    }

    private static Tooltip tooltip(String text) {
        Tooltip tip = new Tooltip(text);
        tip.setShowDelay(Duration.millis(250));
        return tip;
    }
}
