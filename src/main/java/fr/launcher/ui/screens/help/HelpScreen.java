package fr.launcher.ui.screens.help;

import fr.launcher.LauncherConfig;
import fr.launcher.core.auth.Account;
import fr.launcher.core.settings.Settings;
import fr.launcher.ui.AppContext;
import fr.launcher.ui.Icons;
import fr.launcher.ui.PlayController.RunResult;
import fr.launcher.ui.components.Toasts;
import fr.launcher.ui.components.Ui;
import fr.launcher.ui.shell.Screen;
import fr.launcher.ui.shell.ScreenId;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Locale;

/**
 * Centre d'aide : ressources publiques du serveur, réponses aux problèmes fréquents,
 * et informations de diagnostic à joindre à une demande (sans aucun jeton).
 */
public final class HelpScreen implements Screen {

    private final AppContext app;
    private final ScrollPane root;
    private final Label diagnostics = new Label();

    public HelpScreen(AppContext app) {
        this.app = app;
        Button wiki = Ui.secondary("Ouvrir le guide", Icons.OPEN_EXTERNAL);
        wiki.setOnAction(e -> app.open(LauncherConfig.WIKI_URL));
        Button discord = Ui.secondary("Rejoindre le Discord", Icons.OPEN_EXTERNAL);
        discord.setOnAction(e -> app.open(LauncherConfig.DISCORD_URL));
        Button logs = Ui.secondary("Ouvrir le journal", Icons.FOLDER);
        logs.setOnAction(e -> app.openFolder(app.core.logsDir));

        VBox guide = Ui.card(Icons.BOOK, "Guide du serveur",
                Ui.muted("Commandes, systèmes de jeu, arènes : le wiki officiel répond à la plupart des questions."),
                wiki);
        VBox community = Ui.card(Icons.DISCORD, "Discord",
                Ui.muted("L'équipe et la communauté répondent sur le serveur Discord de PokeIsland."), discord);
        VBox journal = Ui.card(Icons.CONSOLE, "Journal du launcher",
                Ui.muted("Le détail de chaque erreur, sans aucun jeton de connexion. Joins-le à ta demande d'aide."),
                logs);

        root = Ui.page(
                Ui.pageHeader("Aide", "Trouver une réponse ou contacter l'équipe."),
                Ui.columns(guide, community, journal),
                Ui.columns(faqCard(), diagnosticsCard()),
                linksRow(),
                Ui.muted(LauncherConfig.WINDOW_TITLE + " " + LauncherConfig.VERSION + " — " + LauncherConfig.DISCLAIMER
                        + ". Projet indépendant, non affilié à PokeIsland, Mojang Studios ni Microsoft."));
    }

    @Override
    public void onShow() {
        diagnostics.setText(diagnosticText());
    }

    private Node faqCard() {
        Accordion faq = new Accordion(
                answer("Le jeu ne se lance pas",
                        "Ouvre Installation › « Vérifier et réparer » : le launcher recontrôle l'empreinte de chaque "
                                + "fichier et retélécharge ceux qui sont absents ou abîmés. Si l'erreur persiste, "
                                + "joins le journal du launcher à ta demande d'aide.",
                        "Aller à l'installation", () -> app.shell().navigate(ScreenId.INSTALL)),
                answer("Le jeu est lent ou se fige",
                        "Paramètres › Mémoire allouée : 4 à 6 Go suffisent en général pour un serveur moddé. "
                                + "En donner trop, ou ne pas en laisser assez à Windows, provoque des saccades.",
                        "Ouvrir les paramètres", () -> app.shell().navigate(ScreenId.SETTINGS)),
                answer("Le premier lancement est long",
                        "Il télécharge Java, Minecraft, ses ressources et les mods : plusieurs centaines de Mo. "
                                + "Les lancements suivants ne retéléchargent que ce qui a changé. Le jeu met ensuite "
                                + "une à deux minutes à charger ses mods.", null, null),
                answer("La connexion Microsoft est indisponible",
                        "Dans ce prototype, la connexion Microsoft n'est pas activée : elle demande une application "
                                + "Azure approuvée par Mojang. Le mode démo permet d'essayer le launcher.",
                        "Voir mon compte", () -> app.shell().navigate(ScreenId.ACCOUNT)));
        faq.setExpandedPane(faq.getPanes().getFirst());
        return Ui.card(Icons.HELP, "Problèmes fréquents", faq);
    }

    private TitledPane answer(String question, String text, String action, Runnable onAction) {
        VBox content = new VBox(10, Ui.muted(text));
        if (action != null) {
            Button go = Ui.ghost(action, null);
            go.setOnAction(e -> onAction.run());
            content.getChildren().add(go);
        }
        content.getStyleClass().add("faq-answer");
        TitledPane pane = new TitledPane(question, content);
        pane.getStyleClass().add("faq-item");
        return pane;
    }

    private Node diagnosticsCard() {
        diagnostics.getStyleClass().add("path-label");
        diagnostics.setWrapText(true);
        diagnostics.setMaxWidth(Double.MAX_VALUE);
        Button copy = Ui.secondary("Copier", Icons.COPY);
        copy.setOnAction(e -> {
            String text = diagnosticText();
            diagnostics.setText(text);
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
            app.toast(Toasts.Kind.SUCCESS, "Copié", "Colle ces informations dans ta demande d'aide.");
        });
        return Ui.card(Icons.CONSOLE, "Informations de diagnostic",
                Ui.muted("À joindre à une demande d'aide. Aucun jeton ni mot de passe n'y figure."), diagnostics, copy);
    }

    /** Ce qui aide à comprendre un problème, et rien de sensible. */
    private String diagnosticText() {
        Account account = app.account.get();
        RunResult last = app.play.lastResultProperty().get();
        String version = app.play.versionLabelProperty().get();
        String accountType = account == null ? "aucun"
                : account.type() == Account.Type.MICROSOFT ? "Microsoft" : "hors ligne (démo)";
        return String.join("\n",
                LauncherConfig.WINDOW_TITLE + " " + LauncherConfig.VERSION,
                "Jeu : " + (version.isEmpty() ? "Minecraft " + LauncherConfig.MINECRAFT_VERSION + " · Fabric" : version),
                "Système : " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                        + " (" + System.getProperty("os.arch") + ")",
                "Java du launcher : " + Runtime.version(),
                String.format(Locale.FRANCE, "Mémoire : %.1f Go au total, %.1f Go pour le jeu",
                        Settings.systemRamMb() / 1024.0, app.core.settings.ramMb / 1024.0),
                "Compte : " + accountType,
                "Dernière opération : " + (last == null ? "aucune" : last.success() ? "réussie" : "échec — " + last.error()),
                "Dossier : " + app.core.gameDir);
    }

    private Node linksRow() {
        HBox row = new HBox(10, Ui.muted("Suivre PokeIsland :"),
                link("Voter pour le serveur", LauncherConfig.VOTE_URL),
                link("YouTube", LauncherConfig.YOUTUBE_URL),
                link("TikTok", LauncherConfig.TIKTOK_URL));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Button link(String text, String url) {
        Button b = Ui.ghost(text, Icons.OPEN_EXTERNAL);
        b.setOnAction(e -> app.open(url));
        return b;
    }

    @Override
    public Node view() {
        return root;
    }
}
