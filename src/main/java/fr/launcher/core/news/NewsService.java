package fr.launcher.core.news;

import com.google.gson.reflect.TypeToken;
import fr.launcher.core.log.Log;
import fr.launcher.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fournit les actualités avec repli : source en ligne, sinon dernière copie sur le disque,
 * sinon exemples de démonstration. La provenance accompagne toujours les articles.
 */
public final class NewsService {

    private static final Logger LOG = Log.get(NewsService.class);
    private static final String DEMO_RESOURCE = "/fr/launcher/assets/news-demo.json";

    private record Cache(String fetchedAt, List<NewsArticle> articles) {
    }

    private final NewsProvider provider;
    private final Path cacheFile;

    public NewsService(NewsProvider provider, Path cacheFile) {
        this.provider = provider;
        this.cacheFile = cacheFile;
    }

    public String sourceName() {
        return provider.name();
    }

    /** À appeler hors du thread graphique (réseau). Ne lève jamais d'exception : il y a toujours un repli. */
    public NewsFeed load() {
        try {
            List<NewsArticle> articles = provider.fetch();
            Instant now = Instant.now();
            saveCache(new Cache(now.toString(), articles));
            LOG.info(articles.size() + " actualités chargées depuis " + provider.name());
            return new NewsFeed(articles, NewsFeed.Source.LIVE, now);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Actualités indisponibles en ligne (" + e.getMessage() + "), repli local");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (Files.isRegularFile(cacheFile)) {
                Cache cache = Json.read(cacheFile, Cache.class);
                if (cache != null && cache.articles() != null && !cache.articles().isEmpty()) {
                    return new NewsFeed(cache.articles(), NewsFeed.Source.CACHE, Instant.parse(cache.fetchedAt()));
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Cache des actualités illisible", e);
            Json.quarantine(cacheFile);
        }
        return new NewsFeed(demo(), NewsFeed.Source.DEMO, null);
    }

    private void saveCache(Cache cache) {
        try {
            Json.write(cacheFile, cache);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cache des actualités non enregistré", e);
        }
    }

    static List<NewsArticle> demo() {
        try (InputStream in = NewsService.class.getResourceAsStream(DEMO_RESOURCE)) {
            if (in == null) return List.of();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                List<NewsArticle> list = Json.GSON.fromJson(reader, new TypeToken<List<NewsArticle>>() { }.getType());
                return list == null ? List.of() : list;
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Actualités de démonstration illisibles", e);
            return List.of();
        }
    }
}
