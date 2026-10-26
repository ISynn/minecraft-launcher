package fr.launcher.ui.shell;

import javafx.animation.FadeTransition;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.Optional;

/**
 * Fond de la fenêtre : l'illustration de marque si elle est disponible, recadrée pour couvrir la fenêtre,
 * sinon un dégradé nuit / jaune. Dans les deux cas un voile assombrit les zones où se trouve le texte.
 *
 * <p>Fond statique : aucune animation, donc aucun coût processeur une fois affiché.</p>
 */
final class Backdrop extends StackPane {

    private final ImageView view = new ImageView();
    private final Region dim = new Region();

    Backdrop(Optional<Image> image) {
        getStyleClass().add("backdrop");
        setMinSize(0, 0);
        Region fallback = new Region();
        fallback.getStyleClass().add("backdrop-fallback");
        Region scrim = new Region();
        scrim.getStyleClass().add("backdrop-scrim");
        dim.getStyleClass().add("backdrop-dim");
        dim.setOpacity(0);
        getChildren().addAll(fallback, view, scrim, dim);

        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setManaged(false);
        image.ifPresent(img -> {
            view.setImage(img);
            // Chargement en arrière-plan : on recadre quand les dimensions sont connues.
            img.progressProperty().addListener((o, a, p) -> requestLayout());
            img.errorProperty().addListener((o, a, error) -> {
                if (error) view.setImage(null);
            });
        });
        setMouseTransparent(true);
    }

    /**
     * Assombrit le fond pour les écrans de contenu : l'illustration s'efface derrière le texte,
     * et reprend toute sa place sur l'accueil.
     */
    void setDimmed(boolean dimmed) {
        FadeTransition fade = new FadeTransition(Duration.millis(260), dim);
        fade.setToValue(dimmed ? 1 : 0);
        fade.play();
    }

    /** Recadrage « cover » : l'image remplit la fenêtre sans déformation, centrée. */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        Image img = view.getImage();
        if (img == null || img.getWidth() <= 0) return;
        double w = getWidth();
        double h = getHeight();
        double scale = Math.max(w / img.getWidth(), h / img.getHeight());
        double iw = img.getWidth() * scale;
        double ih = img.getHeight() * scale;
        view.setFitWidth(iw);
        view.setFitHeight(ih);
        view.relocate((w - iw) / 2, (h - ih) / 2);
    }
}
