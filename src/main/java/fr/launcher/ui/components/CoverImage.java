// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui.components;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;

/**
 * Image recadrée pour remplir toute sa zone sans déformation (équivalent de « object-fit: cover »).
 * Tant que l'image charge, ou si elle est absente ou en erreur, le fond CSS de secours reste visible.
 */
public final class CoverImage extends Region {

    private final ImageView view = new ImageView();
    /** Dernière image demandée : une image plus ancienne qui finit de charger après elle est ignorée. */
    private Image requested;

    public CoverImage() {
        getStyleClass().add("cover-image");
        view.setSmooth(true);
        view.setPreserveRatio(false);
        getChildren().add(view);
        setMinSize(0, 0);
    }

    public void setImage(Image image) {
        requested = image;
        view.setImage(null);
        if (image == null) return;
        if (image.getProgress() >= 1) {
            show(image);
        } else {
            image.progressProperty().addListener((o, a, p) -> {
                if (p.doubleValue() >= 1) show(image);
            });
        }
    }

    private void show(Image image) {
        if (image == requested && !image.isError() && image.getWidth() > 0) {
            view.setImage(image);
            requestLayout();
        }
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        Image img = view.getImage();
        if (img == null || w <= 0 || h <= 0) return;
        // On découpe dans l'image la plus grande zone au format de la carte, centrée.
        double scale = Math.min(img.getWidth() / w, img.getHeight() / h);
        double vw = w * scale;
        double vh = h * scale;
        view.setViewport(new Rectangle2D((img.getWidth() - vw) / 2, (img.getHeight() - vh) / 2, vw, vh));
        view.setFitWidth(w);
        view.setFitHeight(h);
        view.relocate(0, 0);
    }
}
