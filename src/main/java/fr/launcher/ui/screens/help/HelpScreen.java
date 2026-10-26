package fr.launcher.ui.screens.help;

import fr.launcher.LauncherConfig;
import fr.launcher.ui.AppContext;
import fr.launcher.ui.Icons;
import fr.launcher.ui.components.Ui;
import fr.launcher.ui.shell.Screen;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

/** Centre d'aide : ressources publiques du serveur et outils de diagnostic du launcher. */
public final class HelpScreen implements Screen {

    private final ScrollPane root;

    public HelpScreen(AppContext app) {
        Button wiki = Ui.secondary("Ouvrir le guide", Icons.OPEN_EXTERNAL);
        wiki.setOnAction(e -> app.open(LauncherConfig.WIKI_URL));
        Button discord = Ui.secondary("Rejoindre le Discord", Icons.OPEN_EXTERNAL);
        discord.setOnAction(e -> app.open(LauncherConfig.DISCORD_URL));
        Button logs = Ui.secondary("Ouvrir le journal", Icons.FOLDER);
        logs.setOnAction(e -> app.openFolder(app.core.logsDir));

        VBox guide = Ui.card(Icons.BOOK, "Guide du serveur",
                Ui.muted("Commandes, systèmes de jeu, arènes : le wiki officiel répond à la plupart des questions."), wiki);
        VBox community = Ui.card(Icons.DISCORD, "Discord",
                Ui.muted("L'équipe et la communauté répondent sur le serveur Discord de PokeIsland."), discord);
        VBox diagnostics = Ui.card(Icons.CONSOLE, "Un problème avec le launcher ?",
                Ui.muted("Le journal contient le détail des erreurs (sans aucun jeton de connexion). "
                        + "Joins-le à ta demande d'aide."), logs);
        root = Ui.page(
                Ui.pageHeader("Aide", "Trouver une réponse ou contacter l'équipe."),
                Ui.columns(guide, community, diagnostics),
                Ui.muted(LauncherConfig.WINDOW_TITLE + " " + LauncherConfig.VERSION + " — " + LauncherConfig.DISCLAIMER
                        + ". Projet indépendant, non affilié à PokeIsland, Mojang Studios ni Microsoft."));
    }

    @Override
    public Node view() {
        return root;
    }
}
