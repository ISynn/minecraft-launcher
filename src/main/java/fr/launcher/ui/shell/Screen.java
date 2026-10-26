package fr.launcher.ui.shell;

import javafx.scene.Node;

/**
 * Un écran du launcher. Construit une seule fois, à la première visite.
 *
 * <p>{@link #onShow()} / {@link #onHide()} permettent de démarrer et d'arrêter ce qui consomme
 * (animations, rafraîchissements réseau) uniquement quand l'écran est visible.</p>
 */
public interface Screen {

    Node view();

    default void onShow() {
    }

    default void onHide() {
    }
}
