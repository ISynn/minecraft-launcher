package fr.launcher.core.news;

import fr.launcher.util.Http;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Actualités lues sur la page publique du blog (aucune API privée).
 *
 * <p>La page liste des cartes : vignette, catégorie, titre, auteur, date. Le résumé vient de la balise
 * {@code description} de chaque article. La lecture est volontairement tolérante : un champ manquant
 * est laissé vide, une carte sans titre ou sans lien est ignorée. Seuls les liens du site lui-même sont acceptés.</p>
 */
public final class BlogNewsProvider implements NewsProvider {

    private static final int MAX_ARTICLES = 6;
    private static final DateTimeFormatter FRENCH_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Pattern CARD = Pattern.compile("<a\\b[^>]*href=\"(/blog/[^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern IMAGE = Pattern.compile("<img\\b[^>]*?src=\"([^\"]+)\"");
    private static final Pattern CATEGORY = Pattern.compile("<p\\b[^>]*>\\s*<i\\b[^>]*>\\s*</i>\\s*([^<]+?)\\s*</p>");
    private static final Pattern TITLE = Pattern.compile("<h2\\b[^>]*>\\s*([^<]+?)\\s*</h2>");
    private static final Pattern AUTHOR = Pattern.compile("<img\\b[^>]*>\\s*<p>\\s*([^<]+?)\\s*</p>");
    private static final Pattern DATE = Pattern.compile("<p>\\s*(\\d{2}/\\d{2}/\\d{4})\\s*</p>");
    private static final Pattern RECENT = Pattern.compile(">\\s*New\\s*<");
    private static final Pattern DESCRIPTION = Pattern.compile("<meta\\s+name=\"description\"\\s+content=\"([^\"]*)\"");
    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#\\d+|amp|lt|gt|quot|apos|nbsp);");

    private final URI blog;

    public BlogNewsProvider(String blogUrl) {
        this.blog = URI.create(blogUrl);
    }

    @Override
    public String name() {
        return "Blog " + blog.getHost();
    }

    @Override
    public List<NewsArticle> fetch() throws IOException, InterruptedException {
        List<NewsArticle> cards = parseListing(Http.getText(blog.toString()), blog);
        if (cards.isEmpty()) throw new IOException("Aucun article reconnu sur " + blog);

        // Les résumés sont sur les pages des articles : on les lit en parallèle, un échec n'est pas bloquant.
        List<NewsArticle> result = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> summaries = new ArrayList<>();
            for (NewsArticle card : cards) {
                summaries.add(pool.submit(() -> parseDescription(Http.getText(card.url()))));
            }
            for (int i = 0; i < cards.size(); i++) {
                NewsArticle c = cards.get(i);
                String summary;
                try {
                    summary = summaries.get(i).get();
                } catch (ExecutionException e) {
                    summary = "";
                }
                result.add(new NewsArticle(c.title(), c.category(), c.author(), c.date(), summary, c.url(), c.imageUrl(),
                        c.recent()));
            }
        }
        return result;
    }

    /** Extrait les cartes d'articles de la page du blog, les plus récentes d'abord, sans doublon. */
    static List<NewsArticle> parseListing(String html, URI base) {
        Map<String, NewsArticle> byUrl = new LinkedHashMap<>();
        Matcher card = CARD.matcher(html);
        while (card.find()) {
            String url = sameSite(base, card.group(1));
            String body = card.group(2);
            String title = first(TITLE, body);
            if (url == null || title.isEmpty()) continue;
            String image = sameSite(base, first(IMAGE, body));
            byUrl.putIfAbsent(url, new NewsArticle(title, first(CATEGORY, body), first(AUTHOR, body),
                    isoDate(first(DATE, body)), "", url, image == null ? "" : image, RECENT.matcher(body).find()));
        }
        List<NewsArticle> list = new ArrayList<>(byUrl.values());
        list.sort(Comparator.comparing(NewsArticle::date).reversed());
        return list.size() > MAX_ARTICLES ? List.copyOf(list.subList(0, MAX_ARTICLES)) : list;
    }

    static String parseDescription(String html) {
        return first(DESCRIPTION, html);
    }

    /** Résout un lien relatif ; refuse tout ce qui ne pointe pas vers le site lui-même en HTTPS. */
    static String sameSite(URI base, String link) {
        if (link == null || link.isEmpty()) return null;
        try {
            URI resolved = base.resolve(link);
            boolean ok = "https".equals(resolved.getScheme()) && base.getHost().equalsIgnoreCase(resolved.getHost());
            return ok ? resolved.toString() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String first(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? decode(m.group(1)).strip() : "";
    }

    private static String isoDate(String frenchDate) {
        try {
            return frenchDate.isEmpty() ? "" : LocalDate.parse(frenchDate, FRENCH_DATE).toString();
        } catch (DateTimeParseException e) {
            return "";
        }
    }

    /** Décode les entités HTML courantes (&amp;#x27; → ', &amp;amp; → &amp;…). */
    static String decode(String text) {
        Matcher m = ENTITY.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String e = m.group(1);
            String r = switch (e) {
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "apos" -> "'";
                case "nbsp" -> " ";
                default -> {
                    try {
                        int code = e.startsWith("#x")
                                ? Integer.parseInt(e.substring(2), 16)
                                : Integer.parseInt(e.substring(1));
                        yield new String(Character.toChars(code));
                    } catch (IllegalArgumentException invalid) {
                        yield m.group(); // entité invalide : laissée telle quelle
                    }
                }
            };
            m.appendReplacement(sb, Matcher.quoteReplacement(r));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
