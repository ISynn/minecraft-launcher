// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.news;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Un article d'actualité.
 *
 * @param date     date de publication au format ISO (aaaa-mm-jj), vide si inconnue
 * @param url      page complète de l'article
 * @param imageUrl vignette (vide si aucune : l'interface affiche un visuel de secours)
 * @param recent   marqué « New » par la source
 */
public record NewsArticle(String title, String category, String author, String date, String summary,
                          String url, String imageUrl, boolean recent) {

    public Optional<LocalDate> localDate() {
        try {
            return date == null || date.isBlank() ? Optional.empty() : Optional.of(LocalDate.parse(date));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
