// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui.screens.settings;

import fr.launcher.core.log.Log;
import fr.launcher.core.settings.GameDirectory;
import fr.launcher.core.settings.JvmArgs;
import fr.launcher.core.settings.Settings;
import fr.launcher.ui.AppContext;
import fr.launcher.ui.Icons;
import fr.launcher.ui.components.Switch;
import fr.launcher.ui.components.Toasts;
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
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Paramètres : mémoire, fenêtre du jeu, options Java (validées), mises à jour et diagnostic, dossier du jeu.
 * Chaque valeur est contrôlée avant d'être enregistrée ; une erreur s'affiche sous le champ concerné.
 */
public final class SettingsScreen implements Screen {

    private final AppContext app;
    private final Settings settings;
    private final ScrollPane root;

    public SettingsScreen(AppContext app) {
        this.app = app;
        this.settings = app.core.settings;
        root = Ui.page(
                Ui.pageHeader("Paramètres", "Règle le launcher et le jeu selon ton PC."),
                Ui.columns(new VBox(18, memoryCard(), windowCard(), javaCard()),
                        new VBox(18, launcherCard(), updatesCard(), filesCard())));
    }

    private Node memoryCard() {
        long totalMb = Settings.systemRamMb();
        int min = Math.min(Settings.MIN_RAM_MB, Settings.maxRamMb());
        Label value = new Label();
        value.getStyleClass().add("big-value");
        Slider slider = new Slider(min, Settings.maxRamMb(), settings.ramMb);
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

    private Node windowCard() {
        TextField width = numberField(settings.windowWidth, "Largeur");
        TextField height = numberField(settings.windowHeight, "Hauteur");
        Label error = error();
        Runnable apply = () -> {
            Optional<String> problem = size(width.getText(), height.getText());
            show(error, problem);
            if (problem.isEmpty()) {
                settings.windowWidth = Integer.parseInt(width.getText().trim());
                settings.windowHeight = Integer.parseInt(height.getText().trim());
                settings.save();
            }
        };
        width.focusedProperty().addListener((o, a, focused) -> {
            if (!focused) apply.run();
        });
        height.focusedProperty().addListener((o, a, focused) -> {
            if (!focused) apply.run();
        });
        width.setOnAction(e -> apply.run());
        height.setOnAction(e -> apply.run());
        Label times = new Label("×");
        times.getStyleClass().add("text-muted");
        HBox size = new HBox(10, width, times, height);
        size.setAlignment(Pos.CENTER_LEFT);
        size.setDisable(!settings.customResolution || settings.fullscreen);

        HBox fullscreen = toggle("Plein écran", "Le jeu s'ouvre directement en plein écran (F11 pour en sortir).",
                settings.fullscreen, v -> {
                    settings.fullscreen = v;
                    size.setDisable(!settings.customResolution || v);
                });
        HBox custom = toggle("Taille personnalisée", "Sinon, Minecraft choisit lui-même la taille de sa fenêtre.",
                settings.customResolution, v -> {
                    settings.customResolution = v;
                    size.setDisable(!v || settings.fullscreen);
                });
        return Ui.card(Icons.CONSOLE, "Fenêtre du jeu", fullscreen, custom, size, error);
    }

    /** Taille de fenêtre valide ? (nombres entiers, dans des limites raisonnables) */
    static Optional<String> size(String w, String h) {
        try {
            int width = Integer.parseInt(w.trim());
            int height = Integer.parseInt(h.trim());
            if (width < Settings.MIN_WIDTH || height < Settings.MIN_HEIGHT) {
                return Optional.of("Minimum " + Settings.MIN_WIDTH + " × " + Settings.MIN_HEIGHT + ".");
            }
            if (width > Settings.MAX_SIZE || height > Settings.MAX_SIZE) {
                return Optional.of("Maximum " + Settings.MAX_SIZE + " pixels de côté.");
            }
            return Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.of("Indique la largeur et la hauteur en pixels.");
        }
    }

    private static TextField numberField(int value, String label) {
        TextField field = new TextField(Integer.toString(value));
        field.getStyleClass().add("field");
        field.setPrefColumnCount(5);
        field.setAccessibleText(label + " de la fenêtre, en pixels");
        // Uniquement des chiffres, 4 au plus.
        field.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches("\\d{0,4}") ? c : null));
        return field;
    }

    private Node javaCard() {
        TextField args = new TextField(settings.jvmArgs);
        args.setPromptText("ex. -XX:+UseZGC");
        args.getStyleClass().add("field");
        args.setAccessibleText("Arguments Java supplémentaires");
        Label error = error();
        args.textProperty().addListener((o, a, text) -> {
            JvmArgs.Parsed parsed = JvmArgs.parse(text);
            show(error, Optional.ofNullable(parsed.error()));
            if (parsed.valid()) settings.jvmArgs = text.trim();
        });
        args.focusedProperty().addListener((o, a, focused) -> {
            if (!focused) settings.save();
        });
        Label hint = Ui.muted("Arguments Java supplémentaires (-XX:, -D…). Laisse vide si tu ne sais pas : "
                + "le launcher applique déjà les réglages optimisés de Mojang.");
        return Ui.card(Icons.CONSOLE, "Options Java avancées", args, error, hint);
    }

    private Node launcherCard() {
        return Ui.card(Icons.SETTINGS, "Launcher",
                toggle("Masquer pendant le jeu",
                        "Le launcher se cache à l'ouverture du jeu et revient quand tu le quittes.",
                        settings.hideWhilePlaying, v -> settings.hideWhilePlaying = v),
                toggle("Connexion automatique", "Rejoint directement le serveur au lancement.",
                        settings.autoConnect, v -> settings.autoConnect = v));
    }

    private Node updatesCard() {
        return Ui.card(Icons.DOWNLOAD, "Mises à jour et diagnostic",
                toggle("Rechercher les mises à jour", "Désactivé : mode hors ligne, le jeu se lance avec les fichiers "
                                + "déjà installés. Sans réseau, le launcher bascule aussi tout seul hors ligne.",
                        settings.checkUpdates, v -> settings.checkUpdates = v),
                toggle("Journal détaillé", "Ajoute des informations techniques au journal, utiles pour l'aide. "
                                + "Les jetons restent toujours masqués.",
                        settings.verboseLog, v -> {
                            settings.verboseLog = v;
                            Log.setVerbose(v);
                        }));
    }

    private Node filesCard() {
        Label path = new Label(app.core.gameDir.toString());
        path.getStyleClass().add("path-label");
        path.setWrapText(true);
        Label error = error();
        Label pending = Ui.muted("");
        pending.setVisible(false);
        pending.managedProperty().bind(pending.visibleProperty());

        Button open = Ui.secondary("Ouvrir", Icons.FOLDER);
        open.setOnAction(e -> app.openFolder(app.core.gameDir));
        Button change = Ui.secondary("Changer…", Icons.FOLDER);
        change.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Dossier du jeu");
            File current = app.core.gameDir.toFile();
            if (current.isDirectory()) chooser.setInitialDirectory(current);
            File chosen = chooser.showDialog(app.stage);
            if (chosen != null) choose(chosen.toPath(), error, pending);
        });
        Button reset = Ui.ghost("Par défaut", null);
        reset.setOnAction(e -> {
            settings.gameDir = "";
            settings.save();
            show(error, Optional.empty());
            restartNotice(pending, app.core.dataDir);
        });
        change.disableProperty().bind(app.play.busyProperty());
        reset.disableProperty().bind(app.play.busyProperty());

        Button verify = Ui.secondary("Vérifier les fichiers", Icons.VERIFY);
        verify.setOnAction(e -> {
            app.play.verify();
            app.shell().navigate(ScreenId.INSTALL);
        });
        verify.disableProperty().bind(app.play.busyProperty());
        Button logs = Ui.ghost("Journal", Icons.CONSOLE);
        logs.setOnAction(e -> app.openFolder(app.core.logsDir));

        Label note = Ui.muted("Tes mondes et captures restent dans ce dossier : la vérification ne les modifie jamais.");
        return Ui.card(Icons.FOLDER, "Dossier du jeu", path, new HBox(10, open, change, reset), error, pending,
                note, new HBox(10, verify, logs));
    }

    private void choose(Path dir, Label error, Label pending) {
        Optional<String> problem = GameDirectory.problem(dir.toString());
        show(error, problem);
        if (problem.isPresent()) return;
        settings.gameDir = dir.toAbsolutePath().normalize().toString();
        settings.save();
        restartNotice(pending, dir);
    }

    /** Le dossier est pris en compte au redémarrage : on le dit clairement, sans rien déplacer. */
    private void restartNotice(Label pending, Path dir) {
        boolean changed = !dir.toAbsolutePath().normalize().equals(app.core.gameDir);
        pending.setText("Nouveau dossier : " + dir + ". Il sera utilisé au prochain démarrage du launcher ; "
                + "rien n'est déplacé (l'installation y sera faite au premier clic sur Jouer).");
        pending.setVisible(changed);
        if (changed) app.toast(Toasts.Kind.INFO, "Dossier enregistré", "Redémarre le launcher pour l'utiliser.");
    }

    private static Label error() {
        Label error = new Label();
        error.getStyleClass().add("field-error");
        error.setWrapText(true);
        error.setVisible(false);
        error.managedProperty().bind(error.visibleProperty());
        return error;
    }

    private static void show(Label error, Optional<String> problem) {
        error.setText(problem.orElse(""));
        error.setVisible(problem.isPresent());
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
