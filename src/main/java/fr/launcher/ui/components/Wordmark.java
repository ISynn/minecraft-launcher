// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui.components;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * Logo : l'image fournie par les ressources de marque si elle existe, sinon un logo typographique
 * « Poke / Island » (blanc et jaune, comme celui du serveur) entièrement dessiné en CSS.
 */
public final class Wordmark {

    private Wordmark() {
    }

    public static Node create(Optional<Image> logo, double height) {
        if (logo.isPresent()) {
            ImageView view = new ImageView(logo.get());
            view.setFitHeight(height);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            return view;
        }
        Label poke = new Label("Poke");
        poke.getStyleClass().addAll("wordmark", "wordmark-top");
        Label island = new Label("Island");
        island.getStyleClass().addAll("wordmark", "wordmark-bottom");
        double size = height * 0.5;
        poke.setStyle("-fx-font-size: " + size + "px;");
        island.setStyle("-fx-font-size: " + size + "px;");
        // Kanit a de grandes marges verticales : on resserre pour que les deux mots se touchent, comme sur l'original.
        VBox box = new VBox(-size * 0.78, poke, island);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Region.USE_PREF_SIZE); // garde sa taille : le parent décide de l'alignement
        box.getStyleClass().add("wordmark-box");
        box.setCache(true); // l'ombre portée n'est calculée qu'une fois
        return box;
    }
}
