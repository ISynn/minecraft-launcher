// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.news;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlogNewsProviderTest {

    private static final URI BLOG = URI.create("https://pokeisland.fr/blog/");

    /** Même structure que la page publique du blog (contenu fictif). */
    private static String card(String slug, String title, String category, String author, String date, boolean recent) {
        return "<a class=\"w-full\" href=\"/blog/" + slug + "/\"><div class=\"flex-1\">"
                + "<img alt=\"" + title + "\" loading=\"lazy\" src=\"/assets/content/" + slug + "/thumbnail.jpg\"/>"
                + "<p class=\"inline-block\"><i class=\"mr-1.5 bi bi-newspaper\"></i>" + category + "</p>"
                + "<div class=\"flex\"><h2 class=\"font-bold\">" + title + "</h2>"
                + (recent ? "<span class=\"text-xs\">New</span>" : "") + "</div></div>"
                + "<div class=\"px-4\"><div class=\"flex\"><div class=\"flex\">"
                + "<img alt=\"" + author + "\" src=\"https://mc-heads.net/avatar/" + author + "/32\"/><p>" + author + "</p>"
                + "</div><div><p>" + date + "</p></div></div></div></a>";
    }

    @Test
    void litLesCartesDuBlog() {
        String html = "<html><body><a href=\"/\">Accueil</a>"
                + card("ancien", "Un ancien article", "Information", "Alice", "11/02/2026", false)
                + card("nouveau", "L&#x27;île s&#x27;agrandit &amp; plus", "Mise à jour", "Bob", "24/09/2026", true)
                + "</body></html>";
        List<NewsArticle> articles = BlogNewsProvider.parseListing(html, BLOG);

        assertEquals(2, articles.size());
        NewsArticle latest = articles.getFirst();
        assertEquals("L'île s'agrandit & plus", latest.title(), "entités HTML décodées");
        assertEquals("Mise à jour", latest.category());
        assertEquals("Bob", latest.author());
        assertEquals("2026-09-24", latest.date());
        assertEquals("https://pokeisland.fr/blog/nouveau/", latest.url());
        assertEquals("https://pokeisland.fr/assets/content/nouveau/thumbnail.jpg", latest.imageUrl());
        assertTrue(latest.recent());
        assertFalse(articles.get(1).recent());
    }

    @Test
    void ignoreLesDoublonsEtLesCartesSansTitre() {
        String html = card("a", "Article A", "Info", "X", "01/01/2026", false)
                + card("a", "Article A", "Info", "X", "01/01/2026", false)
                + "<a href=\"/blog/vide/\"><p>pas de titre</p></a>";
        assertEquals(1, BlogNewsProvider.parseListing(html, BLOG).size());
    }

    @Test
    void refuseLesLiensHorsDuSite() {
        assertEquals("https://pokeisland.fr/blog/x/", BlogNewsProvider.sameSite(BLOG, "/blog/x/"));
        assertNull(BlogNewsProvider.sameSite(BLOG, "https://exemple.com/piege"));
        assertNull(BlogNewsProvider.sameSite(BLOG, "http://pokeisland.fr/non-securise"));
        assertNull(BlogNewsProvider.sameSite(BLOG, "javascript:alert(1)"));
    }

    @Test
    void litLeResume() {
        assertEquals("Résumé de l'article",
                BlogNewsProvider.parseDescription("<head><meta name=\"description\" content=\"Résumé de l&#x27;article\"/>"));
        assertEquals("", BlogNewsProvider.parseDescription("<head></head>"));
    }

    @Test
    void entiteInvalideLaisseeTelleQuelle() {
        assertEquals("a &#99999999999; b", BlogNewsProvider.decode("a &#99999999999; b"));
    }

    @Test
    void lesExemplesDeDemoSontMarques() {
        List<NewsArticle> demo = NewsService.demo();
        assertFalse(demo.isEmpty());
        assertTrue(demo.stream().allMatch(a -> a.title().startsWith("Exemple") && a.category().equals("Démo")));
    }
}
