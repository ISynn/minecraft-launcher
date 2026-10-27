// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui.screens.account;

import fr.launcher.core.auth.Account;
import fr.launcher.core.auth.AccountStore;
import fr.launcher.core.auth.MicrosoftAuth;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Mon compte : uniquement les informations réellement connues sur la session en cours. */
public final class AccountScreen implements Screen {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM à HH:mm", Locale.FRANCE)
            .withZone(ZoneId.systemDefault());

    private final AppContext app;
    private final ScrollPane root;
    private final Avatar avatar = new Avatar(88, 14);
    private final Label name = new Label();
    private final HBox badges = new HBox(8);
    private final GridPane details = new GridPane();

    public AccountScreen(AppContext app) {
        this.app = app;
        name.getStyleClass().add("account-name");
        VBox identity = new VBox(8, name, badges);
        identity.setAlignment(Pos.CENTER_LEFT);
        HBox profile = new HBox(22, avatar, identity);
        profile.setAlignment(Pos.CENTER_LEFT);

        Button logout = Ui.secondary("Se déconnecter", Icons.LOGOUT);
        logout.setTooltip(Ui.tooltip("Revient à l'écran de connexion. Le compte mémorisé est effacé de cet ordinateur."));
        logout.setOnAction(e -> app.logout());

        details.setHgap(18);
        details.setVgap(12);

        root = Ui.page(
                Ui.pageHeader("Mon compte", "Profil et session utilisés pour rejoindre le serveur."),
                Ui.columns(
                        Ui.card(Icons.ACCOUNT, "Profil", profile, details, new HBox(logout)),
                        microsoftCard()));

        app.account.addListener((o, old, account) -> show(account));
        show(app.account.get());
    }

    private Node microsoftCard() {
        boolean configured = MicrosoftAuth.isConfigured();
        Label state = Ui.badge(configured ? "Disponible" : "Non activée dans ce prototype",
                configured ? Ui.Tone.SUCCESS : Ui.Tone.WARNING);
        Label how = Ui.muted("La connexion suit le parcours officiel de Microsoft : un code s'affiche, tu le saisis "
                + "sur microsoft.com/link. Le launcher ne voit jamais ton mot de passe. Les jetons de session sont "
                + "chiffrés sur ton PC et ne sont jamais écrits dans le journal.");
        Label why = Ui.muted(configured ? ""
                : "Pour l'activer, il faut une application Azure approuvée par Mojang. En attendant, "
                + "le mode démo permet d'essayer le launcher avec un compte hors ligne.");
        why.setVisible(!configured);
        why.managedProperty().bind(why.visibleProperty());
        return Ui.card(Icons.VERIFY, "Connexion Microsoft", state, how, why);
    }

    private void show(Account account) {
        avatar.setAccount(account);
        badges.getChildren().clear();
        details.getChildren().clear();
        if (account == null) {
            name.setText("Non connecté");
            return;
        }
        boolean microsoft = account.type() == Account.Type.MICROSOFT;
        name.setText(account.name());
        if (microsoft) {
            badges.getChildren().add(Ui.badge("Compte Microsoft", Ui.Tone.SUCCESS));
        } else {
            badges.getChildren().addAll(Ui.badge("Compte hors ligne", Ui.Tone.WARNING), Ui.demoBadge());
        }
        row(0, "Pseudo", account.name());
        row(1, "Type", microsoft ? "Minecraft Java Edition (Microsoft)" : "Compte de test local : ne peut pas "
                + "rejoindre un serveur en ligne");
        row(2, "Identifiant", dashed(account.uuid()));
        row(3, "Session", microsoft
                ? "Jeton valable jusqu'au " + TIME.format(Instant.ofEpochMilli(account.expiresAt()))
                + ", renouvelé automatiquement"
                : "Aucune : pas de jeton");
        row(4, "Mémorisé", (AccountStore.ENCRYPTED ? "Chiffré pour ta session Windows (DPAPI), dans " : "Dans ")
                + app.core.dataDir.resolve("launcher"));
    }

    private void row(int index, String key, String value) {
        Label k = new Label(key);
        k.getStyleClass().add("detail-key");
        Label v = new Label(value);
        v.getStyleClass().add("detail-value");
        v.setWrapText(true);
        details.addRow(index, k, v);
    }

    /** UUID au format habituel 8-4-4-4-12. */
    static String dashed(String uuid) {
        if (uuid == null || uuid.length() != 32) return uuid;
        return uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" + uuid.substring(12, 16) + "-"
                + uuid.substring(16, 20) + "-" + uuid.substring(20);
    }

    @Override
    public Node view() {
        return root;
    }
}
