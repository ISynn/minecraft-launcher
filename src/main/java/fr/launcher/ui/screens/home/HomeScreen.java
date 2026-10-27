// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui.screens.home;

import fr.launcher.LauncherConfig;
import fr.launcher.core.download.Progress;
import fr.launcher.ui.AppContext;
import fr.launcher.ui.Fx;
import fr.launcher.ui.Icons;
import fr.launcher.ui.PlayController;
import fr.launcher.ui.PlayController.Phase;
import fr.launcher.ui.components.NewsCarousel;
import fr.launcher.ui.components.PlayButton;
import fr.launcher.ui.components.Ui;
import fr.launcher.ui.components.Wordmark;
import fr.launcher.ui.shell.Screen;
import fr.launcher.ui.shell.ScreenId;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Locale;

/**
 * Accueil : illustration, logo, bouton Jouer (phases réelles), profil, statut du serveur,
 * carrousel d'actualités et bandeau d'état de l'installation.
 */
public final class HomeScreen implements Screen {

    /** Logo affiché à 132 px (≈ 165 px physiques à 125 %) : décodé en double pour rester net. */
    private static final int LOGO_DECODE_HEIGHT = 330;

    private static final String TAGLINE = "Ton aventure Pokémon sur Minecraft";

    private final AppContext app;
    private final PlayController play;
    private final BorderPane root = new BorderPane();
    private final PlayButton playButton;
    private final StatusStrip strip;
    private final NewsCarousel news;
    private final ServerStatusPill server = new ServerStatusPill();
    private boolean shown;

    /** Actif seulement pendant une installation : met à jour bouton et bandeau à partir de la progression. */
    private final AnimationTimer installTicker = new AnimationTimer() {
        private long lastText;
        private long lastSample;
        private long lastBytes;
        private double speed;

        @Override
        public void handle(long now) {
            Progress p = play.progress();
            Phase phase = play.phaseProperty().get();
            boolean transferring = phase == Phase.DOWNLOADING || phase == Phase.UPDATING;
            playButton.setProgress(transferring ? Math.max(0, p.fraction()) : -1);
            // Le texte ne change que 8 fois par seconde : inutile de relancer la mise en page à chaque image.
            if (now - lastText < 125_000_000) return;
            lastText = now;
            if (now - lastSample > 500_000_000) {
                long bytes = p.doneBytes();
                double instant = lastSample == 0 ? 0 : Math.max(0, bytes - lastBytes) / ((now - lastSample) / 1e9);
                speed = speed == 0 ? instant : speed * 0.6 + instant * 0.4;
                lastBytes = bytes;
                lastSample = now;
            }
            strip.update(phase, p, transferring ? speed : 0);
            playButton.show(phase, buttonDetail(phase, p));
        }

        @Override
        public void start() {
            lastText = lastSample = lastBytes = 0;
            speed = 0;
            super.start();
        }
    };

    public HomeScreen(AppContext app) {
        this.app = app;
        this.play = app.play;
        root.getStyleClass().add("home");

        playButton = new PlayButton(play::play);
        strip = new StatusStrip(() -> app.openFolder(app.core.logsDir), play::cancel);
        news = new NewsCarousel(app::open);
        app.news.feedProperty().addListener((o, old, feed) -> {
            if (feed != null) news.setFeed(feed, app.news.sourceName());
        });

        BorderPane.setAlignment(server, Pos.TOP_LEFT);
        root.setTop(server);

        HBox bottomRow = new HBox(24, brandBlock(), Fx.spacer(), news);
        bottomRow.setAlignment(Pos.BOTTOM_LEFT);
        VBox bottom = new VBox(18, bottomRow, strip);
        bottom.getStyleClass().add("home-bottom");
        root.setBottom(bottom);

        play.phaseProperty().addListener((o, old, phase) -> onPhase(phase));
        play.versionLabelProperty().addListener((o, a, b) -> onPhase(play.phaseProperty().get()));
        onPhase(play.phaseProperty().get());
        // Pendant la partie la fenêtre est cachée : rien ne doit tourner pour rien.
        app.stage.showingProperty().addListener((o, a, showing) -> updateActivity());
        app.stage.focusedProperty().addListener((o, a, focused) -> updateActivity());
    }

    private Node brandBlock() {
        Label tagline = new Label(TAGLINE);
        tagline.getStyleClass().add("home-tagline");

        ProfileChip profile = new ProfileChip(app.account, () -> app.shell().navigate(ScreenId.ACCOUNT));
        Button settings = Ui.icon(Icons.SETTINGS, "Paramètres");
        settings.getStyleClass().add("btn-icon-lg");
        settings.setGraphic(Fx.icon(Icons.SETTINGS, 28));
        settings.setOnAction(e -> app.shell().navigate(ScreenId.SETTINGS));

        HBox actions = new HBox(14, playButton, profile, settings);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox block = new VBox(8, Wordmark.create(app.branding.image("logo", LOGO_DECODE_HEIGHT), 132), tagline, actions);
        block.getStyleClass().add("home-brand");
        block.setAlignment(Pos.BOTTOM_LEFT);
        VBox.setMargin(actions, new Insets(18, 0, 0, 0));
        return block;
    }

    private void onPhase(Phase phase) {
        String version = (play.versionLabelProperty().get().isEmpty()
                ? "Minecraft " + LauncherConfig.MINECRAFT_VERSION + " · Fabric " + LauncherConfig.FABRIC_LOADER
                : play.versionLabelProperty().get())
                + (app.core.settings.checkUpdates ? "" : " · mode hors ligne (Paramètres)");
        PlayController.RunResult last = play.lastResultProperty().get();
        strip.show(phase, version, play.errorProperty().get(), last != null && last.cancelled());
        playButton.show(phase, buttonDetail(phase, play.progress()));
        boolean installing = phase == Phase.PREPARING || phase == Phase.DOWNLOADING || phase == Phase.UPDATING;
        if (installing) installTicker.start();
        else installTicker.stop();
    }

    private static String buttonDetail(Phase phase, Progress p) {
        return switch (phase) {
            case PREPARING -> p.stageIndex() <= 1 ? "Recherche des mises à jour" : "Vérification des fichiers";
            case DOWNLOADING, UPDATING -> p.fraction() < 0 ? "" : String.format(Locale.FRANCE, "%d %%",
                    Math.round(p.fraction() * 100));
            case LAUNCHING -> "Chargement du jeu…";
            case ERROR -> "Voir le détail ci-dessous";
            default -> "";
        };
    }

    @Override
    public void onShow() {
        shown = true;
        app.news.ensureLoaded();
        updateActivity();
    }

    @Override
    public void onHide() {
        shown = false;
        updateActivity();
    }

    private void updateActivity() {
        boolean active = shown && app.stage.isShowing();
        server.setPolling(active);
        // Le carrousel ne défile que si le joueur regarde le launcher : dans une autre fenêtre, inutile d'animer.
        news.setRunning(active && app.stage.isFocused());
    }

    @Override
    public Node view() {
        return root;
    }
}
