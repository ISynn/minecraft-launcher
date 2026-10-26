package fr.launcher.core.news;

import java.io.IOException;
import java.util.List;

/**
 * Source d'actualités. Remplaçable : le jour où une API officielle existe, une nouvelle implémentation
 * suffit, sans toucher à l'interface.
 */
public interface NewsProvider {

    /** Nom affiché à l'utilisateur (par ex. « Blog PokeIsland »). */
    String name();

    List<NewsArticle> fetch() throws IOException, InterruptedException;
}
