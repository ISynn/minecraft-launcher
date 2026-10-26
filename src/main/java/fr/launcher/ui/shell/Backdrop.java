package fr.launcher.ui.shell;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.Optional;

/**
 * Fond de la fenêtre : l'illustration de marque si elle est disponible, recadrée pour couvrir la fenêtre,
 * sinon un dégradé nuit / jaune. Dans les deux cas un voile assombrit les zones où se trouve le texte.
 *
 * <p>Fond statique : aucune animation, donc aucun coût processeur une fois affiché.</p>
 */
final class Backdrop extends StackPane {

    private final ImageView view = new ImageView();

    Backdrop(Optional<Image> image) {
        getStyleClass().add("backdrop");
        setMinSize(0, 0);
        Region fallback = new Region();
        fallback.getStyleClass().add("backdrop-fallback");
        Region scrim = new Region();
        scrim.getStyleClass().add("backdrop-scrim");
        getChildren().addAll(fallback, view, scrim);

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
