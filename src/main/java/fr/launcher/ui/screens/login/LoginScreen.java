package fr.launcher.ui.screens.login;

import fr.launcher.LauncherConfig;
import fr.launcher.core.auth.Account;
import fr.launcher.core.auth.MicrosoftAuth;
import fr.launcher.ui.AppContext;
import fr.launcher.ui.Fx;
import fr.launcher.ui.Icons;
import fr.launcher.ui.components.Toasts;
import fr.launcher.ui.components.Ui;
import fr.launcher.ui.components.Wordmark;
import fr.launcher.ui.shell.Screen;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Connexion : compte Microsoft via le « device code flow » officiel (le launcher ne voit jamais le mot de passe),
 * ou compte de test hors ligne en mode développeur.
 */
public final class LoginScreen implements Screen {

    private final AppContext app;
    private final StackPane root = new StackPane();
    private final VBox choice = new VBox(14);
    private final VBox waiting = new VBox(14);
    private final Label code = new Label();
    private final Hyperlink link = new Hyperlink();
    private AtomicBoolean cancelled = new AtomicBoolean();

    public LoginScreen(AppContext app) {
        this.app = app;
        root.getStyleClass().add("login");

        Label title = new Label("Connexion");
        title.getStyleClass().add("login-title");
        Label subtitle = Ui.muted("Connecte-toi avec ton compte Microsoft pour rejoindre " + LauncherConfig.NAME + ".");
        subtitle.getStyleClass().add("login-subtitle");

        buildChoice();
        buildWaiting();
        StackPane body = new StackPane(choice, waiting);
        choice.managedProperty().bind(choice.visibleProperty());
        waiting.managedProperty().bind(waiting.visibleProperty());
        waiting.setVisible(false);

        VBox card = new VBox(Wordmark.create(app.branding.image("logo"), 96), title, subtitle, body);
        card.getStyleClass().addAll("card", "login-card");
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxSize(420, Region.USE_PREF_SIZE);
        VBox.setMargin(title, new Insets(18, 0, 0, 0));
        VBox.setMargin(body, new Insets(24, 0, 0, 0));
        root.getChildren().add(card);
    }

    private void buildChoice() {
        Button microsoft = new Button("Se connecter avec Microsoft", microsoftLogo());
        microsoft.getStyleClass().addAll("btn", "btn-microsoft");
        microsoft.setMaxWidth(Double.MAX_VALUE);
        microsoft.setOnAction(e -> startMicrosoft());
        choice.getChildren().add(microsoft);

        if (LauncherConfig.DEV_MODE) {
            Label separator = new Label("MODE DÉMO");
            separator.getStyleClass().add("separator-label");
            HBox line = new HBox(12, divider(), separator, divider());
            line.setAlignment(Pos.CENTER);

            TextField pseudo = new TextField();
            pseudo.setPromptText("Pseudo de test");
            pseudo.getStyleClass().add("field");
            pseudo.setAccessibleText("Pseudo de test");
            Button offline = Ui.secondary("Tester", null);
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
            HBox.setHgrow(pseudo, Priority.ALWAYS);
            Label note = Ui.muted("Compte hors ligne, sans connexion Microsoft : pour essayer le launcher uniquement.");
            choice.getChildren().addAll(line, row, note);
        }
    }

    private void buildWaiting() {
        Label hint = Ui.muted("Entre ce code sur");
        link.getStyleClass().add("link");
        link.setOnAction(e -> app.open(link.getText()));
        HBox hintRow = new HBox(4, hint, link);
        hintRow.setAlignment(Pos.CENTER);

        code.getStyleClass().add("device-code");
        Button copy = Ui.icon(Icons.COPY, "Copier le code");
        copy.setOnAction(e -> copyCode());
        HBox codeRow = new HBox(12, code, copy);
        codeRow.setAlignment(Pos.CENTER);
        codeRow.getStyleClass().add("code-box");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("spinner");
        spinner.setPrefSize(20, 20);
        spinner.setMaxSize(20, 20);
        HBox statusRow = new HBox(10, spinner, Ui.muted("En attente de la connexion…"));
        statusRow.setAlignment(Pos.CENTER);

        Button cancel = Ui.ghost("Annuler", null);
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
                    "Ce prototype n'a pas d'application Azure approuvée par Mojang. Utilise le mode démo.");
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
        HBox.setHgrow(r, Priority.ALWAYS);
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

    @Override
    public Node view() {
        return root;
    }
}
