// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.launcher.core.download.DownloadTask;
import fr.launcher.core.download.Downloader;
import fr.launcher.core.download.Progress;
import fr.launcher.util.Http;
import fr.launcher.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Récupère le JSON de version Mojang et le profil Fabric, puis les fusionne. */
final class VersionResolver {

    private static final String MOJANG_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String FABRIC_META = "https://meta.fabricmc.net/v2/versions/loader/";

    private final Path versionsDir;
    private final Path librariesDir;

    VersionResolver(Path gameDir) {
        this.versionsDir = gameDir.resolve("versions");
        this.librariesDir = gameDir.resolve("libraries");
    }

    /** @param online faux = n'utilise que les fichiers déjà installés (aucune requête réseau) */
    GameProfile resolve(String mcVersion, String loaderVersion, Progress progress, boolean online)
            throws IOException, InterruptedException {
        JsonObject vanilla = vanillaJson(mcVersion, progress, online);
        String loader = loaderVersion == null || loaderVersion.isBlank()
                ? fabricLoader(mcVersion, online)
                : loaderVersion;
        JsonObject fabric = fabricJson(mcVersion, loader, online);

        // Les bibliothèques Fabric passent en premier : en cas de doublon, c'est leur version qui gagne.
        Map<String, GameProfile.Library> libraries = new LinkedHashMap<>();
        for (JsonElement e : fabric.getAsJsonArray("libraries")) {
            GameProfile.Library lib = mavenLibrary(e.getAsJsonObject());
            libraries.putIfAbsent(key(lib.name()), lib);
        }
        for (JsonElement e : vanilla.getAsJsonArray("libraries")) {
            JsonObject lib = e.getAsJsonObject();
            if (!Rules.allowed(lib.get("rules"), Map.of())) continue;
            JsonObject downloads = lib.getAsJsonObject("downloads");
            if (downloads == null || !downloads.has("artifact")) continue;
            JsonObject artifact = downloads.getAsJsonObject("artifact");
            String name = lib.get("name").getAsString();
            libraries.putIfAbsent(key(name), new GameProfile.Library(name,
                    librariesDir.resolve(artifact.get("path").getAsString()),
                    artifact.get("url").getAsString(),
                    Json.string(artifact, "sha1", null),
                    Json.number(artifact, "size", 0)));
        }

        List<JsonElement> jvm = new ArrayList<>();
        List<JsonElement> game = new ArrayList<>();
        collectArguments(vanilla, jvm, game);
        collectArguments(fabric, jvm, game);

        return new GameProfile(mcVersion, loader, fabric.get("mainClass").getAsString(), vanilla,
                List.copyOf(libraries.values()), jvm, game);
    }

    private JsonObject vanillaJson(String mcVersion, Progress progress, boolean online)
            throws IOException, InterruptedException {
        Path file = versionsDir.resolve(mcVersion).resolve(mcVersion + ".json");
        if (!online) return local(file, "Minecraft " + mcVersion);
        try {
            JsonObject manifest = Http.getJson(MOJANG_MANIFEST);
            for (JsonElement e : manifest.getAsJsonArray("versions")) {
                JsonObject v = e.getAsJsonObject();
                if (v.get("id").getAsString().equals(mcVersion)) {
                    Downloader.downloadAll(progress.step(), List.of(new DownloadTask(
                            v.get("url").getAsString(), file, v.get("sha1").getAsString(), 0, true)), progress);
                    return Json.readObject(file);
                }
            }
            throw new IOException("La version " + mcVersion + " n'existe pas chez Mojang.");
        } catch (IOException e) {
            if (!Files.isRegularFile(file)) throw e;
            progress.setOffline(true); // Mojang injoignable : on garde la copie locale
            return Json.readObject(file);
        }
    }

    /** Dernière version stable de Fabric ; hors ligne ou injoignable, la plus récente déjà installée. */
    private String fabricLoader(String mcVersion, boolean online) throws IOException, InterruptedException {
        if (online) {
            try {
                return latestFabricLoader(mcVersion);
            } catch (IOException e) {
                if (installedLoader(versionsDir, mcVersion).isEmpty()) throw e;
            }
        }
        return installedLoader(versionsDir, mcVersion).orElseThrow(() -> new Downloader.OfflineException(
                "Fabric n'est pas encore installé pour Minecraft " + mcVersion + " : une connexion est nécessaire."));
    }

    /** Version de Fabric Loader la plus récente déjà installée pour cette version de Minecraft. */
    static Optional<String> installedLoader(Path versionsDir, String mcVersion) {
        String prefix = "fabric-loader-";
        String suffix = "-" + mcVersion;
        if (!Files.isDirectory(versionsDir)) return Optional.empty();
        try (Stream<Path> dirs = Files.list(versionsDir)) {
            return dirs.map(d -> d.getFileName().toString())
                    .filter(n -> n.startsWith(prefix) && n.endsWith(suffix))
                    .filter(n -> Files.isRegularFile(versionsDir.resolve(n).resolve(n + ".json")))
                    .map(n -> n.substring(prefix.length(), n.length() - suffix.length()))
                    .max(VersionResolver::compareVersions);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Compare « 0.17.3 » et « 0.9.10 » numériquement, composante par composante. */
    static int compareVersions(String a, String b) {
        String[] pa = a.split("[.+-]");
        String[] pb = b.split("[.+-]");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            String x = i < pa.length ? pa[i] : "0";
            String y = i < pb.length ? pb[i] : "0";
            int c = x.matches("\\d+") && y.matches("\\d+")
                    ? Long.compare(Long.parseLong(x), Long.parseLong(y)) : x.compareTo(y);
            if (c != 0) return c;
        }
        return 0;
    }

    private static JsonObject local(Path file, String what) throws IOException {
        if (Files.isRegularFile(file)) return Json.readObject(file);
        throw new Downloader.OfflineException(what + " n'est pas encore installé : une connexion est nécessaire.");
    }

    private String latestFabricLoader(String mcVersion) throws IOException, InterruptedException {
        JsonArray loaders = Http.get(FABRIC_META + mcVersion).getAsJsonArray();
        for (JsonElement e : loaders) {
            JsonObject loader = e.getAsJsonObject().getAsJsonObject("loader");
            if (loader.get("stable").getAsBoolean()) return loader.get("version").getAsString();
        }
        if (!loaders.isEmpty()) {
            return loaders.get(0).getAsJsonObject().getAsJsonObject("loader").get("version").getAsString();
        }
        throw new IOException("Fabric ne supporte pas encore Minecraft " + mcVersion + ".");
    }

    private JsonObject fabricJson(String mcVersion, String loader, boolean online)
            throws IOException, InterruptedException {
        String id = "fabric-loader-" + loader + "-" + mcVersion;
        Path file = versionsDir.resolve(id).resolve(id + ".json");
        if (!online) return local(file, "Fabric " + loader);
        try {
            JsonObject profile = Http.getJson(FABRIC_META + mcVersion + "/" + loader + "/profile/json");
            Json.write(file, profile);
            return profile;
        } catch (IOException e) {
            if (Files.isRegularFile(file)) return Json.readObject(file);
            throw e;
        }
    }

    private GameProfile.Library mavenLibrary(JsonObject lib) {
        String name = lib.get("name").getAsString();
        String path = mavenPath(name);
        String repo = Json.string(lib, "url", "https://maven.fabricmc.net/");
        if (!repo.endsWith("/")) repo += "/";
        return new GameProfile.Library(name, librariesDir.resolve(path), repo + path,
                Json.string(lib, "sha1", null), Json.number(lib, "size", 0));
    }

    /** « groupe:artefact:version[:classifier] » → chemin Maven du .jar. */
    static String mavenPath(String name) {
        String[] parts = name.split(":");
        String file = parts[1] + "-" + parts[2] + (parts.length > 3 ? "-" + parts[3] : "") + ".jar";
        return parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + file;
    }

    /** Identifie une bibliothèque sans sa version, pour détecter les doublons. */
    private static String key(String name) {
        String[] parts = name.split(":");
        return parts[0] + ":" + parts[1] + (parts.length > 3 ? ":" + parts[3] : "");
    }

    private static void collectArguments(JsonObject version, List<JsonElement> jvm, List<JsonElement> game) {
        JsonObject arguments = version.getAsJsonObject("arguments");
        if (arguments != null) {
            if (arguments.has("jvm")) arguments.getAsJsonArray("jvm").forEach(jvm::add);
            if (arguments.has("game")) arguments.getAsJsonArray("game").forEach(game::add);
        } else if (version.has("minecraftArguments")) {
            // Anciennes versions : une simple chaîne
            for (String arg : version.get("minecraftArguments").getAsString().split(" ")) {
                game.add(new com.google.gson.JsonPrimitive(arg));
            }
        }
    }
}
