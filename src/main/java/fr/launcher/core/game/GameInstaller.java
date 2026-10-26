package fr.launcher.core.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.launcher.LauncherConfig;
import fr.launcher.core.download.DownloadTask;
import fr.launcher.core.download.Downloader;
import fr.launcher.core.download.Progress;
import fr.launcher.core.log.Log;
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
import java.util.logging.Logger;
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

    private static final Logger LOG = Log.get(GameInstaller.class);

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

    /**
     * @param deepVerify   recalcule le SHA-1 de chaque fichier (bouton « Vérifier et réparer »)
     * @param checkUpdates faux = mode hors ligne choisi par le joueur : rien n'est demandé au réseau
     */
    public Installation install(Progress progress, boolean deepVerify, boolean checkUpdates)
            throws IOException, InterruptedException {
        progress.stage(1, STAGES, STAGE_NAMES.get(0));
        boolean online = checkUpdates;
        Resolved resolved;
        try {
            resolved = resolve(progress, online);
        } catch (Downloader.OfflineException e) {
            throw e;
        } catch (IOException e) {
            // Réseau en panne mais jeu déjà installé : on joue avec ce qu'on a plutôt que d'échouer.
            if (!online || !hasInstallation()) throw e;
            LOG.warning("Serveurs injoignables (" + e.getMessage() + ") : lancement hors ligne avec les fichiers "
                    + "installés");
            online = false;
            resolved = resolve(progress, false);
        }
        if (!online) progress.setOffline(true);
        GameProfile profile = resolved.profile();

        progress.stage(2, STAGES, STAGE_NAMES.get(1));
        Path java = runtime.install(profile.vanilla(), progress, online);

        progress.stage(3, STAGES, STAGE_NAMES.get(2));
        List<DownloadTask> game = new ArrayList<>();
        JsonObject client = profile.vanilla().getAsJsonObject("downloads").getAsJsonObject("client");
        game.add(new DownloadTask(client.get("url").getAsString(), clientJar(profile),
                client.get("sha1").getAsString(), client.get("size").getAsLong(), true));
        profile.libraries().forEach(lib -> game.add(lib.task()));
        Downloader.downloadAll(progress.step(), game, progress, online);

        progress.stage(4, STAGES, STAGE_NAMES.get(3));
        Downloader.downloadAll(progress.step(), assetTasks(profile.vanilla(), progress, deepVerify, online), progress,
                online);

        progress.stage(5, STAGES, STAGE_NAMES.get(4));
        if (resolved.manifest().isPresent()) modpack.sync(resolved.manifest().get(), progress, online);

        markInstalled(profile);
        return new Installation(profile, java);
    }

    private record Resolved(Optional<ModpackManifest> manifest, GameProfile profile) {
    }

    private Resolved resolve(Progress progress, boolean online) throws IOException, InterruptedException {
        Optional<ModpackManifest> manifest = modpack.fetch(online);
        String mcVersion = manifest.map(ModpackManifest::minecraft).filter(s -> !s.isBlank())
                .orElse(LauncherConfig.MINECRAFT_VERSION);
        String loader = manifest.map(ModpackManifest::fabricLoader).filter(s -> !s.isBlank())
                .orElse(LauncherConfig.FABRIC_LOADER);
        return new Resolved(manifest, resolver.resolve(mcVersion, loader, progress, online));
    }

    private List<DownloadTask> assetTasks(JsonObject vanilla, Progress progress, boolean strict, boolean online)
            throws IOException, InterruptedException {
        JsonObject index = vanilla.getAsJsonObject("assetIndex");
        Path assets = assetsDir();
        Path indexFile = assets.resolve("indexes").resolve(index.get("id").getAsString() + ".json");
        Downloader.downloadAll(progress.step(), List.of(new DownloadTask(index.get("url").getAsString(), indexFile,
                index.get("sha1").getAsString(), index.get("size").getAsLong(), true)), progress, online);

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

    /** Témoin écrit à la fin d'une installation complète : une installation interrompue n'en a pas. */
    private static final String INSTALLED_MARKER = ".installation-complete";

    /**
     * Vrai si une installation complète a déjà réussi ici : un téléchargement est alors une mise à jour ou une
     * réparation. Une première installation interrompue reste une première installation.
     */
    public boolean hasInstallation() {
        return Files.isRegularFile(gameDir.resolve(INSTALLED_MARKER));
    }

    private void markInstalled(GameProfile profile) {
        try {
            Files.writeString(gameDir.resolve(INSTALLED_MARKER), profile.displayName());
        } catch (IOException e) {
            LOG.warning("Témoin d'installation non écrit : " + e.getMessage());
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
