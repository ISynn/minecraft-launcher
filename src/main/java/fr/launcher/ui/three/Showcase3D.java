package fr.launcher.ui.three;

import fr.launcher.core.auth.Account;
import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.geometry.Point3D;
import javafx.scene.AmbientLight;
import javafx.scene.Cursor;
import javafx.scene.DirectionalLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Vitrine 3D : le joueur avec son vrai skin, debout sur une île flottante.
 * La tête suit la souris, on peut faire tourner l'île en la faisant glisser (avec inertie).
 *
 * <p>Performances : 3 maillages seulement pour l'île et ~12 pour le joueur, et l'animation
 * ne calcule rien quand la vitrine n'est pas affichée ou que la fenêtre est réduite.</p>
 */
public final class Showcase3D extends Pane {

    /** Courbe « back out » : dépasse un peu la cible puis revient, pour un effet de rebond. */
    private static final Interpolator BACK_OUT = new Interpolator() {
        @Override
        protected double curve(double t) {
            double c1 = 1.70158;
            double u = t - 1;
            return 1 + (c1 + 1) * u * u * u + c1 * u * u;
        }
    };
    private static final double CAMERA_DISTANCE = 375;
    /** Le joueur est un peu agrandi par rapport aux blocs : effet « figurine » plus lisible. */
    private static final double PLAYER_SCALE = 1.35;

    private final Region glow = new Region();
    private final Region shadow = new Region();
    private final SubScene subScene;
    private final Group floating = new Group();
    private final Group playerSlot = new Group();
    private final Rotate yaw = new Rotate(-28, Rotate.Y_AXIS);
    private final Rotate tilt = new Rotate(-16, Rotate.X_AXIS);

    private SkinModel player;
    private double userYaw = -28;
    private double yawVelocity;
    private boolean dragging;
    private double lastDragX;
    private double pointerX = Double.NaN;
    private double pointerY;
    private double headYaw;
    private double headPitch;
    private double time;
    private String skinId;

    private final EventHandler<MouseEvent> pointerTracker = e -> {
        pointerX = e.getSceneX();
        pointerY = e.getSceneY();
    };

    private final AnimationTimer timer = new AnimationTimer() {
        private long last;

        @Override
        public void handle(long now) {
            double dt = last == 0 ? 0 : Math.min(0.05, (now - last) / 1e9);
            last = now;
            if (isRenderable()) tick(dt);
        }

        @Override
        public void stop() {
            super.stop();
            last = 0;
        }
    };

    public Showcase3D() {
        getStyleClass().add("showcase");
        setMinSize(0, 0);

        IslandModel.Meshes island = IslandModel.build();
        playerSlot.getTransforms().add(new Scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE, 0, 0, 0));
        Group world = new Group(island.solid(), playerSlot, island.leaves());
        world.getTransforms().addAll(tilt, yaw);
        floating.getChildren().add(world);

        // Lumière de Minecraft : forte par le dessus, plus douce de face, + une touche cyan aux couleurs du launcher.
        AmbientLight ambient = new AmbientLight(Color.rgb(112, 112, 128));
        DirectionalLight sun = new DirectionalLight(Color.rgb(200, 196, 188));
        sun.setDirection(new Point3D(0.35, 1, 0.6));
        DirectionalLight rim = new DirectionalLight(Color.rgb(16, 62, 76));
        rim.setDirection(new Point3D(-1, 0.1, 0.3));

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setFieldOfView(30);
        camera.setNearClip(1);
        camera.setFarClip(2000);
        camera.setTranslateZ(-CAMERA_DISTANCE);
        camera.setTranslateY(-8);

        Group root = new Group(floating, ambient, sun, rim);
        subScene = new SubScene(root, 400, 400, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.TRANSPARENT);
        subScene.setCamera(camera);
        subScene.setCursor(Cursor.OPEN_HAND);

        glow.getStyleClass().add("showcase-glow");
        glow.setMouseTransparent(true);
        shadow.getStyleClass().add("showcase-shadow");
        shadow.setMouseTransparent(true);
        getChildren().addAll(glow, shadow, subScene);

        subScene.setOnMousePressed(e -> {
            dragging = true;
            lastDragX = e.getSceneX();
            yawVelocity = 0;
            subScene.setCursor(Cursor.CLOSED_HAND);
        });
        subScene.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - lastDragX;
            lastDragX = e.getSceneX();
            userYaw -= dx * 0.6;
            yawVelocity = -dx * 0.6 * 60;
        });
        subScene.setOnMouseReleased(e -> {
            dragging = false;
            subScene.setCursor(Cursor.OPEN_HAND);
        });

        sceneProperty().addListener((o, old, scene) -> attach(old, scene));
    }

    /** Charge le skin du compte (par UUID pour un compte Microsoft, par pseudo sinon). */
    public void setAccount(Account account) {
        String id = account == null ? "MHF_Steve"
                : account.type() == Account.Type.MICROSOFT ? account.uuid() : account.name();
        if (id.equals(skinId)) return;
        skinId = id;
        loadSkin("https://mc-heads.net/skin/" + id, true);
    }

    private void loadSkin(String url, boolean fallbackOnError) {
        Image skin = new Image(url, true);
        skin.progressProperty().addListener((o, a, progress) -> {
            if (progress.doubleValue() < 1) return;
            if (skin.isError() || skin.getWidth() != 64) {
                if (fallbackOnError) loadSkin("https://mc-heads.net/skin/MHF_Steve", false);
            } else {
                showPlayer(new SkinModel(skin));
            }
        });
    }

    /** Le joueur apparaît avec un petit rebond. */
    private void showPlayer(SkinModel model) {
        player = model;
        playerSlot.getChildren().setAll(model.root);
        model.root.setScaleX(0.4);
        model.root.setScaleY(0.4);
        model.root.setScaleZ(0.4);
        ScaleTransition pop = new ScaleTransition(Duration.millis(650), model.root);
        pop.setToX(1);
        pop.setToY(1);
        pop.setToZ(1);
        pop.setInterpolator(BACK_OUT);
        pop.play();
    }

    private void tick(double dt) {
        time += dt;
        double smooth = 1 - Math.exp(-dt * 7);

        // Rotation : inertie après un lancer, puis léger balancement.
        if (!dragging) {
            userYaw += yawVelocity * dt;
            yawVelocity *= Math.exp(-dt * 2.5);
        }
        double modelYaw = userYaw + Math.sin(time * 0.35) * 9;
        yaw.setAngle(modelYaw);

        // Flottaison : l'île monte et descend, son ombre grossit quand elle se rapproche.
        double bob = Math.sin(time * 0.9);
        floating.setTranslateY(bob * 4);
        shadow.setScaleX(1 - bob * 0.06);
        shadow.setOpacity(0.75 - bob * 0.12);

        if (player == null) return;

        // La tête regarde la souris (dans les limites d'un vrai cou).
        double targetYaw = 0;
        double targetPitch = 0;
        if (!Double.isNaN(pointerX)) {
            Point2D center = localToScene(getWidth() / 2, getHeight() * 0.42);
            double nx = Math.clamp((pointerX - center.getX()) / 420, -1, 1);
            double ny = Math.clamp((pointerY - center.getY()) / 320, -1, 1);
            double facing = ((modelYaw % 360) + 540) % 360 - 180;
            targetYaw = Math.clamp(-nx * 45 - facing, -65, 65);
            targetPitch = ny * 28;
        }
        headYaw += (targetYaw - headYaw) * smooth;
        headPitch += (targetPitch - headPitch) * smooth;
        player.headYaw.setAngle(headYaw);
        player.headPitch.setAngle(headPitch);

        // Bras qui se balancent doucement, comme au repos dans le jeu.
        double swing = Math.sin(time * 1.6) * 5;
        double spread = 3 + Math.sin(time * 1.1) * 1.5;
        player.rightArmSwing.setAngle(swing);
        player.leftArmSwing.setAngle(-swing);
        player.rightArmSpread.setAngle(spread);
        player.leftArmSpread.setAngle(-spread);
    }

    private boolean isRenderable() {
        Scene scene = getScene();
        Window window = scene == null ? null : scene.getWindow();
        return window != null && window.isShowing() && !(window instanceof Stage s && s.isIconified());
    }

    private void attach(Scene old, Scene scene) {
        if (old != null) {
            old.removeEventFilter(MouseEvent.MOUSE_MOVED, pointerTracker);
            old.removeEventFilter(MouseEvent.MOUSE_DRAGGED, pointerTracker);
        }
        if (scene != null) {
            scene.addEventFilter(MouseEvent.MOUSE_MOVED, pointerTracker);
            scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, pointerTracker);
            timer.start();
        } else {
            timer.stop();
        }
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        subScene.setWidth(w);
        subScene.setHeight(h);
        double glowSize = Math.min(w, h) * 0.95;
        glow.resizeRelocate((w - glowSize) / 2, (h - glowSize) / 2 - h * 0.04, glowSize, glowSize);
        double shadowW = Math.min(w, h) * 0.42;
        shadow.resizeRelocate((w - shadowW) / 2, h * 0.86, shadowW, shadowW * 0.16);
    }
}
