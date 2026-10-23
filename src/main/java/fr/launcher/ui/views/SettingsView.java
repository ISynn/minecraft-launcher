package fr.launcher.ui.views;

import fr.launcher.LauncherConfig;
import fr.launcher.core.settings.Settings;
import fr.launcher.ui.AppContext;
import fr.launcher.ui.Fx;
import fr.launcher.ui.Icons;
import fr.launcher.ui.PlayController;
import fr.launcher.ui.components.Switch;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.function.Consumer;

/** Réglages : mémoire, options Java, comportement du launcher, fichiers du jeu. */
public final class SettingsView extends ScrollPane {

    private final AppContext app;
    private final Settings settings;

    public SettingsView(AppContext app) {
        this.app = app;
        this.settings = app.core.settings;
        getStyleClass().add("page-scroll");
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);

        Label title = new Label("Paramètres");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Règle le launcher et le jeu selon ton PC.");
        subtitle.getStyleClass().add("page-subtitle");

        VBox left = new VBox(18, memoryCard(), javaCard());
        VBox right = new VBox(18, launcherCard(), filesCard());
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setPrefWidth(0);
        right.setPrefWidth(0);
        HBox columns = new HBox(18, left, right);

        Label about = new Label(LauncherConfig.NAME + " Launcher " + LauncherConfig.VERSION);
        about.getStyleClass().add("muted-small");

        VBox content = new VBox(4, title, subtitle, columns, about);
        VBox.setMargin(columns, new Insets(20, 0, 14, 0));
        content.setPadding(new Insets(30, 40, 32, 36));
        content.getStyleClass().add("page");
        setContent(content);
    }

    private Node memoryCard() {
        long totalMb = Settings.systemRamMb();
        Label value = new Label();
        value.getStyleClass().add("big-value");
        Slider slider = new Slider(1024, Settings.maxRamMb(), settings.ramMb);
        slider.setBlockIncrement(512);
        slider.setMajorTickUnit(512);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.valueProperty().addListener((o, a, v) -> {
            int mb = (int) Math.round(v.doubleValue() / 512) * 512;
            value.setText(String.format(Locale.FRANCE, "%.1f Go", mb / 1024.0));
            settings.ramMb = mb;
        });
        slider.valueChangingProperty().addListener((o, a, changing) -> {
            if (!changing) settings.save();
        });
        slider.setOnMouseReleased(e -> settings.save());
        value.setText(String.format(Locale.FRANCE, "%.1f Go", settings.ramMb / 1024.0));

        String advice = totalMb < 8192
                ? "Garde au moins 2 Go libres pour Windows, sinon le PC entier ralentit."
                : "Pour un serveur moddé, 4 à 8 Go suffisent : trop de mémoire peut provoquer des saccades.";
        Label hint = new Label(String.format(Locale.FRANCE, "Ton PC a %.0f Go. %s", totalMb / 1024.0, advice));
        hint.getStyleClass().add("muted-small");
        hint.setWrapText(true);

        HBox head = cardHead(Icons.MEMORY, "Mémoire allouée");
        head.getChildren().addAll(Fx.spacer(), value);
        return card(head, slider, hint);
    }

    private Node javaCard() {
        TextField args = new TextField(settings.jvmArgs);
        args.setPromptText("ex. -XX:+UseZGC");
        args.getStyleClass().add("field");
        args.textProperty().addListener((o, a, text) -> settings.jvmArgs = text.trim());
        args.focusedProperty().addListener((o, a, focused) -> {
            if (!focused) settings.save();
        });
        Label hint = new Label("Arguments Java supplémentaires. Laisse vide si tu ne sais pas : "
                + "le launcher applique déjà les réglages optimisés de Mojang.");
        hint.getStyleClass().add("muted-small");
        hint.setWrapText(true);
        return card(cardHead(Icons.CONSOLE, "Options Java avancées"), args, hint);
    }

    private Node launcherCard() {
        return card(cardHead(Icons.SETTINGS, "Launcher"),
                toggle("Masquer pendant le jeu", "Le launcher se cache et revient quand tu quittes le jeu.",
                        settings.hideWhilePlaying, v -> settings.hideWhilePlaying = v),
                toggle("Connexion automatique", "Rejoint directement le serveur au lancement.",
                        settings.autoConnect, v -> settings.autoConnect = v),
                toggle("Fond animé", "Désactive-le sur un PC peu puissant.",
                        settings.animatedBackground, v -> {
                            settings.animatedBackground = v;
                            app.window().background().setAnimated(v);
                        }));
    }

    private Node filesCard() {
        Label path = new Label(app.core.gameDir.toString());
        path.getStyleClass().add("path-label");
        path.setWrapText(true);

        Button open = new Button("Ouvrir le dossier", Fx.icon(Icons.FOLDER, 16));
        open.getStyleClass().add("secondary-button");
        open.setOnAction(e -> app.open(app.core.gameDir.toUri().toString()));

        Button verify = new Button("Vérifier les fichiers", Fx.icon(Icons.VERIFY, 16));
        verify.getStyleClass().add("secondary-button");
        verify.setOnAction(e -> {
            app.play.verify();
            app.window().navigate(fr.launcher.ui.Page.HOME);
        });
        verify.disableProperty().bind(app.play.stateProperty().isNotEqualTo(PlayController.State.IDLE));

        HBox buttons = new HBox(10, open, verify);
        return card(cardHead(Icons.FOLDER, "Fichiers du jeu"), path, buttons);
    }

    private HBox toggle(String label, String description, boolean initial, Consumer<Boolean> onChange) {
        Label name = new Label(label);
        name.getStyleClass().add("setting-name");
        Label desc = new Label(description);
        desc.getStyleClass().add("muted-small");
        desc.setWrapText(true);
        VBox text = new VBox(2, name, desc);
        HBox.setHgrow(text, Priority.ALWAYS);
        Switch toggle = new Switch(initial);
        toggle.selectedProperty().addListener((o, a, v) -> {
            onChange.accept(v);
            settings.save();
        });
        HBox row = new HBox(16, text, toggle);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("setting-row");
        return row;
    }

    private static HBox cardHead(String icon, String title) {
        Label label = new Label(title);
        label.getStyleClass().add("card-title");
        HBox head = new HBox(10, Fx.icon(icon, 18), label);
        head.getStyleClass().add("card-head");
        head.setAlignment(Pos.CENTER_LEFT);
        return head;
    }

    private static VBox card(Node... children) {
        VBox card = new VBox(14, children);
        card.getStyleClass().add("glass-card");
        return card;
    }
}
