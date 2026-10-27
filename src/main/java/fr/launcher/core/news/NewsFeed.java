// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.news;

import java.time.Instant;
import java.util.List;

/**
 * Les articles à afficher et leur provenance, que l'interface doit toujours rendre visible.
 *
 * @param fetchedAt moment où les articles ont été récupérés à la source (null pour la démo)
 */
public record NewsFeed(List<NewsArticle> articles, Source source, Instant fetchedAt) {

    public enum Source {
        /** Récupérés à l'instant sur le blog public. */
        LIVE,
        /** Hors ligne : dernière copie enregistrée sur le disque. */
        CACHE,
        /** Aucune source disponible : exemples fictifs fournis avec le launcher. */
        DEMO
    }
}
