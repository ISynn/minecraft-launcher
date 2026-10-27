package fr.launcher.ui.components;

import fr.launcher.core.news.NewsArticle;
import fr.launcher.core.news.NewsFeed;
import fr.launcher.ui.Fx;
import fr.launcher.ui.Icons;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Éléments d'affichage des actualités, partagés par le carrousel de l'accueil et l'écran Actualités. */
public final class NewsCards {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)
            .withZone(ZoneId.systemDefault());
    private static final int THUMBNAIL_WIDTH = 800;
    /** Vignettes déjà demandées : chacune n'est téléchargée et décodée qu'une fois. */
    private static final Map<String, Image> IMAGES = new HashMap<>();

    private NewsCards() {
    }

    /** Provenance des articles, toujours visible : blog en direct, copie hors ligne datée, ou démo. */
    public static Label sourceBadge(NewsFeed feed, String sourceName) {
        return switch (feed.source()) {
            case LIVE -> Ui.badge(sourceName, Ui.Tone.NEUTRAL);
            case CACHE -> {
                Label badge = Ui.badge("Hors ligne · copie du " + DAY.format(feed.fetchedAt()), Ui.Tone.WARNING);
                badge.setTooltip(Ui.tooltip("Le blog est injoignable : dernière version enregistrée."));
                yield badge;
            }
            case DEMO -> Ui.demoBadge();
        };
    }

    public static Image image(NewsArticle article) {
        if (article.imageUrl().isBlank()) return null;
        // Décodée à 800 px de large (la plus grande carte en affiche ~550 à 125 %), et non à 1200 : moins de mémoire.
        return IMAGES.computeIfAbsent(article.imageUrl(), url -> new Image(url, THUMBNAIL_WIDTH, 0, true, true, true));
    }

    /** Catégorie, mention « Nouveau » et date, sur une ligne. */
    public static HBox meta(NewsArticle article, DateTimeFormatter format, String extraBadgeClass) {
        HBox meta = new HBox(8);
        meta.setAlignment(Pos.CENTER_LEFT);
        if (!article.category().isBlank()) {
            meta.getChildren().add(styled(Ui.badge(article.category(), Ui.Tone.ACCENT), extraBadgeClass));
        }
        if (article.recent()) meta.getChildren().add(styled(Ui.badge("Nouveau", Ui.Tone.DANGER), extraBadgeClass));
        article.localDate().ifPresent(d -> {
            Label date = new Label((format == null ? SHORT_DATE : format).format(d));
            date.getStyleClass().add("news-date");
            // Sur une image, la date devient un badge sombre comme le reste, sinon elle se perd.
            if (extraBadgeClass != null) date.getStyleClass().addAll("badge", "badge-neutral", extraBadgeClass);
            meta.getChildren().add(date);
        });
        return meta;
    }

    private static Node styled(Label badge, String extraClass) {
        if (extraClass != null) badge.getStyleClass().add(extraClass);
        return badge;
    }

    /** Grande carte horizontale pour l'article à la une. */
    public static Node featured(NewsArticle article, Consumer<String> open) {
        CoverImage cover = new CoverImage();
        cover.setImage(image(article));
        cover.getStyleClass().add("news-featured-image");
        cover.setPrefSize(440, 250);
        cover.setMinWidth(440);
        clip(cover, 16);

        Label title = text(article.title(), "news-featured-title");
        Label summary = text(article.summary(), "news-summary");
        VBox body = new VBox(12, meta(article, DATE, null), title, summary, Fx.spacer(), readMore(article));
        body.getStyleClass().add("news-featured-body");
        HBox.setHgrow(body, Priority.ALWAYS);

        HBox card = new HBox(cover, body);
        card.getStyleClass().addAll("card", "news-card", "news-featured");
        return clickable(card, article, open);
    }

    /** Carte verticale de la grille. */
    public static Node card(NewsArticle article, Consumer<String> open) {
        CoverImage cover = new CoverImage();
        cover.setImage(image(article));
        cover.setPrefHeight(150);
        cover.setMinHeight(150);
        cover.getStyleClass().add("news-card-image");

        Label title = text(article.title(), "news-card-title");
        Label summary = text(article.summary(), "news-summary");
        VBox body = new VBox(8, meta(article, null, null), title, summary, Fx.spacer(), readMore(article));
        body.getStyleClass().add("news-card-body");
        VBox.setVgrow(body, Priority.ALWAYS);

        VBox card = new VBox(cover, body);
        card.getStyleClass().addAll("card", "news-card");
        clip(card, 18);
        return clickable(card, article, open);
    }

    private static Label text(String value, String style) {
        Label label = new Label(value);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        label.setVisible(!value.isBlank());
        label.managedProperty().bind(label.visibleProperty());
        return label;
    }

    private static Node readMore(NewsArticle article) {
        Label link = new Label("Lire l'article");
        link.getStyleClass().add("news-read");
        link.setGraphic(Fx.icon(Icons.OPEN_EXTERNAL, 14));
        link.setContentDisplay(ContentDisplay.RIGHT);
        return link;
    }

    /** Toute la carte est cliquable, et activable au clavier (Tab puis Entrée). */
    private static Node clickable(Region card, NewsArticle article, Consumer<String> open) {
        card.setCursor(Cursor.HAND);
        card.setFocusTraversable(true);
        card.setAccessibleText(article.title() + " — ouvrir l'article");
        card.setOnMouseClicked(e -> open.accept(article.url()));
        card.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                open.accept(article.url());
                e.consume();
            }
        });
        Tooltip.install(card, Ui.tooltip("Lire l'article sur le site ↗"));
        return card;
    }

    private static void clip(Region region, double radius) {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());
        region.setClip(clip);
    }

    /** Pour le carrousel : la même ligne de méta, avec des badges lisibles sur une image. */
    public static HBox overlayMeta(NewsArticle article) {
        return meta(article, null, "carousel-chip");
    }
}
