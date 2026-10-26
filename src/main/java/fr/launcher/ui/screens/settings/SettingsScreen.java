package fr.launcher.ui.screens.settings;

import fr.launcher.core.settings.Settings;
import fr.launcher.ui.AppContext;
import fr.launcher.ui.Icons;
import fr.launcher.ui.PlayController;
import fr.launcher.ui.components.Switch;
import fr.launcher.ui.components.Ui;
import fr.launcher.ui.shell.Screen;
import fr.launcher.ui.shell.ScreenId;
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

/** Paramètres : mémoire, options Java, comportement du launcher, fichiers et journal. */
public final class SettingsScreen implements Screen {

    private final AppContext app;
    private final Settings settings;
    private final ScrollPane root;

    public SettingsScreen(AppContext app) {
        this.app = app;
        this.settings = app.core.settings;

        root = Ui.page(
                Ui.pageHeader("Paramètres", "Règle le launcher et le jeu selon ton PC."),
                Ui.columns(new VBox(18, memoryCard(), javaCard()), new VBox(18, launcherCard(), filesCard())));
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
        slider.setAccessibleText("Mémoire allouée au jeu");
        slider.valueProperty().addListener((o, a, v) -> {
            int mb = (int) Math.round(v.doubleValue() / 512) * 512;
            value.setText(gigabytes(mb));
            settings.ramMb = mb;
        });
        slider.valueChangingProperty().addListener((o, a, changing) -> {
            if (!changing) settings.save();
        });
        slider.setOnMouseReleased(e -> settings.save());
        slider.setOnKeyReleased(e -> settings.save());
        value.setText(gigabytes(settings.ramMb));

        String advice = totalMb < 8192
                ? "Garde au moins 2 Go libres pour Windows, sinon le PC entier ralentit."
                : "Pour un serveur moddé, 4 à 8 Go suffisent : trop de mémoire peut provoquer des saccades.";
        Label hint = Ui.muted(String.format(Locale.FRANCE, "Ton PC a %.0f Go. %s", totalMb / 1024.0, advice));

        return Ui.card(null, null, Ui.cardHead(Icons.MEMORY, "Mémoire allouée", value), slider, hint);
    }

    private static String gigabytes(int mb) {
        return String.format(Locale.FRANCE, "%.1f Go", mb / 1024.0);
    }

    private Node javaCard() {
        TextField args = new TextField(settings.jvmArgs);
        args.setPromptText("ex. -XX:+UseZGC");
        args.getStyleClass().add("field");
        args.setAccessibleText("Arguments Java supplémentaires");
        args.textProperty().addListener((o, a, text) -> settings.jvmArgs = text.trim());
        args.focusedProperty().addListener((o, a, focused) -> {
            if (!focused) settings.save();
        });
        Label hint = Ui.muted("Arguments Java supplémentaires. Laisse vide si tu ne sais pas : "
                + "le launcher applique déjà les réglages optimisés de Mojang.");
        return Ui.card(Icons.CONSOLE, "Options Java avancées", args, hint);
    }

    private Node launcherCard() {
        return Ui.card(Icons.SETTINGS, "Launcher",
                toggle("Masquer pendant le jeu", "Le launcher se cache et revient quand tu quittes le jeu.",
                        settings.hideWhilePlaying, v -> settings.hideWhilePlaying = v),
                toggle("Connexion automatique", "Rejoint directement le serveur au lancement.",
                        settings.autoConnect, v -> settings.autoConnect = v));
    }

    private Node filesCard() {
        Label path = new Label(app.core.gameDir.toString());
        path.getStyleClass().add("path-label");
        path.setWrapText(true);

        Button open = Ui.secondary("Ouvrir le dossier", Icons.FOLDER);
        open.setOnAction(e -> app.openFolder(app.core.gameDir));

        Button verify = Ui.secondary("Vérifier les fichiers", Icons.VERIFY);
        verify.setOnAction(e -> {
            app.play.verify();
            app.shell().navigate(ScreenId.HOME);
        });
        verify.disableProperty().bind(app.play.stateProperty().isNotEqualTo(PlayController.State.IDLE));

        Button logs = Ui.ghost("Journal du launcher", Icons.CONSOLE);
        logs.setOnAction(e -> app.openFolder(app.core.logsDir));

        HBox buttons = new HBox(10, open, verify);
        return Ui.card(Icons.FOLDER, "Fichiers du jeu", path, buttons, logs);
    }

    private HBox toggle(String label, String description, boolean initial, Consumer<Boolean> onChange) {
        Label name = new Label(label);
        name.getStyleClass().add("setting-name");
        Label desc = Ui.muted(description);
        VBox text = new VBox(2, name, desc);
        HBox.setHgrow(text, Priority.ALWAYS);
        Switch toggle = new Switch(initial, label);
        toggle.selectedProperty().addListener((o, a, v) -> {
            onChange.accept(v);
            settings.save();
        });
        HBox row = new HBox(16, text, toggle);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("setting-row");
        return row;
    }

    @Override
    public Node view() {
        return root;
    }
}
