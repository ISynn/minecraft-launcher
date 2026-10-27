// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui.shell;

import fr.launcher.LauncherConfig;
import fr.launcher.ui.AppContext;
import fr.launcher.ui.Fx;
import fr.launcher.ui.Icons;
import fr.launcher.ui.NativeWindow;
import fr.launcher.ui.components.Toasts;
import fr.launcher.ui.theme.Theme;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.function.Supplier;

/**
 * La fenêtre du launcher : fond, barre de titre, zone des écrans, rail de navigation et notifications.
 *
 * <p>Sous Windows 11 la fenêtre est opaque et c'est le système qui arrondit les coins et dessine l'ombre
 * (une fenêtre transparente coûte ~3× plus de processeur). Ailleurs, JavaFX s'en charge.</p>
 */
public final class AppShell {

    public static final double WIDTH = 1180;
    public static final double HEIGHT = 700;
    private static final double SHADOW = 22;
    private static final double RADIUS = 14;

    private final Stage stage;
    private final StackPane screenHost = new StackPane();
    private final Router router = new Router(screenHost);
    private final Toasts toasts = new Toasts();
    private final NavRail rail;
    private final StackPane root;
    private Supplier<Screen> loginFactory;
    private Screen login;
    private boolean decorated;

    public AppShell(AppContext app) {
        this.stage = app.stage;

        screenHost.getStyleClass().add("screen-host");
        screenHost.setMinSize(0, 0);

        rail = new NavRail(router.currentProperty(), router::go,
                new NavRail.External(Icons.GLOBE, "Site officiel", () -> app.open(LauncherConfig.WEBSITE_URL)),
                new NavRail.External(Icons.SHOP, "Boutique", () -> app.open(LauncherConfig.SHOP_URL)),
                new NavRail.External(Icons.DISCORD, "Discord", () -> app.open(LauncherConfig.DISCORD_URL)));
        BorderPane.setAlignment(rail, Pos.CENTER_RIGHT);
        BorderPane.setMargin(rail, new Insets(0, 22, 0, 0));

        BorderPane layout = new BorderPane();
        layout.setTop(new TitleBar(stage, this::close));
        layout.setCenter(screenHost);
        layout.setRight(rail);

        Backdrop backdrop = new Backdrop(app.branding.image("background"));
        // Fond assombri partout sauf sur l'accueil (écrans de contenu, connexion).
        router.currentProperty().addListener((o, old, id) -> backdrop.setDimmed(id != ScreenId.HOME));
        StackPane frame = new StackPane(backdrop, layout, toasts);
        frame.getStyleClass().add("window");

        Scene scene;
        if (NativeWindow.SUPPORTED) {
            root = frame;
            scene = new Scene(root, WIDTH, HEIGHT, Color.web("#0b0d12"));
            stage.initStyle(StageStyle.UNDECORATED);
        } else {
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
            stage.initStyle(StageStyle.TRANSPARENT);
        }
        Theme.apply(scene);
        // Échap ramène à l'accueil depuis n'importe quel écran secondaire.
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            ScreenId current = router.currentProperty().get();
            if (e.getCode() == KeyCode.ESCAPE && current != null && current != ScreenId.HOME) {
                navigate(ScreenId.HOME);
                e.consume();
            }
        });
        stage.setTitle(LauncherConfig.WINDOW_TITLE);
        // Icône de la barre des tâches (monogramme original, voir packaging/make-icon.ps1) : Windows choisit la taille.
        for (int size : new int[]{16, 32, 64, 256}) {
            stage.getIcons().add(new Image(Theme.class.getResource("/fr/launcher/assets/icons/icon-" + size + ".png")
                    .toExternalForm()));
        }
        stage.setResizable(false);
        stage.setScene(scene);
        // Fenêtre cachée pendant la partie : sans cela, JavaFX considérerait l'application terminée et la fermerait,
        // et le launcher ne reviendrait jamais après le jeu. La fermeture passe donc toujours par close().
        Platform.setImplicitExit(false);
        stage.setOnCloseRequest(e -> {
            e.consume();
            close();
        });
    }

    public void register(ScreenId id, Supplier<Screen> factory) {
        router.register(id, factory);
    }

    public void setLoginScreen(Supplier<Screen> factory) {
        this.loginFactory = factory;
    }

    public void navigate(ScreenId id) {
        rail.setInternalVisible(true);
        router.go(id);
    }

    public void showLogin() {
        if (login == null) login = loginFactory.get();
        rail.setInternalVisible(false);
        router.showStandalone(login);
    }

    public void showMain() {
        navigate(ScreenId.HOME);
    }

    public Toasts toasts() {
        return toasts;
    }

    /** Apparition de la fenêtre : fondu + léger zoom. */
    public void show() {
        root.setOpacity(0);
        root.setScaleX(0.97);
        root.setScaleY(0.97);
        stage.show();
        if (!decorated) decorated = NativeWindow.decorate(stage);
        FadeTransition fade = new FadeTransition(Duration.millis(320), root);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(420), root);
        scale.setToX(1);
        scale.setToY(1);
        ParallelTransition anim = new ParallelTransition(fade, scale);
        anim.setInterpolator(Fx.EASE_OUT);
        anim.play();
    }

    public void close() {
        FadeTransition fade = new FadeTransition(Duration.millis(160), root);
        fade.setToValue(0);
        fade.setOnFinished(e -> Platform.exit());
        fade.play();
    }

    /** Pendant la partie, la fenêtre est cachée : JavaFX n'a plus rien à dessiner. */
    public void hideForGame() {
        stage.hide();
    }

    public void restoreAfterGame() {
        if (!stage.isShowing()) show();
    }
}
