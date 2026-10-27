// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui.components;

import fr.launcher.core.auth.Account;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.util.Locale;

/**
 * Tête du skin du joueur, en pixels nets (pas de flou de redimensionnement).
 * Sans réseau ou si le service d'avatars ne répond pas, l'initiale du pseudo s'affiche à la place.
 */
public final class Avatar extends StackPane {

    private final ImageView view = new ImageView();
    private final Label initial = new Label();
    private final double size;

    public Avatar(double size, double radius) {
        this.size = size;
        getStyleClass().add("avatar");
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setSmooth(false); // « plus proche voisin » : les pixels du skin restent carrés et nets
        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        view.setClip(clip);
        initial.getStyleClass().add("avatar-initial");
        initial.setStyle("-fx-font-size: " + Math.round(size * 0.45) + "px;");
        getChildren().addAll(initial, view);
    }

    public void setAccount(Account account) {
        view.setImage(null);
        if (account == null) {
            initial.setText("");
            return;
        }
        // Visible tant que l'image n'est pas là, et pour de bon si elle ne vient jamais.
        initial.setText(account.name().substring(0, 1).toUpperCase(Locale.ROOT));
        // Compte hors-ligne : pas de vrai UUID, on demande la tête par pseudo.
        String id = account.type() == Account.Type.MICROSOFT ? account.uuid() : account.name();
        // Téléchargée en 2x pour les écrans haute densité, chargée en arrière-plan.
        int pixels = (int) Math.ceil(size * 2);
        Image image = new Image("https://mc-heads.net/avatar/" + id + "/" + pixels, true);
        image.progressProperty().addListener((o, a, p) -> {
            if (p.doubleValue() >= 1 && !image.isError()) view.setImage(image);
        });
    }
}
