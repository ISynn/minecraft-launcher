package fr.launcher.ui;

import fr.launcher.LauncherConfig;
import fr.launcher.ui.components.AuroraBackground;
import fr.launcher.ui.components.Logo;
import fr.launcher.ui.components.Toasts;
import fr.launcher.ui.views.HomeView;
import fr.launcher.ui.views.LoginView;
import fr.launcher.ui.views.SettingsView;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Fenêtre sans bordure système : coins arrondis, ombre portée, barre de titre personnalisée.
 *
 * <p>Optimisation : sous Windows 11, la fenêtre est opaque et c'est le système qui arrondit les coins et
 * dessine l'ombre (une fenêtre transparente coûte ~3× plus de processeur). Ailleurs, l'ombre est portée par
 * un calque séparé mis en cache, pour ne pas être recalculée à chaque image du fond animé.</p>
 */
public final class AppWindow {

    public static final double WIDTH = 1100;
    public static final double HEIGHT = 680;
    private static final double SHADOW = 22;
    private static final double RADIUS = 16;

    private final AppContext app;
    private final AuroraBackground background = new AuroraBackground();
    private final BorderPane layout = new BorderPane();
    private final StackPane viewHost = new StackPane();
    private final Toasts toasts = new Toasts();
    private final StackPane root;

    private Sidebar sidebar;
    private LoginView loginView;
    private HomeView homeView;
    private SettingsView settingsView;
    private Node current;
    private FadeTransition leaving;
    private boolean decorated;
    private double dragX;
    private double dragY;

    AppWindow(AppContext app) {
        this.app = app;
        app.window = this;

        layout.setTop(titleBar());
        layout.setCenter(viewHost);
        viewHost.getStyleClass().add("view-host");

        StackPane frame = new StackPane(background, layout, toasts);
        frame.getStyleClass().add("window");
        Scene scene;
        if (NativeWindow.SUPPORTED) {
            // Windows 11 : fenêtre opaque, coins arrondis et ombre dessinés par le système.
            root = frame;
            scene = new Scene(root, WIDTH, HEIGHT, Color.web("#07080f"));
            app.stage.initStyle(StageStyle.UNDECORATED);
        } else {
            // Ailleurs : fenêtre transparente, coins et ombre dessinés par JavaFX.
            Region border = new Region();
            border.getStyleClass().add("window-border");
            border.setMouseTransparent(true);
            frame.getChildren().add(border);
            Rectangle clip = new Rectangle();
            clip.setArcWidth(RADIUS * 2);
            clip.setArcHeight(RADIUS * 2);
            clip.widthProperty().bind(frame.widthProperty());
            clip.heightProperty().bind(frame.heightProperty());
            frame.setClip(clip);

            Region shadow = new Region();
            shadow.getStyleClass().add("window-shadow");
            shadow.setCache(true);

            root = new StackPane(shadow, frame);
            root.getStyleClass().add("window-root");
            root.setPadding(new Insets(SHADOW));
            scene = new Scene(root, WIDTH + SHADOW * 2, HEIGHT + SHADOW * 2, Color.TRANSPARENT);
            app.stage.initStyle(StageStyle.TRANSPARENT);
        }
        scene.getStylesheets().add(AppWindow.class.getResource("/fr/launcher/assets/launcher.css").toExternalForm());

        app.stage.setTitle(LauncherConfig.NAME);
        app.stage.setResizable(false);
        app.stage.setScene(scene);
        scene.addEventFilter(MouseEvent.MOUSE_MOVED,
                e -> background.pointer(e.getSceneX() / scene.getWidth(), e.getSceneY() / scene.getHeight()));
        app.stage.iconifiedProperty().addListener((o, was, iconified) -> background.setPaused(iconified));
        background.setAnimated(app.core.settings.animatedBackground);
    }

    private Node titleBar() {
        Label name = new Label(LauncherConfig.NAME);
        name.getStyleClass().add("title-name");
        HBox brand = new HBox(10, Logo.mark(24), name);
        brand.setAlignment(Pos.CENTER_LEFT);

        Button minimize = windowButton(Icons.MINIMIZE, "minimize");
        minimize.setOnAction(e -> app.stage.setIconified(true));
        Button close = windowButton(Icons.CLOSE, "close");
        close.setOnAction(e -> close());

        HBox bar = new HBox(brand, Fx.spacer(), minimize, close);
        bar.getStyleClass().add("title-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setOnMousePressed(e -> {
            dragX = app.stage.getX() - e.getScreenX();
            dragY = app.stage.getY() - e.getScreenY();
        });
        bar.setOnMouseDragged(e -> {
            app.stage.setX(e.getScreenX() + dragX);
            app.stage.setY(e.getScreenY() + dragY);
        });
        return bar;
    }

    private static Button windowButton(String icon, String kind) {
        Button button = new Button();
        button.setGraphic(Fx.icon(icon, 16));
        button.getStyleClass().addAll("window-button", kind);
        button.setFocusTraversable(false);
        return button;
    }

    /** Apparition de la fenêtre : fondu + léger zoom. */
    void show() {
        root.setOpacity(0);
        root.setScaleX(0.96);
        root.setScaleY(0.96);
        app.stage.show();
        if (!decorated) decorated = NativeWindow.decorate(app.stage);
        FadeTransition fade = new FadeTransition(Duration.millis(380), root);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(520), root);
        scale.setToX(1);
        scale.setToY(1);
        ParallelTransition anim = new ParallelTransition(fade, scale);
        anim.setInterpolator(Fx.EASE_OUT);
        anim.play();
    }

    void close() {
        FadeTransition fade = new FadeTransition(Duration.millis(180), root);
        fade.setToValue(0);
        ScaleTransition scale = new ScaleTransition(Duration.millis(180), root);
        scale.setToX(0.97);
        scale.setToY(0.97);
        ParallelTransition anim = new ParallelTransition(fade, scale);
        anim.setOnFinished(e -> Platform.exit());
        anim.play();
    }

    public void showLogin() {
        if (loginView == null) loginView = new LoginView(app);
        layout.setLeft(null);
        setView(loginView);
    }

    public void showMain() {
        if (sidebar == null) {
            sidebar = new Sidebar(app, this::navigate);
            homeView = new HomeView(app);
            settingsView = new SettingsView(app);
        }
        layout.setLeft(sidebar);
        Fx.reveal(sidebar, 0);
        navigate(Page.HOME);
    }

    public void navigate(Page page) {
        if (sidebar == null) return;
        sidebar.select(page);
        setView(switch (page) {
            case HOME -> homeView;
            case SETTINGS -> settingsView;
        });
    }

    /** Transition entre deux pages : l'ancienne s'efface, la nouvelle monte en fondu. */
    private void setView(Node next) {
        if (next == current) return;
        Node old = current;
        current = next;
        if (leaving != null && leaving.getNode() == next) leaving.stop();
        if (old != null) {
            FadeTransition out = new FadeTransition(Duration.millis(140), old);
            leaving = out;
            out.setToValue(0);
            // Si l'utilisateur est revenu sur cette page entre-temps, on ne la retire pas.
            out.setOnFinished(e -> {
                if (old != current) viewHost.getChildren().remove(old);
            });
            out.play();
        }
        if (!viewHost.getChildren().contains(next)) viewHost.getChildren().add(next);
        next.toFront();
        next.setOpacity(0);
        next.setTranslateY(16);
        FadeTransition in = new FadeTransition(Duration.millis(420), next);
        in.setToValue(1);
        TranslateTransition up = new TranslateTransition(Duration.millis(520), next);
        up.setToY(0);
        ParallelTransition anim = new ParallelTransition(in, up);
        anim.setInterpolator(Fx.EASE_OUT);
        anim.setDelay(Duration.millis(old != null ? 90 : 0));
        anim.play();
    }

    /** Pendant la partie : la fenêtre est cachée et le fond animé arrêté (zéro ressource consommée). */
    void hideForGame() {
        background.setPaused(true);
        app.stage.hide();
    }

    void restoreAfterGame() {
        if (!app.stage.isShowing()) {
            show();
            background.setPaused(false);
        }
    }

    public AuroraBackground background() {
        return background;
    }

    public Toasts toasts() {
        return toasts;
    }
}
