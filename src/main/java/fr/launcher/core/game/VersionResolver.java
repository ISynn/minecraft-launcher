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

    GameProfile resolve(String mcVersion, String loaderVersion, Progress progress)
            throws IOException, InterruptedException {
        JsonObject vanilla = vanillaJson(mcVersion, progress);
        String loader = loaderVersion == null || loaderVersion.isBlank() ? latestFabricLoader(mcVersion) : loaderVersion;
        JsonObject fabric = fabricJson(mcVersion, loader);

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

    private JsonObject vanillaJson(String mcVersion, Progress progress) throws IOException, InterruptedException {
        Path file = versionsDir.resolve(mcVersion).resolve(mcVersion + ".json");
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
            if (Files.isRegularFile(file)) return Json.readObject(file); // hors-ligne : on garde la copie locale
            throw e;
        }
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

    private JsonObject fabricJson(String mcVersion, String loader) throws IOException, InterruptedException {
        String id = "fabric-loader-" + loader + "-" + mcVersion;
        Path file = versionsDir.resolve(id).resolve(id + ".json");
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
