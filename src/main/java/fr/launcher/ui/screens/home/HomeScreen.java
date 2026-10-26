package fr.launcher.ui.screens.home;

import fr.launcher.ui.AppContext;
import fr.launcher.ui.Fx;
import fr.launcher.ui.Icons;
import fr.launcher.ui.PlayController;
import fr.launcher.ui.components.Avatar;
import fr.launcher.ui.components.EmptyState;
import fr.launcher.ui.components.Ui;
import fr.launcher.ui.components.Wordmark;
import fr.launcher.ui.shell.Screen;
import fr.launcher.ui.shell.ScreenId;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Accueil — ossature de l'étape 2 : logo, bouton Jouer relié au vrai lancement, profil, emplacement du carrousel.
 * La composition définitive est l'objet de l'étape 3.
 */
public final class HomeScreen implements Screen {

    private final AppContext app;
    private final BorderPane root = new BorderPane();
    private final Button play = new Button("JOUER");
    private final Label status = new Label();

    public HomeScreen(AppContext app) {
        this.app = app;
        root.getStyleClass().add("home");

        VBox brand = new VBox(18, Wordmark.create(app.branding.image("logo"), 150), actions(), status);
        brand.setAlignment(Pos.BOTTOM_LEFT);
        status.getStyleClass().add("home-status");

        Node news = newsSlot();
        HBox bottom = new HBox(brand, Fx.spacer(), news);
        bottom.setAlignment(Pos.BOTTOM_LEFT);
        bottom.getStyleClass().add("home-bottom");
        root.setBottom(bottom);

        app.play.stateProperty().addListener((o, old, state) -> onState(state));
        onState(app.play.stateProperty().get());
    }

    private Node actions() {
        play.getStyleClass().addAll("btn", "btn-play");
        play.setMnemonicParsing(false);
        play.setOnAction(e -> app.play.play());

        Avatar avatar = new Avatar(64, 10);
        app.account.addListener((o, old, account) -> avatar.setAccount(account));
        avatar.setAccount(app.account.get());
        StackPane profile = new StackPane(avatar);
        profile.getStyleClass().add("profile-tile");
        Button profileButton = new Button();
        profileButton.setGraphic(profile);
        profileButton.getStyleClass().add("tile-button");
        profileButton.setAccessibleText("Mon compte");
        profileButton.setTooltip(Ui.tooltip("Mon compte"));
        profileButton.setOnAction(e -> app.shell().navigate(ScreenId.ACCOUNT));

        Button settings = Ui.icon(Icons.SETTINGS, "Paramètres");
        settings.getStyleClass().add("btn-icon-lg");
        settings.setGraphic(Fx.icon(Icons.SETTINGS, 30));
        settings.setOnAction(e -> app.shell().navigate(ScreenId.SETTINGS));

        HBox row = new HBox(18, play, profileButton, settings);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Emplacement réservé au carrousel d'actualités (étape 3). */
    private Node newsSlot() {
        EmptyState placeholder = new EmptyState(Icons.NEWS, "Carrousel d'actualités",
                "Emplacement prévu pour l'étape 3.");
        Label badge = Ui.badge("ÉTAPE 3", Ui.Tone.ACCENT);
        StackPane.setAlignment(badge, Pos.TOP_LEFT);
        StackPane slot = new StackPane(placeholder, badge);
        slot.getStyleClass().addAll("card", "news-slot");
        return slot;
    }

    private void onState(PlayController.State state) {
        play.setDisable(state != PlayController.State.IDLE);
        play.setText(switch (state) {
            case IDLE -> "JOUER";
            case INSTALLING -> "PRÉPARATION…";
            case RUNNING -> "EN JEU";
        });
        status.setText(switch (state) {
            case IDLE -> "Prêt à jouer";
            case INSTALLING -> "Vérification et téléchargement des fichiers…";
            case RUNNING -> "Minecraft est lancé";
        });
    }

    @Override
    public Node view() {
        return root;
    }
}
