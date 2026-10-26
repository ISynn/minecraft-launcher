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
    private static final int STAGES = 5;

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
        progress.stage(1, STAGES, "Versions et modpack");
        Optional<ModpackManifest> manifest = modpack.fetch();
        String mcVersion = manifest.map(ModpackManifest::minecraft).filter(s -> !s.isBlank())
                .orElse(LauncherConfig.MINECRAFT_VERSION);
        String loader = manifest.map(ModpackManifest::fabricLoader).filter(s -> !s.isBlank())
                .orElse(LauncherConfig.FABRIC_LOADER);
        GameProfile profile = resolver.resolve(mcVersion, loader, progress);

        progress.stage(2, STAGES, "Java");
        Path java = runtime.install(profile.vanilla(), progress);

        progress.stage(3, STAGES, "Minecraft et bibliothèques");
        List<DownloadTask> game = new ArrayList<>();
        JsonObject client = profile.vanilla().getAsJsonObject("downloads").getAsJsonObject("client");
        game.add(new DownloadTask(client.get("url").getAsString(), clientJar(profile),
                client.get("sha1").getAsString(), client.get("size").getAsLong(), true));
        profile.libraries().forEach(lib -> game.add(lib.task()));
        Downloader.downloadAll(progress.step(), game, progress);

        progress.stage(4, STAGES, "Ressources du jeu");
        Downloader.downloadAll(progress.step(), assetTasks(profile.vanilla(), progress, deepVerify), progress);

        progress.stage(5, STAGES, "Mods du serveur");
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
