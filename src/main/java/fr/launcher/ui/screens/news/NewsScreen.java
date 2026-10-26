package fr.launcher.ui.screens.news;

import fr.launcher.LauncherConfig;
import fr.launcher.ui.AppContext;
import fr.launcher.ui.Icons;
import fr.launcher.ui.components.EmptyState;
import fr.launcher.ui.components.Ui;
import fr.launcher.ui.shell.Screen;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;

/** Actualités — structure de l'étape 2. La liste d'articles arrive à l'étape 4. */
public final class NewsScreen implements Screen {

    private final ScrollPane root;

    public NewsScreen(AppContext app) {
        Button blog = Ui.secondary("Ouvrir le blog PokeIsland", Icons.OPEN_EXTERNAL);
        blog.setOnAction(e -> app.open(LauncherConfig.BLOG_URL));
        root = Ui.page(
                Ui.pageHeader("Actualités", "Annonces, mises à jour et événements du serveur.",
                        Ui.badge("ÉTAPE 4", Ui.Tone.ACCENT)),
                new EmptyState(Icons.HOURGLASS, "Les articles arrivent bientôt",
                        "Cet écran affichera la liste des articles. Tant qu'aucune API publique n'est disponible, "
                                + "les exemples seront clairement marqués « DÉMO ».", blog));
    }

    @Override
    public Node view() {
        return root;
    }
}
