package fr.launcher.ui.screens.install;

import fr.launcher.LauncherConfig;
import fr.launcher.ui.AppContext;
import fr.launcher.ui.Icons;
import fr.launcher.ui.components.EmptyState;
import fr.launcher.ui.components.Ui;
import fr.launcher.ui.shell.Screen;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;

/** Installation et mises à jour — structure de l'étape 2. Le suivi détaillé arrive aux étapes 4 et 5. */
public final class InstallScreen implements Screen {

    private final ScrollPane root;

    public InstallScreen(AppContext app) {
        Label path = new Label(app.core.gameDir.toString());
        path.getStyleClass().add("path-label");
        Button open = Ui.secondary("Ouvrir le dossier", Icons.FOLDER);
        open.setOnAction(e -> app.openFolder(app.core.gameDir));

        Label version = Ui.badge("Minecraft " + LauncherConfig.MINECRAFT_VERSION + " · Fabric", Ui.Tone.NEUTRAL);
        root = Ui.page(
                Ui.pageHeader("Installation", "État des fichiers du jeu, téléchargements et vérifications.",
                        Ui.badge("ÉTAPE 4", Ui.Tone.ACCENT)),
                Ui.card(Icons.FOLDER, "Dossier d'installation", new HBox(10, version), path, open),
                new EmptyState(Icons.HOURGLASS, "Suivi de l'installation",
                        "Cet écran détaillera chaque étape (Java, Minecraft, ressources, mods), la progression, "
                                + "les vérifications et les erreurs éventuelles."));
    }

    @Override
    public Node view() {
        return root;
    }
}
