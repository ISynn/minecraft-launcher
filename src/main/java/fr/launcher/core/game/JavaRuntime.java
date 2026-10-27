// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.launcher.core.download.DownloadTask;
import fr.launcher.core.download.Downloader;
import fr.launcher.core.download.Progress;
import fr.launcher.util.Http;
import fr.launcher.util.Json;
import fr.launcher.util.Os;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Installe le Java officiel fourni par Mojang pour la version jouée (Java 25 pour 26.x).
 * Le joueur n'a rien à installer et tout le monde joue avec le même runtime.
 */
final class JavaRuntime {

    private static final String INDEX = "https://launchermeta.mojang.com/v1/products/java-runtime/"
            + "2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

    private final Path runtimesDir;

    JavaRuntime(Path gameDir) {
        this.runtimesDir = gameDir.resolve("runtime");
    }

    /**
     * Installe (ou vérifie) le runtime demandé et renvoie le chemin de l'exécutable java.
     * Hors ligne, ou si Mojang ne répond pas, le runtime déjà installé est utilisé tel quel.
     */
    Path install(JsonObject vanilla, Progress progress, boolean online) throws IOException, InterruptedException {
        String component = vanilla.has("javaVersion")
                ? vanilla.getAsJsonObject("javaVersion").get("component").getAsString()
                : "jre-legacy";
        Path home = runtimesDir.resolve(component);
        Path java = executable(home);
        if (!online) {
            if (Files.isRegularFile(java)) return java;
            throw new Downloader.OfflineException("Java n'est pas encore installé : une connexion est nécessaire.");
        }

        JsonObject files;
        try {
            JsonObject platforms = Http.getJson(INDEX);
            JsonObject platform = platforms.getAsJsonObject(Os.javaRuntimePlatform());
            JsonArray builds = platform == null ? null : platform.getAsJsonArray(component);
            if (builds == null || builds.isEmpty()) {
                return currentJava(); // pas de runtime Mojang pour cette plateforme
            }
            JsonObject manifestRef = builds.get(0).getAsJsonObject().getAsJsonObject("manifest");
            files = Http.getJson(manifestRef.get("url").getAsString()).getAsJsonObject("files");
        } catch (IOException e) {
            // Index ou manifeste injoignable : le runtime déjà installé suffit pour jouer.
            if (!Files.isRegularFile(java)) throw e;
            progress.setOffline(true);
            return java;
        }

        List<DownloadTask> tasks = new ArrayList<>();
        List<Path> executables = new ArrayList<>();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : files.entrySet()) {
            JsonObject file = entry.getValue().getAsJsonObject();
            Path target = home.resolve(entry.getKey());
            switch (file.get("type").getAsString()) {
                case "directory" -> Files.createDirectories(target);
                case "file" -> {
                    JsonObject raw = file.getAsJsonObject("downloads").getAsJsonObject("raw");
                    tasks.add(new DownloadTask(raw.get("url").getAsString(), target,
                            raw.get("sha1").getAsString(), raw.get("size").getAsLong(), false));
                    if (file.has("executable") && file.get("executable").getAsBoolean()) executables.add(target);
                }
                default -> { } // liens symboliques : uniquement sur macOS/Linux, créés ci-dessous
            }
        }
        Downloader.downloadAll(progress.step(), tasks, progress);

        if (Os.CURRENT != Os.WINDOWS) {
            for (Path exe : executables) {
                Files.setPosixFilePermissions(exe, PosixFilePermissions.fromString("rwxr-xr-x"));
            }
            for (Map.Entry<String, com.google.gson.JsonElement> entry : files.entrySet()) {
                JsonObject file = entry.getValue().getAsJsonObject();
                Path link = home.resolve(entry.getKey());
                if ("link".equals(Json.string(file, "type", "")) && !Files.exists(link)) {
                    Files.createSymbolicLink(link, Path.of(file.get("target").getAsString()));
                }
            }
        }
        return java;
    }

    private static Path executable(Path home) {
        return switch (Os.CURRENT) {
            case WINDOWS -> home.resolve("bin/javaw.exe");
            case MAC -> home.resolve("jre.bundle/Contents/Home/bin/java");
            case LINUX -> home.resolve("bin/java");
        };
    }

    private static Path currentJava() {
        String exe = Os.CURRENT == Os.WINDOWS ? "javaw.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", exe);
    }
}
