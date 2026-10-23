package fr.launcher.ui.views;

import fr.launcher.LauncherConfig;
import fr.launcher.core.auth.Account;
import fr.launcher.core.auth.MicrosoftAuth;
import fr.launcher.ui.AppContext;
import fr.launcher.ui.Fx;
import fr.launcher.ui.Icons;
import fr.launcher.ui.components.Logo;
import fr.launcher.ui.components.Toasts;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.concurrent.atomic.AtomicBoolean;

/** Écran de connexion : compte Microsoft (code à saisir sur microsoft.com/link) ou compte de test. */
public final class LoginView extends StackPane {

    private final AppContext app;
    private final VBox choice = new VBox(14);
    private final VBox waiting = new VBox(14);
    private final StackPane body = new StackPane();
    private final Label code = new Label();
    private final Hyperlink link = new Hyperlink();
    private AtomicBoolean cancelled = new AtomicBoolean();

    public LoginView(AppContext app) {
        this.app = app;
        getStyleClass().add("login-view");

        Label title = new Label("Bienvenue");
        title.getStyleClass().add("login-title");
        Label subtitle = new Label("Connecte-toi avec ton compte Microsoft pour rejoindre " + LauncherConfig.NAME + ".");
        subtitle.getStyleClass().add("login-subtitle");
        subtitle.setWrapText(true);

        buildChoice();
        buildWaiting();
        body.getChildren().addAll(choice, waiting);
        // Un bloc caché ne doit pas réserver de place dans la carte.
        choice.managedProperty().bind(choice.visibleProperty());
        waiting.managedProperty().bind(waiting.visibleProperty());
        waiting.setVisible(false);

        VBox card = new VBox(Logo.mark(64), title, subtitle, body);
        card.getStyleClass().addAll("glass-card", "login-card");
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxSize(420, Region.USE_PREF_SIZE);
        VBox.setMargin(title, new javafx.geometry.Insets(18, 0, 0, 0));
        VBox.setMargin(body, new javafx.geometry.Insets(26, 0, 0, 0));
        getChildren().add(card);
        Fx.reveal(card, 120);
    }

    private void buildChoice() {
        Button microsoft = new Button("Se connecter avec Microsoft", microsoftLogo());
        microsoft.getStyleClass().add("microsoft-button");
        microsoft.setMaxWidth(Double.MAX_VALUE);
        microsoft.setOnAction(e -> startMicrosoft());
        choice.getChildren().add(microsoft);

        if (LauncherConfig.DEV_MODE) {
            Label separator = new Label("MODE DÉVELOPPEUR");
            separator.getStyleClass().add("separator-label");
            HBox line = new HBox(12, divider(), separator, divider());
            line.setAlignment(Pos.CENTER);

            TextField pseudo = new TextField();
            pseudo.setPromptText("Pseudo de test");
            pseudo.getStyleClass().add("field");
            Button offline = new Button("Tester");
            offline.getStyleClass().add("secondary-button");
            offline.setOnAction(e -> {
                String name = pseudo.getText().trim();
                if (!name.matches("[A-Za-z0-9_]{3,16}")) {
                    app.toast(Toasts.Kind.ERROR, "Pseudo invalide", "3 à 16 caractères : lettres, chiffres ou _.");
                    return;
                }
                app.login(Account.offline(name));
            });
            pseudo.setOnAction(offline.getOnAction());
            HBox row = new HBox(10, pseudo, offline);
            HBox.setHgrow(pseudo, javafx.scene.layout.Priority.ALWAYS);
            choice.getChildren().addAll(line, row);
        }
    }

    private void buildWaiting() {
        Label hint = new Label("Entre ce code sur");
        hint.getStyleClass().add("muted");
        link.getStyleClass().add("link");
        link.setOnAction(e -> app.open(link.getText()));
        HBox hintRow = new HBox(4, hint, link);
        hintRow.setAlignment(Pos.CENTER);

        code.getStyleClass().add("device-code");
        Button copy = new Button();
        copy.setGraphic(Fx.icon(Icons.COPY, 18));
        copy.getStyleClass().add("icon-button");
        copy.setOnAction(e -> copyCode());
        HBox codeRow = new HBox(12, code, copy);
        codeRow.setAlignment(Pos.CENTER);
        codeRow.getStyleClass().add("code-box");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("spinner");
        spinner.setPrefSize(20, 20);
        spinner.setMaxSize(20, 20);
        Label status = new Label("En attente de la connexion…");
        status.getStyleClass().add("muted");
        HBox statusRow = new HBox(10, spinner, status);
        statusRow.setAlignment(Pos.CENTER);

        Button cancel = new Button("Annuler");
        cancel.getStyleClass().add("ghost-button");
        cancel.setOnAction(e -> {
            cancelled.set(true);
            showChoice();
        });

        waiting.setAlignment(Pos.TOP_CENTER);
        waiting.getChildren().addAll(hintRow, codeRow, statusRow, cancel);
    }

    private void startMicrosoft() {
        if (!MicrosoftAuth.isConfigured()) {
            app.toast(Toasts.Kind.ERROR, "Connexion Microsoft indisponible",
                    "Renseigne AZURE_CLIENT_ID dans LauncherConfig (voir README).");
            return;
        }
        AtomicBoolean myCancel = new AtomicBoolean();
        cancelled = myCancel;
        Fx.async(app.core.auth::requestDeviceCode, deviceCode -> {
            if (myCancel.get()) return;
            code.setText(deviceCode.userCode());
            link.setText(deviceCode.verificationUri());
            showWaiting();
            copyCode();
            app.open(deviceCode.verificationUri());
            Fx.async(() -> app.core.auth.waitForLogin(deviceCode, myCancel::get), account -> {
                if (!myCancel.get()) app.login(account);
            }, error -> {
                if (myCancel.get()) return;
                showChoice();
                app.error("Connexion échouée", error);
            });
        }, error -> app.error("Connexion impossible", error));
    }

    private void copyCode() {
        ClipboardContent content = new ClipboardContent();
        content.putString(code.getText());
        Clipboard.getSystemClipboard().setContent(content);
        app.toast(Toasts.Kind.INFO, "Code copié", "Colle-le sur la page Microsoft qui vient de s'ouvrir.");
    }

    private void showWaiting() {
        choice.setVisible(false);
        waiting.setVisible(true);
        Fx.reveal(waiting, 0);
    }

    private void showChoice() {
        waiting.setVisible(false);
        choice.setVisible(true);
        Fx.reveal(choice, 0);
    }

    private static Region divider() {
        Region r = new Region();
        r.getStyleClass().add("divider");
        HBox.setHgrow(r, javafx.scene.layout.Priority.ALWAYS);
        return r;
    }

    /** Le logo Microsoft : quatre carrés de couleur. */
    private static GridPane microsoftLogo() {
        GridPane grid = new GridPane();
        grid.setHgap(2);
        grid.setVgap(2);
        String[] colors = {"#f25022", "#7fba00", "#00a4ef", "#ffb900"};
        for (int i = 0; i < 4; i++) grid.add(new Rectangle(8, 8, Color.web(colors[i])), i % 2, i / 2);
        return grid;
    }
}
