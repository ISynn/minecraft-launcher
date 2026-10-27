// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui.shell;

import fr.launcher.core.log.Log;
import fr.launcher.ui.Fx;
import fr.launcher.ui.Icons;
import fr.launcher.ui.components.EmptyState;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Affiche un écran à la fois dans un conteneur, avec une transition courte.
 *
 * <p>Les écrans sont créés à la demande : un écran qui plante à la construction est remplacé par un
 * état d'erreur lisible au lieu de faire tomber tout le launcher.</p>
 */
public final class Router {

    private static final Logger LOG = Log.get(Router.class);

    private final StackPane host;
    private final Map<ScreenId, Supplier<Screen>> factories = new EnumMap<>(ScreenId.class);
    private final Map<ScreenId, Screen> built = new EnumMap<>(ScreenId.class);
    private final ReadOnlyObjectWrapper<ScreenId> current = new ReadOnlyObjectWrapper<>();
    private Screen shown;
    private FadeTransition leaving;

    public Router(StackPane host) {
        this.host = host;
    }

    public void register(ScreenId id, Supplier<Screen> factory) {
        factories.put(id, factory);
    }

    public void go(ScreenId id) {
        if (id == current.get()) return;
        Screen next = built.computeIfAbsent(id, this::build);
        show(next);
        current.set(id);
    }

    /** Affiche un écran hors navigation (connexion), sans sélectionner d'entrée du rail. */
    public void showStandalone(Screen screen) {
        show(screen);
        current.set(null);
    }

    public ReadOnlyObjectProperty<ScreenId> currentProperty() {
        return current.getReadOnlyProperty();
    }

    private Screen build(ScreenId id) {
        Supplier<Screen> factory = factories.get(id);
        try {
            if (factory == null) throw new IllegalStateException("Aucun écran enregistré pour " + id);
            return factory.get();
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Impossible de construire l'écran " + id, e);
            EmptyState error = EmptyState.error(Icons.ERROR, "Cet écran n'a pas pu s'afficher",
                    "Une erreur inattendue est survenue (" + Fx.message(e) + "). "
                            + "Les détails sont dans le journal du launcher.");
            return () -> error;
        }
    }

    private void show(Screen next) {
        if (next == shown) return;
        Screen old = shown;
        shown = next;
        Node view = next.view();
        // Retour rapide sur l'écran qu'on quittait : on interrompt son fondu de sortie.
        if (leaving != null && leaving.getNode() == view) leaving.stop();
        if (old != null) {
            old.onHide();
            Node oldView = old.view();
            FadeTransition out = new FadeTransition(Duration.millis(120), oldView);
            leaving = out;
            out.setToValue(0);
            out.setOnFinished(e -> {
                if (shown != old) host.getChildren().remove(oldView);
            });
            out.play();
        }
        if (!host.getChildren().contains(view)) host.getChildren().add(view);
        view.toFront();
        view.setOpacity(0);
        view.setTranslateY(10);
        FadeTransition in = new FadeTransition(Duration.millis(260), view);
        in.setToValue(1);
        TranslateTransition up = new TranslateTransition(Duration.millis(320), view);
        up.setToY(0);
        ParallelTransition anim = new ParallelTransition(in, up);
        anim.setInterpolator(Fx.EASE_OUT);
        anim.setDelay(Duration.millis(old != null ? 60 : 0));
        anim.play();
        next.onShow();
    }
}
