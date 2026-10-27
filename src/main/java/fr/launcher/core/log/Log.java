// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.log;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Journal du launcher : {@code launcher/logs/launcher-0.log} (3 fichiers tournants de 2 Mo).
 *
 * <p>Chaque ligne passe par {@link #redact(String)} : un jeton d'authentification ne peut pas
 * finir dans un fichier qu'un joueur enverra au support.</p>
 */
public final class Log {

    private static final String ROOT = "fr.launcher";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    /** Jetons à masquer : JWT, identité Xbox, valeurs nommées (clé = valeur / clé: valeur), --accessToken, Bearer. */
    private static final List<Pattern> SECRETS = List.of(
            Pattern.compile("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]+"),
            // Avant les valeurs nommées : « identityToken=XBL3.0 x=… » doit être masqué en entier.
            Pattern.compile("(XBL3\\.0 x=)(\\S+)"),
            Pattern.compile("(?i)(\"?(?:access_?token|refresh_?token|id_?token|device_?code|xsts_?token|identityToken|"
                    + "auth_access_token|auth_session)\"?\\s*[:=]\\s*\"?)([^\\s\",}&]+)"),
            Pattern.compile("(--accessToken\\s+)(\\S+)"),
            Pattern.compile("(?i)(Bearer\\s+)(\\S+)"));

    private static final String MASK = "<masqué>";

    private Log() {
    }

    /** Branche le journal sur un dossier. Sans appel (tests), les messages vont seulement sur la console. */
    public static void init(Path dir) {
        Logger root = Logger.getLogger(ROOT);
        root.setUseParentHandlers(false);
        root.setLevel(Level.INFO);
        for (Handler h : root.getHandlers()) root.removeHandler(h);

        Formatter formatter = new LineFormatter();
        ConsoleHandler console = new ConsoleHandler();
        console.setFormatter(formatter);
        root.addHandler(console);
        try {
            Files.createDirectories(dir);
            FileHandler file = new FileHandler(dir.resolve("launcher-%g.log").toString(), 2 * 1024 * 1024, 3, true);
            file.setEncoding("UTF-8");
            file.setFormatter(formatter);
            root.addHandler(file);
        } catch (IOException | SecurityException e) {
            root.log(Level.WARNING, "Journal sur disque indisponible, console seulement", e);
        }
    }

    /** Vide et ferme le journal (fermeture du launcher) : le fichier n'est plus verrouillé. */
    public static void close() {
        Logger root = Logger.getLogger(ROOT);
        for (Handler h : root.getHandlers()) {
            root.removeHandler(h);
            h.close();
        }
    }

    /** Journal détaillé (diagnostic) : inclut les messages de niveau FINE dans le fichier. */
    public static void setVerbose(boolean verbose) {
        Logger.getLogger(ROOT).setLevel(verbose ? Level.FINE : Level.INFO);
    }

    public static Logger get(Class<?> type) {
        return Logger.getLogger(ROOT + "." + type.getSimpleName());
    }

    /** Remplace tout ce qui ressemble à un jeton par « &lt;masqué&gt; ». */
    public static String redact(String text) {
        if (text == null || text.isEmpty()) return text;
        String out = SECRETS.getFirst().matcher(text).replaceAll(MASK);
        for (Pattern p : SECRETS.subList(1, SECRETS.size())) {
            Matcher m = p.matcher(out);
            out = m.replaceAll(r -> Matcher.quoteReplacement(r.group(1) + MASK));
        }
        return out;
    }

    private static final class LineFormatter extends Formatter {
        @Override
        public String format(LogRecord r) {
            String name = r.getLoggerName();
            String source = name.startsWith(ROOT + ".") ? name.substring(ROOT.length() + 1) : name;
            StringBuilder sb = new StringBuilder()
                    .append('[').append(TIME.format(Instant.ofEpochMilli(r.getMillis()))).append("] [")
                    .append(String.format("%-7s", r.getLevel().getName())).append("] [").append(source).append("] ")
                    .append(formatMessage(r)).append(System.lineSeparator());
            if (r.getThrown() != null) {
                StringWriter trace = new StringWriter();
                r.getThrown().printStackTrace(new PrintWriter(trace));
                sb.append(trace);
            }
            return redact(sb.toString());
        }
    }
}
