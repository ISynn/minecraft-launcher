// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui.screens.news;

import fr.launcher.LauncherConfig;
import fr.launcher.core.news.NewsArticle;
import fr.launcher.core.news.NewsFeed;
import fr.launcher.ui.AppContext;
import fr.launcher.ui.Icons;
import fr.launcher.ui.components.EmptyState;
import fr.launcher.ui.components.NewsCards;
import fr.launcher.ui.components.Ui;
import fr.launcher.ui.shell.Screen;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/** Actualités : l'article le plus récent à la une, puis les autres en grille. Clic → article sur le site. */
public final class NewsScreen implements Screen {

    private static final int COLUMNS = 3;

    private final AppContext app;
    private final ScrollPane root;
    private final VBox content = new VBox(20);
    private final HBox source = new HBox();

    public NewsScreen(AppContext app) {
        this.app = app;
        Button refresh = Ui.secondary("Actualiser", Icons.REFRESH);
        refresh.setOnAction(e -> app.news.refresh());
        refresh.disableProperty().bind(app.news.loadingProperty());
        Button blog = Ui.ghost("Tout le blog", Icons.OPEN_EXTERNAL);
        blog.setOnAction(e -> app.open(LauncherConfig.BLOG_URL));
        source.setAlignment(Pos.CENTER);

        root = Ui.page(
                Ui.pageHeader("Actualités", "Annonces, mises à jour et événements du blog public de PokeIsland.",
                        source, refresh, blog),
                content);

        app.news.feedProperty().addListener((o, old, feed) -> show(feed));
        app.news.loadingProperty().addListener((o, was, loading) -> {
            if (loading && app.news.feedProperty().get() == null) showLoading();
        });
        if (app.news.feedProperty().get() != null) show(app.news.feedProperty().get());
        else showLoading();
    }

    @Override
    public void onShow() {
        app.news.ensureLoaded();
    }

    private void showLoading() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("spinner");
        spinner.setMaxSize(32, 32);
        Label text = Ui.muted("Chargement des actualités…");
        VBox box = new VBox(12, spinner, text);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("empty-state");
        content.getChildren().setAll(box);
    }

    private void show(NewsFeed feed) {
        if (feed == null) return;
        source.getChildren().setAll(NewsCards.sourceBadge(feed, app.news.sourceName()));
        List<NewsArticle> articles = feed.articles();
        if (articles.isEmpty()) {
            Button retry = Ui.secondary("Réessayer", Icons.REFRESH);
            retry.setOnAction(e -> app.news.refresh());
            content.getChildren().setAll(new EmptyState(Icons.NEWS, "Aucune actualité",
                    "Le blog n'a renvoyé aucun article pour le moment.", retry));
            return;
        }
        content.getChildren().setAll(NewsCards.featured(articles.getFirst(), app::open));
        List<NewsArticle> rest = articles.subList(1, articles.size());
        for (int i = 0; i < rest.size(); i += COLUMNS) {
            Node[] row = new Node[COLUMNS];
            for (int c = 0; c < COLUMNS; c++) {
                // Cellule vide pour garder trois colonnes de même largeur sur la dernière ligne.
                row[c] = i + c < rest.size() ? NewsCards.card(rest.get(i + c), app::open) : new VBox();
            }
            GridPane grid = Ui.columns(row);
            grid.setVgap(18);
            content.getChildren().add(grid);
        }
    }

    @Override
    public Node view() {
        return root;
    }
}
