package fr.launcher.ui.components;

import fr.launcher.core.auth.Account;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

/** Tête du skin du joueur, en pixels nets (pas de flou de redimensionnement). */
public final class Avatar extends StackPane {

    private final ImageView view = new ImageView();
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
        getChildren().add(view);
    }

    public void setAccount(Account account) {
        if (account == null) {
            view.setImage(null);
            return;
        }
        // Compte hors-ligne : pas de vrai UUID, on demande la tête par pseudo.
        String id = account.type() == Account.Type.MICROSOFT ? account.uuid() : account.name();
        // Téléchargée en 2x pour les écrans haute densité, chargée en arrière-plan.
        int pixels = (int) Math.ceil(size * 2);
        view.setImage(new Image("https://mc-heads.net/avatar/" + id + "/" + pixels, true));
    }
}
