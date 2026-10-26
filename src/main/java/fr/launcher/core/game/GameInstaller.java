package fr.launcher.core.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.launcher.LauncherConfig;
import fr.launcher.core.download.DownloadTask;
import fr.launcher.core.download.Downloader;
import fr.launcher.core.download.Progress;
import fr.launcher.core.modpack.ModpackManifest;
import fr.launcher.core.modpack.ModpackSync;
import fr.launcher.util.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Installe ou met à jour tout ce qu'il faut pour jouer, en 5 étapes.
 * Leurs noms sont neutres : c'est l'interface qui dit si l'étape vérifie ou télécharge.
 */
public final class GameInstaller {

    private static final String RESOURCES = "https://resources.download.minecraft.net/";
    /** Les étapes, dans l'ordre : l'écran Installation les affiche telles quelles. */
    public static final List<String> STAGE_NAMES = List.of(
            "Versions et modpack", "Java", "Minecraft et bibliothèques", "Ressources du jeu", "Mods du serveur");
    private static final int STAGES = STAGE_NAMES.size();

    private final Path gameDir;
    private final VersionResolver resolver;
    private final JavaRuntime runtime;
    private final ModpackSync modpack;

    public record Installation(GameProfile profile, Path java) {
    }

    public GameInstaller(Path gameDir) {
        this.gameDir = gameDir;
        this.resolver = new VersionResolver(gameDir);
        this.runtime = new JavaRuntime(gameDir);
        this.modpack = new ModpackSync(gameDir);
    }

    /** @param deepVerify recalcule le SHA-1 de chaque fichier (bouton « Vérifier les fichiers »). */
    public Installation install(Progress progress, boolean deepVerify) throws IOException, InterruptedException {
        progress.stage(1, STAGES, STAGE_NAMES.get(0));
        Optional<ModpackManifest> manifest = modpack.fetch();
        String mcVersion = manifest.map(ModpackManifest::minecraft).filter(s -> !s.isBlank())
                .orElse(LauncherConfig.MINECRAFT_VERSION);
        String loader = manifest.map(ModpackManifest::fabricLoader).filter(s -> !s.isBlank())
                .orElse(LauncherConfig.FABRIC_LOADER);
        GameProfile profile = resolver.resolve(mcVersion, loader, progress);

        progress.stage(2, STAGES, STAGE_NAMES.get(1));
        Path java = runtime.install(profile.vanilla(), progress);

        progress.stage(3, STAGES, STAGE_NAMES.get(2));
        List<DownloadTask> game = new ArrayList<>();
        JsonObject client = profile.vanilla().getAsJsonObject("downloads").getAsJsonObject("client");
        game.add(new DownloadTask(client.get("url").getAsString(), clientJar(profile),
                client.get("sha1").getAsString(), client.get("size").getAsLong(), true));
        profile.libraries().forEach(lib -> game.add(lib.task()));
        Downloader.downloadAll(progress.step(), game, progress);

        progress.stage(4, STAGES, STAGE_NAMES.get(3));
        Downloader.downloadAll(progress.step(), assetTasks(profile.vanilla(), progress, deepVerify), progress);

        progress.stage(5, STAGES, STAGE_NAMES.get(4));
        if (manifest.isPresent()) modpack.sync(manifest.get(), progress);

        return new Installation(profile, java);
    }

    private List<DownloadTask> assetTasks(JsonObject vanilla, Progress progress, boolean strict)
            throws IOException, InterruptedException {
        JsonObject index = vanilla.getAsJsonObject("assetIndex");
        Path assets = assetsDir();
        Path indexFile = assets.resolve("indexes").resolve(index.get("id").getAsString() + ".json");
        Downloader.downloadAll(progress.step(), List.of(new DownloadTask(index.get("url").getAsString(), indexFile,
                index.get("sha1").getAsString(), index.get("size").getAsLong(), true)), progress);

        // Plusieurs noms peuvent pointer vers le même fichier : on dédoublonne par hash.
        Map<String, DownloadTask> tasks = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : Json.readObject(indexFile).getAsJsonObject("objects").entrySet()) {
            JsonObject object = entry.getValue().getAsJsonObject();
            String hash = object.get("hash").getAsString();
            String sub = hash.substring(0, 2) + "/" + hash;
            tasks.computeIfAbsent(hash, h -> new DownloadTask(RESOURCES + sub,
                    assets.resolve("objects").resolve(sub), h, object.get("size").getAsLong(), strict));
        }
        return new ArrayList<>(tasks.values());
    }

    /**
     * Vrai si un Minecraft a déjà été installé ici (au moins une fiche de version), même incomplet :
     * un téléchargement est alors une mise à jour ou une réparation, pas une première installation.
     */
    public boolean hasInstallation() {
        Path versions = gameDir.resolve("versions");
        if (!Files.isDirectory(versions)) return false;
        try (Stream<Path> dirs = Files.list(versions)) {
            return dirs.anyMatch(dir -> Files.isRegularFile(dir.resolve(dir.getFileName() + ".json")));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Espace occupé par chaque partie de l'installation, en octets (dossiers absents : 0).
     * Parcourt des milliers de fichiers : à appeler hors du thread graphique.
     */
    public Map<String, Long> diskUsage() {
        Map<String, Long> usage = new LinkedHashMap<>();
        usage.put("Minecraft", size(gameDir.resolve("versions")));
        usage.put("Bibliothèques", size(gameDir.resolve("libraries")));
        usage.put("Ressources", size(gameDir.resolve("assets")));
        usage.put("Java", size(gameDir.resolve("runtime")));
        usage.put("Mods", size(gameDir.resolve("mods")));
        return usage;
    }

    /** Runtimes Java de Mojang installés (par ex. « java-runtime-epsilon »). */
    public List<String> installedRuntimes() {
        Path dir = gameDir.resolve("runtime");
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> dirs = Files.list(dir)) {
            return dirs.filter(Files::isDirectory).map(d -> d.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static long size(Path dir) {
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).mapToLong(f -> {
                try {
                    return Files.size(f);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (IOException | UncheckedIOException e) {
            return 0;
        }
    }

    Path clientJar(GameProfile profile) {
        String v = profile.minecraftVersion();
        return gameDir.resolve("versions").resolve(v).resolve(v + ".jar");
    }

    Path assetsDir() {
        return gameDir.resolve("assets");
    }

    Path librariesDir() {
        return gameDir.resolve("libraries");
    }

    Path gameDir() {
        return gameDir;
    }
}
