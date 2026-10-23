package fr.launcher.ui.components;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/**
 * Fond animé « aurore » : halos colorés qui dérivent lentement + particules lumineuses.
 *
 * <p>Performances : un seul Canvas (rendu GPU via Prism), aucun nœud par particule,
 * les dégradés fixes sont créés une seule fois, et l'animation s'arrête quand la fenêtre est réduite.
 * Si une image {@code assets/images/background.png} existe, elle est affichée dessous avec un zoom lent.</p>
 */
public final class AuroraBackground extends Pane {

    private static final Color BASE_TOP = Color.web("#0b0c1a");
    private static final Color BASE_BOTTOM = Color.web("#05060c");

    private record Blob(Color color, double x, double y, double ax, double ay, double speed, double phase, double radius) {
    }

    private static final Blob[] BLOBS = {
            new Blob(Color.web("#7c5cff"), 0.18, 0.22, 0.10, 0.08, 0.050, 0.0, 0.62),
            new Blob(Color.web("#22d3ee"), 0.86, 0.30, 0.08, 0.12, 0.037, 1.7, 0.55),
            new Blob(Color.web("#ec4899"), 0.70, 0.95, 0.14, 0.06, 0.043, 3.1, 0.50),
            new Blob(Color.web("#3b82f6"), 0.30, 0.85, 0.10, 0.10, 0.031, 4.4, 0.45),
    };

    private static final int PARTICLES = 70;

    private final Canvas canvas = new Canvas();
    private final double[] px = new double[PARTICLES];
    private final double[] py = new double[PARTICLES];
    private final double[] pSize = new double[PARTICLES];
    private final double[] pSpeed = new double[PARTICLES];
    private final double[] pPhase = new double[PARTICLES];
    private final Random random = new Random();
    private final Image image = loadImage();

    private final LinearGradient base = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, BASE_TOP), new Stop(1, BASE_BOTTOM));
    private final RadialGradient vignette = new RadialGradient(0, 0, 0.5, 0.45, 0.85, true, CycleMethod.NO_CYCLE,
            new Stop(0.45, Color.TRANSPARENT), new Stop(1, Color.rgb(0, 0, 0, 0.6)));
    private final LinearGradient bottomShade = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0.55, Color.TRANSPARENT), new Stop(1, Color.rgb(4, 5, 10, 0.7)));

    private final AnimationTimer timer = new AnimationTimer() {
        private long last;

        @Override
        public void handle(long now) {
            // 30 images/s suffisent pour un fond qui bouge lentement : deux fois moins de travail pour le GPU.
            if (last != 0 && now - last < 32_000_000) return;
            double dt = last == 0 ? 0 : Math.min(0.08, (now - last) / 1e9);
            last = now;
            time += dt;
            double follow = 1 - Math.exp(-dt * 2.5);
            offsetX += (targetX - offsetX) * follow;
            offsetY += (targetY - offsetY) * follow;
            draw(dt);
        }

        @Override
        public void stop() {
            super.stop();
            last = 0;
        }
    };

    private double time = random.nextDouble() * 100;
    private boolean animated;
    /** Position de la souris (-0.5 à 0.5), suivie en douceur pour l'effet de profondeur. */
    private double targetX;
    private double targetY;
    private double offsetX;
    private double offsetY;

    public AuroraBackground() {
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.widthProperty().addListener((o, a, b) -> resetParticles());
        canvas.heightProperty().addListener((o, a, b) -> resetParticles());
        setMouseTransparent(true);
    }

    public void setAnimated(boolean value) {
        animated = value;
        if (value) {
            timer.start();
        } else {
            timer.stop();
            draw(0);
        }
    }

    /** Effet de profondeur : les halos et particules suivent légèrement la souris (coordonnées 0 à 1). */
    public void pointer(double x, double y) {
        targetX = Math.clamp(x, 0, 1) - 0.5;
        targetY = Math.clamp(y, 0, 1) - 0.5;
    }

    /** Met l'animation en pause (fenêtre réduite / cachée) sans perdre le réglage. */
    public void setPaused(boolean paused) {
        if (paused) timer.stop();
        else if (animated) timer.start();
    }

    private void resetParticles() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        for (int i = 0; i < PARTICLES; i++) {
            px[i] = random.nextDouble() * w;
            py[i] = random.nextDouble() * h;
            pSize[i] = 1 + random.nextDouble() * 2.2;
            pSpeed[i] = 6 + random.nextDouble() * 18;
            pPhase[i] = random.nextDouble() * Math.PI * 2;
        }
        draw(0);
    }

    private void draw(double dt) {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;
        GraphicsContext g = canvas.getGraphicsContext2D();

        g.setFill(base);
        g.fillRect(0, 0, w, h);

        if (image != null) {
            double zoom = 1.06 + 0.04 * Math.sin(time * 0.05);
            double scale = Math.max(w / image.getWidth(), h / image.getHeight()) * zoom;
            double iw = image.getWidth() * scale;
            double ih = image.getHeight() * scale;
            g.setGlobalAlpha(0.55);
            g.drawImage(image, (w - iw) / 2, (h - ih) / 2, iw, ih);
            g.setGlobalAlpha(1);
        }

        double size = Math.max(w, h);
        for (int i = 0; i < BLOBS.length; i++) {
            Blob b = BLOBS[i];
            // Chaque halo est à une « profondeur » différente : les plus proches bougent plus avec la souris.
            double depth = 0.03 + 0.025 * i;
            double cx = w * (b.x + b.ax * Math.sin(time * b.speed * 6 + b.phase) + offsetX * depth);
            double cy = h * (b.y + b.ay * Math.cos(time * b.speed * 5 + b.phase * 1.3) + offsetY * depth);
            double r = size * b.radius * (0.92 + 0.08 * Math.sin(time * 0.3 + b.phase));
            g.setFill(new RadialGradient(0, 0, cx, cy, r, false, CycleMethod.NO_CYCLE,
                    new Stop(0, b.color.deriveColor(0, 1, 1, image != null ? 0.22 : 0.34)),
                    new Stop(1, Color.TRANSPARENT)));
            g.fillOval(cx - r, cy - r, r * 2, r * 2);
        }

        for (int i = 0; i < PARTICLES; i++) {
            py[i] -= pSpeed[i] * dt;
            px[i] += Math.sin(time * 0.6 + pPhase[i]) * 4 * dt;
            if (py[i] < -6) {
                py[i] = h + 6;
                px[i] = random.nextDouble() * w;
            }
            double alpha = 0.12 + 0.38 * (0.5 + 0.5 * Math.sin(time * 1.4 + pPhase[i]));
            g.setFill(Color.rgb(220, 225, 255, alpha));
            double parallax = pSize[i] * 9; // les grosses particules semblent plus proches
            g.fillOval(px[i] - offsetX * parallax, py[i] - offsetY * parallax, pSize[i], pSize[i]);
        }

        g.setFill(bottomShade);
        g.fillRect(0, 0, w, h);
        g.setFill(vignette);
        g.fillRect(0, 0, w, h);
    }

    private static Image loadImage() {
        for (String name : new String[]{"background.png", "background.jpg"}) {
            try (InputStream in = AuroraBackground.class.getResourceAsStream("/fr/launcher/assets/images/" + name)) {
                if (in != null) return new Image(in);
            } catch (IOException ignored) {
                // pas d'image : le fond aurore seul suffit
            }
        }
        return null;
    }
}
