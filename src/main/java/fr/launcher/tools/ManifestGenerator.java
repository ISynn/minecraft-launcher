// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.tools;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Génère le modpack.json à partir d'un dossier (mods/, config/…) que tu hébergeras en ligne.
 *
 * <pre>
 * mvn compile
 * java -cp target/classes fr.launcher.tools.ManifestGenerator &lt;dossier&gt; &lt;url-de-base&gt; [version-mc] [version-fabric]
 * java -cp target/classes fr.launcher.tools.ManifestGenerator modpack https://monsite.fr/modpack/ 26.3
 * </pre>
 *
 * Seul le dossier mods/ est « géré » : les mods en trop chez le joueur y sont supprimés.
 * Les autres dossiers (config/…) sont seulement installés et mis à jour.
 */
public final class ManifestGenerator {

    private static final List<String> MANAGED = List.of("mods");

    private ManifestGenerator() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage : ManifestGenerator <dossier> <url-de-base> [version-mc] [version-fabric]");
            return;
        }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        String baseUrl = args[1].endsWith("/") ? args[1] : args[1] + "/";
        Path output = root.resolve("modpack.json");

        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile).filter(p -> !p.equals(output)).sorted().toList();
        }

        StringBuilder json = new StringBuilder("{\n");
        if (args.length > 2) json.append("  \"minecraft\": \"").append(escape(args[2])).append("\",\n");
        if (args.length > 3) json.append("  \"fabricLoader\": \"").append(escape(args[3])).append("\",\n");
        json.append("  \"managedFolders\": [")
                .append(MANAGED.stream().map(m -> "\"" + m + "\"").collect(Collectors.joining(", ")))
                .append("],\n  \"files\": [\n");
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            String relative = root.relativize(file).toString().replace('\\', '/');
            json.append("    { \"path\": \"").append(escape(relative))
                    .append("\", \"url\": \"").append(escape(baseUrl + encodePath(relative)))
                    .append("\", \"sha1\": \"").append(sha1(file))
                    .append("\", \"size\": ").append(Files.size(file))
                    .append(" }").append(i < files.size() - 1 ? ",\n" : "\n");
        }
        json.append("  ]\n}\n");

        Files.writeString(output, json, StandardCharsets.UTF_8);
        System.out.println(files.size() + " fichiers -> " + output);
        System.out.println("Envoie tout le dossier sur " + baseUrl + " puis mets l'URL de modpack.json dans LauncherConfig.");
    }

    private static String encodePath(String path) {
        return Stream.of(path.split("/"))
                .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String sha1(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = in.read(buffer)) > 0; ) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
