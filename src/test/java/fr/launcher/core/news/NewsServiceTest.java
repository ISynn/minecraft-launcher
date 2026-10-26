package fr.launcher.core.news;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NewsServiceTest {

    @TempDir
    Path dir;

    private static final NewsArticle ARTICLE = new NewsArticle("Titre", "Info", "Auteur", "2026-09-24", "Résumé",
            "https://pokeisland.fr/blog/titre/", "", true);

    /** Source réglable : en ligne ou en panne. */
    private static final class FakeProvider implements NewsProvider {
        boolean online = true;

        @Override
        public String name() {
            return "Faux blog";
        }

        @Override
        public List<NewsArticle> fetch() throws IOException {
            if (!online) throw new IOException("réseau coupé");
            return List.of(ARTICLE);
        }
    }

    @Test
    void enLigneLesArticlesSontFraisEtMisEnCache() {
        NewsService service = new NewsService(new FakeProvider(), dir.resolve("cache/news.json"));
        NewsFeed feed = service.load();
        assertEquals(NewsFeed.Source.LIVE, feed.source());
        assertEquals(List.of(ARTICLE), feed.articles());
    }

    @Test
    void horsLigneOnReafficheLaDerniereCopie() {
        FakeProvider provider = new FakeProvider();
        NewsService service = new NewsService(provider, dir.resolve("cache/news.json"));
        service.load();
        provider.online = false;
        NewsFeed feed = service.load();
        assertEquals(NewsFeed.Source.CACHE, feed.source());
        assertEquals(List.of(ARTICLE), feed.articles());
        assertNotNull(feed.fetchedAt(), "la date de la copie est affichée à l'utilisateur");
    }

    @Test
    void sansReseauNiCopieOnMontreLaDemo() {
        FakeProvider provider = new FakeProvider();
        provider.online = false;
        NewsFeed feed = new NewsService(provider, dir.resolve("cache/news.json")).load();
        assertEquals(NewsFeed.Source.DEMO, feed.source());
        assertFalse(feed.articles().isEmpty());
    }
}
