package fr.launcher.ui.components;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.Labeled;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/** Texte en dégradé traversé de temps en temps par un reflet de lumière. */
public final class Shimmer {

    private static final double BAND = 0.13;

    private Shimmer() {
    }

    public static void apply(Labeled label, Color... colors) {
        DoubleProperty position = new SimpleDoubleProperty(-1);
        position.addListener((o, a, p) -> label.setTextFill(paint(p.doubleValue(), colors)));
        label.setTextFill(paint(-1, colors));

        Timeline sweep = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(position, -BAND)),
                new KeyFrame(Duration.millis(1500), new KeyValue(position, 1 + BAND, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.seconds(7)));
        sweep.setCycleCount(Timeline.INDEFINITE);
        sweep.setDelay(Duration.seconds(1.2));
        sweep.play();
    }

    private static LinearGradient paint(double p, Color[] colors) {
        List<Stop> stops = new ArrayList<>();
        for (int i = 0; i < colors.length; i++) {
            double offset = (double) i / (colors.length - 1);
            if (Math.abs(offset - p) > BAND) stops.add(new Stop(offset, colors[i]));
        }
        if (p > -BAND && p < 1 + BAND) {
            addClamped(stops, p - BAND, base(p - BAND, colors));
            addClamped(stops, p, Color.WHITE);
            addClamped(stops, p + BAND, base(p + BAND, colors));
        }
        stops.sort((a, b) -> Double.compare(a.getOffset(), b.getOffset()));
        return new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, stops);
    }

    private static void addClamped(List<Stop> stops, double offset, Color color) {
        if (offset >= 0 && offset <= 1) stops.add(new Stop(offset, color));
    }

    /** Couleur du dégradé de base à une position donnée. */
    private static Color base(double p, Color[] colors) {
        double scaled = Math.clamp(p, 0, 1) * (colors.length - 1);
        int i = Math.min((int) scaled, colors.length - 2);
        return colors[i].interpolate(colors[i + 1], scaled - i);
    }
}
