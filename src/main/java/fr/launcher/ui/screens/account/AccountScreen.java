package fr.launcher.ui.screens.account;

import fr.launcher.core.auth.Account;
import fr.launcher.ui.AppContext;
import fr.launcher.ui.Icons;
import fr.launcher.ui.components.Avatar;
import fr.launcher.ui.components.Ui;
import fr.launcher.ui.shell.Screen;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Mon compte : uniquement les informations réellement connues sur la session en cours. */
public final class AccountScreen implements Screen {

    private final ScrollPane root;
    private final Avatar avatar = new Avatar(72, 12);
    private final Label name = new Label();
    private final HBox badges = new HBox(8);
    private final Label details = new Label();

    public AccountScreen(AppContext app) {
        name.getStyleClass().add("account-name");
        details.getStyleClass().add("text-muted");
        VBox identity = new VBox(6, name, badges, details);
        identity.setAlignment(Pos.CENTER_LEFT);
        HBox profile = new HBox(20, avatar, identity);
        profile.setAlignment(Pos.CENTER_LEFT);

        Button logout = Ui.secondary("Se déconnecter", Icons.LOGOUT);
        logout.setOnAction(e -> app.logout());

        root = Ui.page(
                Ui.pageHeader("Mon compte", "Profil et session utilisés pour rejoindre le serveur."),
                Ui.card(Icons.ACCOUNT, "Profil", profile, new HBox(logout)));

        app.account.addListener((o, old, account) -> show(account));
        show(app.account.get());
    }

    private void show(Account account) {
        avatar.setAccount(account);
        badges.getChildren().clear();
        if (account == null) {
            name.setText("Non connecté");
            details.setText("");
            return;
        }
        name.setText(account.name());
        if (account.type() == Account.Type.MICROSOFT) {
            badges.getChildren().add(Ui.badge("Compte Microsoft", Ui.Tone.SUCCESS));
            details.setText("Identifiant Minecraft : " + account.uuid());
        } else {
            badges.getChildren().addAll(Ui.badge("Compte hors ligne", Ui.Tone.WARNING), Ui.demoBadge());
            details.setText("Compte de test du mode développeur : il ne peut pas rejoindre un serveur en ligne.");
        }
    }

    @Override
    public Node view() {
        return root;
    }
}
