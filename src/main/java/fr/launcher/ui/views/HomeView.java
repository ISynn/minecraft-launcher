package fr.launcher.ui.views;

import fr.launcher.LauncherConfig;
import fr.launcher.core.download.Progress;
import fr.launcher.core.server.ServerPinger;
import fr.launcher.ui.AppContext;
import fr.launcher.ui.Fx;
import fr.launcher.ui.Icons;
import fr.launcher.ui.PlayController;
import fr.launcher.ui.components.Avatar;
import fr.launcher.ui.components.Shimmer;
import fr.launcher.ui.components.SmoothProgressBar;
import fr.launcher.ui.three.Showcase3D;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.Locale;

/** Page d'accueil : statut du serveur, titre, joueur en 3D sur son île, carte du joueur et bouton Jouer. */
public final class HomeView extends VBox {

    private static final PseudoClass ONLINE = PseudoClass.getPseudoClass("online");

    private final AppContext app;
    private final Region statusDot = new Region();
    private final Region statusRing = new Region();
    private final Label statusText = new Label("Connexion au serveur…");
    private final Label playersText = new Label("—");
    private final Label pingText = new Label("");
    private final HBox playersBox;
    private final ParallelTransition ringPulse;

    private final VBox progressBox = new VBox(8);
    private final Label stepLabel = new Label();
    private final Label percentLabel = new Label();
    private final Label detailLabel = new Label();
    private final SmoothProgressBar bar = new SmoothProgressBar();
    private final Button play = new Button("JOUER");
    private final Region playGlow = new Region();
    private final Region shine = new Region();

    private final AnimationTimer progressTicker = new AnimationTimer() {
        private long lastText;
        private long lastSample;
        private long lastBytes;
        private double speed;

        @Override
        public void handle(long now) {
            Progress p = app.play.progress();
            bar.setProgress(p.fraction());
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
            stepLabel.setText(p.stageCount() > 0 ? p.stageIndex() + "/" + p.stageCount() + " · " + p.step() : p.step());
            double f = p.fraction();
            percentLabel.setText(f < 0 ? "" : Math.round(f * 100) + " %");
            StringBuilder detail = new StringBuilder();
            if (p.totalFiles() > 1) detail.append(p.doneFiles()).append(" / ").append(p.totalFiles()).append(" fichiers");
            if (speed > 50_000) {
                if (!detail.isEmpty()) detail.append("  ·  ");
                detail.append(String.format(Locale.FRANCE, "%.1f Mo/s", speed / 1_048_576));
            }
            detailLabel.setText(detail.toString());
        }

        @Override
        public void start() {
            lastText = lastSample = lastBytes = 0;
            speed = 0;
            super.start();
        }
    };

    public HomeView(AppContext app) {
        this.app = app;
        getStyleClass().add("home-view");
        setPadding(new Insets(26, 44, 36, 40));

        // Statut du serveur, avec une onde radar autour du point quand il est en ligne.
        statusDot.getStyleClass().add("status-dot");
        statusRing.getStyleClass().add("status-ring");
        statusRing.setOpacity(0);
        StackPane dot = new StackPane(statusRing, statusDot);
        ScaleTransition grow = new ScaleTransition(Duration.millis(1700), statusRing);
        grow.setFromX(1);
        grow.setFromY(1);
        grow.setToX(3.2);
        grow.setToY(3.2);
        FadeTransition fade = new FadeTransition(Duration.millis(1700), statusRing);
        fade.setFromValue(0.7);
        fade.setToValue(0);
        ringPulse = new ParallelTransition(grow, fade);
        ringPulse.setInterpolator(Fx.EASE_OUT);
        ringPulse.setCycleCount(ParallelTransition.INDEFINITE);

        statusText.getStyleClass().add("status-text");
        playersText.getStyleClass().add("status-value");
        pingText.getStyleClass().add("status-muted");
        playersBox = new HBox(6, Fx.icon(Icons.PLAYERS, 16), playersText);
        playersBox.setAlignment(Pos.CENTER);
        playersBox.getStyleClass().add("status-players");
        playersBox.setVisible(false);
        playersBox.setManaged(false);
        HBox status = new HBox(10, dot, statusText, playersBox, pingText);
        status.getStyleClass().add("pill");
        status.setAlignment(Pos.CENTER_LEFT);
        status.setMaxWidth(Region.USE_PREF_SIZE);

        VBox hero = hero();
        HBox middle = new HBox(hero);
        middle.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(middle, Priority.ALWAYS);
        if (Platform.isSupported(ConditionalFeature.SCENE3D)) {
            Showcase3D showcase = new Showcase3D();
            HBox.setHgrow(showcase, Priority.ALWAYS);
            showcase.setPrefSize(440, 420);
            app.account.addListener((o, old, account) -> showcase.setAccount(account));
            showcase.setAccount(app.account.get());
            middle.getChildren().add(showcase);
            Fx.reveal(showcase, 340);
        }

        Node card = playCard();
        getChildren().addAll(status, middle, card);

        Fx.reveal(status, 80);
        Fx.reveal(hero, 160);
        Fx.reveal(card, 260);

        app.play.stateProperty().addListener((o, old, state) -> onState(state));
        onState(app.play.stateProperty().get());
        startServerPolling();
    }

    private VBox hero() {
        Label eyebrow = new Label("BIENVENUE SUR");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label(LauncherConfig.NAME.toUpperCase(Locale.ROOT));
        title.getStyleClass().add("hero-title");
        Shimmer.apply(title, Color.web("#f1edff"), Color.web("#c3b4ff"), Color.web("#7dd3fc"));
        Label tagline = new Label(LauncherConfig.TAGLINE);
        tagline.getStyleClass().add("hero-tagline");
        Label version = new Label();
        version.textProperty().bind(app.play.versionLabelProperty());
        version.getStyleClass().add("chip");
        Label address = new Label(LauncherConfig.SERVER_HOST
                + (LauncherConfig.SERVER_PORT != 25565 ? ":" + LauncherConfig.SERVER_PORT : ""));
        address.getStyleClass().addAll("chip", "chip-muted");
        HBox chips = new HBox(10, version, address);
        VBox hero = new VBox(eyebrow, title, tagline, chips);
        hero.getStyleClass().add("hero");
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setMinWidth(Region.USE_PREF_SIZE);
        VBox.setMargin(chips, new Insets(18, 0, 0, 0));
        return hero;
    }

    private HBox playCard() {
        Avatar avatar = new Avatar(50, 12);
        app.account.addListener((o, old, account) -> avatar.setAccount(account));
        avatar.setAccount(app.account.get());
        Label hello = new Label("Prêt à jouer");
        hello.getStyleClass().add("muted-small");
        Label name = new Label();
        name.getStyleClass().add("player-name");
        name.textProperty().bind(app.account.map(a -> a.name()).orElse(""));
        VBox who = new VBox(2, hello, name);
        who.setAlignment(Pos.CENTER_LEFT);
        HBox player = new HBox(14, avatar, who);
        player.setAlignment(Pos.CENTER_LEFT);
        player.setMinWidth(Region.USE_PREF_SIZE);

        stepLabel.getStyleClass().add("progress-step");
        percentLabel.getStyleClass().add("progress-percent");
        detailLabel.getStyleClass().add("muted-small");
        HBox top = new HBox(stepLabel, Fx.spacer(), percentLabel);
        progressBox.getChildren().addAll(top, bar, detailLabel);
        progressBox.setAlignment(Pos.CENTER_LEFT);
        progressBox.setOpacity(0);
        HBox.setHgrow(progressBox, Priority.ALWAYS);
        HBox.setMargin(progressBox, new Insets(0, 12, 0, 28));

        HBox card = new HBox(player, progressBox, playButton());
        card.getStyleClass().addAll("glass-card", "play-card");
        card.setAlignment(Pos.CENTER_LEFT);
        return card;
    }

    /**
     * Le bouton Jouer : un halo flou qui « respire » derrière et un reflet qui le traverse.
     * Le halo est flouté une seule fois puis mis en cache : l'animer ne coûte presque rien.
     */
    private StackPane playButton() {
        play.setGraphic(Fx.icon(Icons.PLAY, 22));
        play.getStyleClass().add("play-button");
        play.setOnAction(e -> app.play.play());

        playGlow.getStyleClass().add("play-glow");
        playGlow.setEffect(new GaussianBlur(26));
        playGlow.setCache(true);
        playGlow.setCacheHint(CacheHint.SPEED);
        playGlow.setMouseTransparent(true);
        Timeline breathe = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(playGlow.opacityProperty(), 0.45, Interpolator.EASE_BOTH),
                        new KeyValue(playGlow.scaleXProperty(), 0.9, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(2200),
                        new KeyValue(playGlow.opacityProperty(), 0.9, Interpolator.EASE_BOTH),
                        new KeyValue(playGlow.scaleXProperty(), 1.04, Interpolator.EASE_BOTH)));
        breathe.setAutoReverse(true);
        breathe.setCycleCount(Timeline.INDEFINITE);
        breathe.play();

        shine.getStyleClass().add("play-shine");
        shine.setManaged(false);
        shine.setRotate(18);
        shine.setMouseTransparent(true);
        Pane shineLayer = new Pane(shine);
        shineLayer.setMouseTransparent(true);
        Rectangle clip = new Rectangle();
        clip.setArcWidth(32);
        clip.setArcHeight(32);
        clip.widthProperty().bind(shineLayer.widthProperty());
        clip.heightProperty().bind(shineLayer.heightProperty());
        shineLayer.setClip(clip);
        shineLayer.heightProperty().addListener((o, a, h) -> {
            shine.resizeRelocate(0, -h.doubleValue() * 0.5, 46, h.doubleValue() * 2);
            shine.setTranslateX(-120);
        });

        play.hoverProperty().addListener((o, was, hover) -> {
            if (hover && !play.isDisabled()) sweepShine(shineLayer);
        });
        Timeline periodic = new Timeline(new KeyFrame(Duration.seconds(6), e -> {
            if (!play.isDisabled()) sweepShine(shineLayer);
        }));
        periodic.setCycleCount(Timeline.INDEFINITE);
        periodic.play();

        StackPane stack = new StackPane(playGlow, play, shineLayer);
        stack.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return stack;
    }

    private void sweepShine(Pane layer) {
        TranslateTransition sweep = new TranslateTransition(Duration.millis(750), shine);
        sweep.setFromX(-120);
        sweep.setToX(layer.getWidth() + 60);
        sweep.setInterpolator(Fx.EASE_IN_OUT);
        sweep.play();
    }

    private void onState(PlayController.State state) {
        boolean installing = state == PlayController.State.INSTALLING;
        play.setDisable(state != PlayController.State.IDLE);
        playGlow.setVisible(state == PlayController.State.IDLE);
        play.setText(switch (state) {
            case IDLE -> "JOUER";
            case INSTALLING -> "PRÉPARATION";
            case RUNNING -> "EN JEU";
        });
        FadeTransition fade = new FadeTransition(Duration.millis(300), progressBox);
        fade.setToValue(installing ? 1 : 0);
        fade.play();
        if (installing) {
            bar.start();
            progressTicker.start();
        } else {
            progressTicker.stop();
            bar.stop();
        }
    }

    private void startServerPolling() {
        Timeline poll = new Timeline(new KeyFrame(Duration.ZERO, e -> refreshStatus()),
                new KeyFrame(Duration.seconds(30)));
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.play();
    }

    private void refreshStatus() {
        Fx.async(() -> ServerPinger.ping(LauncherConfig.SERVER_HOST, LauncherConfig.SERVER_PORT), s -> {
            statusDot.pseudoClassStateChanged(ONLINE, s.online());
            statusText.setText(s.online() ? "Serveur en ligne" : "Serveur hors ligne");
            playersBox.setVisible(s.online());
            playersBox.setManaged(s.online());
            playersText.setText(s.players() + " / " + s.maxPlayers());
            pingText.setText(s.online() ? s.pingMs() + " ms" : "");
            if (s.online()) {
                ringPulse.play();
            } else {
                ringPulse.stop();
                statusRing.setOpacity(0);
            }
        }, e -> { });
    }
}
