// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Petits outils JavaFX partagés par toute l'interface. */
public final class Fx {

    /** Courbe « ease-out » douce, celle des interfaces modernes (décélération marquée). */
    public static final Interpolator EASE_OUT = Interpolator.SPLINE(0.16, 1, 0.3, 1);
    public static final Interpolator EASE_IN_OUT = Interpolator.SPLINE(0.65, 0, 0.35, 1);

    private static final ExecutorService BACKGROUND = Executors.newVirtualThreadPerTaskExecutor();

    @FunctionalInterface
    public interface Work<T> {
        T run() throws Exception;
    }

    private Fx() {
    }

    /**
     * Exécute un travail lent (réseau, disque) hors du thread graphique, puis revient sur celui-ci
     * pour mettre l'interface à jour. L'interface ne gèle donc jamais.
     *
     * @return la tâche, que {@code cancel(true)} interrompt (téléchargements compris)
     */
    public static <T> Future<?> async(Work<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        return BACKGROUND.submit(() -> {
            try {
                T result = work.run();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Throwable t) {
                Platform.runLater(() -> onError.accept(t));
            }
        });
    }

    /** Icône vectorielle (viewBox 24×24) redimensionnée, nette à toutes les tailles. */
    public static Node icon(String svg, double size) {
        SVGPath path = new SVGPath();
        path.setContent(svg);
        path.getStyleClass().add("icon");
        path.setScaleX(size / 24);
        path.setScaleY(size / 24);
        return new Group(path);
    }

    public static Region spacer() {
        Region r = new Region();
        javafx.scene.layout.HBox.setHgrow(r, Priority.ALWAYS);
        javafx.scene.layout.VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    /** Apparition : fondu + légère montée. */
    public static void reveal(Node node, double delayMs) {
        node.setOpacity(0);
        node.setTranslateY(14);
        FadeTransition fade = new FadeTransition(Duration.millis(520), node);
        fade.setToValue(1);
        TranslateTransition move = new TranslateTransition(Duration.millis(620), node);
        move.setToY(0);
        ParallelTransition anim = new ParallelTransition(fade, move);
        anim.setInterpolator(EASE_OUT);
        anim.setDelay(Duration.millis(delayMs));
        anim.play();
    }

    /** Taille lisible en français : « 475 Mo », « 1,2 Go » (espace insécable : jamais coupée en fin de ligne). */
    public static String size(long bytes) {
        if (bytes < 1024 * 1024) return (bytes == 0 ? 0 : Math.max(1, bytes / 1024)) + " Ko";
        double mb = bytes / 1_048_576.0;
        return mb < 1024
                ? String.format(Locale.FRANCE, "%.0f Mo", mb)
                : String.format(Locale.FRANCE, "%.1f Go", mb / 1024);
    }

    public static String message(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getMessage() == null) root = root.getCause();
        String msg = root.getMessage();
        return msg != null && !msg.isBlank() ? msg : root.getClass().getSimpleName();
    }
}
