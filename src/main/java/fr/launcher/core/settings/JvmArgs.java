// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Options Java saisies par le joueur : découpage (guillemets compris) et liste des options autorisées.
 *
 * <p>Refusées : la mémoire (réglée par le curseur), le classpath, et tout ce qui charge ou exécute du code
 * en dehors du jeu (agents, commandes lancées en cas d'erreur).</p>
 */
public final class JvmArgs {

    /** Résultat de l'analyse : les arguments, ou un message d'erreur clair. */
    public record Parsed(List<String> args, String error) {
        public boolean valid() {
            return error == null;
        }
    }

    private static final List<String> ALLOWED_PREFIXES = List.of("-XX:", "-D", "-Xss", "-Xmn", "-Xlog");
    private static final List<String> FORBIDDEN_PREFIXES = List.of(
            "-Xmx", "-Xms", "-javaagent", "-agentlib", "-agentpath", "-cp", "-classpath", "--class-path", "-jar",
            "-XX:OnError", "-XX:OnOutOfMemoryError", "-XX:+StartAttachListener");

    private JvmArgs() {
    }

    public static Parsed parse(String text) {
        List<String> args = split(text == null ? "" : text);
        if (args == null) return new Parsed(List.of(), "Guillemet non fermé.");
        for (String arg : args) {
            String lower = arg.toLowerCase(Locale.ROOT);
            for (String forbidden : FORBIDDEN_PREFIXES) {
                if (lower.startsWith(forbidden.toLowerCase(Locale.ROOT))) {
                    return new Parsed(List.of(), reason(forbidden, arg));
                }
            }
            if (ALLOWED_PREFIXES.stream().noneMatch(arg::startsWith)) {
                return new Parsed(List.of(), "« " + arg + " » n'est pas une option Java autorisée "
                        + "(acceptées : -XX:, -D, -Xss, -Xmn, -Xlog).");
            }
        }
        return new Parsed(List.copyOf(args), null);
    }

    private static String reason(String forbidden, String arg) {
        if (forbidden.equals("-Xmx") || forbidden.equals("-Xms")) {
            return "La mémoire se règle avec le curseur « Mémoire allouée », pas avec " + arg + ".";
        }
        return "« " + arg + " » est refusée : elle permettrait de charger ou d'exécuter du code hors du jeu.";
    }

    /** Découpe sur les espaces en respectant les guillemets ; null si un guillemet n'est pas fermé. */
    static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        for (char c : text.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
                any = true;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (any) out.add(current.toString());
                current.setLength(0);
                any = false;
            } else {
                current.append(c);
                any = true;
            }
        }
        if (quoted) return null;
        if (any) out.add(current.toString());
        return out;
    }
}
