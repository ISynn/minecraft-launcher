package fr.launcher.ui.components;

import javafx.animation.AnimationTimer;
import javafx.scene.layout.Region;

/**
 * Barre de progression qui glisse en douceur vers sa valeur cible au lieu de sauter.
 * Avec une valeur négative, elle passe en mode indéterminé (un reflet qui va et vient).
 */
public final class SmoothProgressBar extends Region {

    private final Region fill = new Region();
    private double target;
    private double shown;
    private double phase;

    private final AnimationTimer timer = new AnimationTimer() {
        private long last;

        @Override
        public void handle(long now) {
            double dt = last == 0 ? 0.016 : Math.min(0.05, (now - last) / 1e9);
            last = now;
            if (target < 0) {
                phase = (phase + dt * 0.9) % 1;
            } else {
                // Rattrapage exponentiel : rapide quand l'écart est grand, doux à l'arrivée.
                shown += (target - shown) * (1 - Math.exp(-dt * 8));
            }
            requestLayout();
        }

        @Override
        public void stop() {
            super.stop();
            last = 0;
        }
    };

    public SmoothProgressBar() {
        getStyleClass().add("smooth-progress");
        fill.getStyleClass().add("fill");
        getChildren().add(fill);
        setMinHeight(6);
        setPrefHeight(6);
        setMaxHeight(6);
    }

    /** @param value entre 0 et 1, ou négatif pour « indéterminé » */
    public void setProgress(double value) {
        if (value >= 0 && target < 0) shown = 0;
        target = value;
    }

    public void start() {
        shown = 0;
        target = -1;
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        if (target < 0) {
            double barWidth = w * 0.28;
            double x = (w + barWidth) * phase - barWidth;
            fill.resizeRelocate(Math.max(0, x), 0, Math.min(barWidth + Math.min(0, x), w - Math.max(0, x)), h);
        } else {
            fill.resizeRelocate(0, 0, Math.max(h, w * Math.min(1, shown)), h);
        }
    }
}
