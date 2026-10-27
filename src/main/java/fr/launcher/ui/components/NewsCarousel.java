// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui.components;

import fr.launcher.core.news.NewsArticle;
import fr.launcher.core.news.NewsFeed;
import fr.launcher.ui.Fx;
import fr.launcher.ui.Icons;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

/**
 * Carrousel d'actualités : une carte à la fois, défilement automatique toutes les 7 s (en pause au survol
 * et au focus), flèches, points et clavier (← → Entrée). La provenance des articles est toujours affichée.
 */
public final class NewsCarousel extends StackPane {

    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final double RADIUS = 18;

    private final Consumer<String> openUrl;
    private final StackPane slides = new StackPane();
    private final HBox dots = new HBox(6);
    private final HBox sourceBox = new HBox();
    private final HBox controls;
    /** Délai avant la carte suivante, relancé après chaque changement (manuel ou automatique). */
    private final PauseTransition autoplay = new PauseTransition(Duration.seconds(7));
    private List<NewsArticle> articles = List.of();
    private int index;
    private boolean running;

    public NewsCarousel(Consumer<String> openUrl) {
        this.openUrl = openUrl;
        getStyleClass().add("news-carousel");
        setFocusTraversable(true);
        setAccessibleText("Actualités");

        Rectangle clip = new Rectangle();
        clip.setArcWidth(RADIUS * 2);
        clip.setArcHeight(RADIUS * 2);
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        Button prev = Ui.icon(Icons.CHEVRON_LEFT, "Actualité précédente");
        prev.getStyleClass().add("carousel-arrow");
        prev.setOnAction(e -> go(index - 1));
        Button next = Ui.icon(Icons.CHEVRON_RIGHT, "Actualité suivante");
        next.getStyleClass().add("carousel-arrow");
        next.setOnAction(e -> go(index + 1));
        dots.setAlignment(Pos.CENTER);
        controls = new HBox(6, prev, dots, next);
        controls.setAlignment(Pos.CENTER);
        controls.getStyleClass().add("carousel-controls");
        controls.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(controls, Pos.BOTTOM_RIGHT);

        sourceBox.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        sourceBox.getStyleClass().add("carousel-source");
        StackPane.setAlignment(sourceBox, Pos.TOP_RIGHT);

        // Bordure dessinée au-dessus des diapositives : sinon les images la recouvrent dans les coins.
        Region frame = new Region();
        frame.getStyleClass().add("carousel-frame");
        frame.setMouseTransparent(true);
        getChildren().addAll(slides, sourceBox, controls, frame);

        autoplay.setOnFinished(e -> next());
        // Pause pendant le survol, ou quand le carrousel est parcouru au clavier. Un simple clic sur une flèche
        // ne doit pas le figer : le focus « souris » n'est pas pris en compte.
        hoverProperty().addListener((o, a, b) -> updateAutoplay());
        focusVisibleProperty().addListener((o, a, b) -> updateAutoplay());
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.LEFT) go(index - 1);
            else if (e.getCode() == KeyCode.RIGHT) go(index + 1);
            else if (e.getCode() == KeyCode.ENTER && !articles.isEmpty()) openUrl.accept(articles.get(index).url());
            else return;
            e.consume();
        });
        showLoading();
    }

    public void showLoading() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("spinner");
        spinner.setMaxSize(26, 26);
        Label text = new Label("Chargement des actualités…");
        text.getStyleClass().add("text-muted");
        VBox box = new VBox(10, spinner, text);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("carousel-placeholder");
        slides.getChildren().setAll(box);
        controls.setVisible(false);
        sourceBox.getChildren().clear();
    }

    public void setFeed(NewsFeed feed, String sourceName) {
        articles = feed.articles();
        index = 0;
        sourceBox.getChildren().setAll(chip(NewsCards.sourceBadge(feed, sourceName)));
        if (articles.isEmpty()) {
            slides.getChildren().setAll(
                    new EmptyState(Icons.NEWS, "Aucune actualité", "Rien à afficher pour le moment."));
            controls.setVisible(false);
            return;
        }
        dots.getChildren().clear();
        for (int i = 0; i < articles.size(); i++) {
            int target = i;
            Region dot = new Region();
            dot.getStyleClass().add("carousel-dot");
            dot.setOnMouseClicked(e -> go(target));
            dot.setCursor(Cursor.HAND);
            dots.getChildren().add(dot);
        }
        controls.setVisible(articles.size() > 1);
        slides.getChildren().setAll(slide(articles.getFirst()));
        updateDots();
        // Précharge la vignette suivante pour que la transition ne montre pas une carte vide.
        if (articles.size() > 1) NewsCards.image(articles.get(1));
        updateAutoplay();
    }

    /** Démarre / arrête le défilement automatique (écran visible ou non). */
    public void setRunning(boolean value) {
        running = value;
        updateAutoplay();
    }

    private void updateAutoplay() {
        boolean wanted = running && articles.size() > 1 && !isHover() && !isFocusVisible();
        if (!wanted) autoplay.stop();
        else if (autoplay.getStatus() != Animation.Status.RUNNING) autoplay.playFromStart();
    }

    private void next() {
        go(index + 1);
    }

    private void go(int target) {
        if (articles.size() < 2) return;
        int n = articles.size();
        int nextIndex = ((target % n) + n) % n;
        if (nextIndex == index) return;
        boolean forward = target > index;
        index = nextIndex;
        Node old = slides.getChildren().getLast();
        Node fresh = slide(articles.get(index));
        slides.getChildren().add(fresh);
        fresh.setOpacity(0);
        fresh.setTranslateX(forward ? 28 : -28);
        FadeTransition in = new FadeTransition(Duration.millis(320), fresh);
        in.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(380), fresh);
        slide.setToX(0);
        ParallelTransition anim = new ParallelTransition(in, slide);
        anim.setInterpolator(Fx.EASE_OUT);
        anim.setOnFinished(e -> slides.getChildren().remove(old));
        anim.play();
        updateDots();
        if (index + 1 < n) NewsCards.image(articles.get(index + 1));
        autoplay.stop();
        updateAutoplay();
    }

    private void updateDots() {
        for (int i = 0; i < dots.getChildren().size(); i++) {
            dots.getChildren().get(i).pseudoClassStateChanged(ACTIVE, i == index);
        }
    }

    private Node slide(NewsArticle article) {
        CoverImage cover = new CoverImage();
        cover.setImage(NewsCards.image(article));
        Region shade = new Region();
        shade.getStyleClass().add("carousel-shade");

        HBox meta = NewsCards.overlayMeta(article);

        Label title = new Label(article.title());
        title.getStyleClass().add("carousel-title");
        title.setWrapText(true);
        title.setMaxHeight(Region.USE_PREF_SIZE);
        Label summary = new Label(article.summary());
        summary.getStyleClass().add("carousel-summary");
        summary.setWrapText(true);
        summary.setVisible(!article.summary().isBlank());
        summary.managedProperty().bind(summary.visibleProperty());

        VBox text = new VBox(6, meta, title, summary);
        text.getStyleClass().add("carousel-text");
        text.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(text, Pos.BOTTOM_LEFT);

        StackPane slide = new StackPane(cover, shade, text);
        slide.getStyleClass().add("carousel-slide");
        slide.setCursor(Cursor.HAND);
        slide.setOnMouseClicked(e -> openUrl.accept(article.url()));
        Tooltip.install(slide, Ui.tooltip("Lire l'article sur le site ↗"));
        return slide;
    }

    /** Sur une vignette claire, un badge translucide devient illisible : fond sombre opaque. */
    private static Node chip(Node badge) {
        badge.getStyleClass().add("carousel-chip");
        return badge;
    }
}
