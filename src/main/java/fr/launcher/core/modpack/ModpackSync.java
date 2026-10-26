package fr.launcher.core.modpack;

import fr.launcher.LauncherConfig;
import fr.launcher.core.download.DownloadTask;
import fr.launcher.core.download.Downloader;
import fr.launcher.core.download.Progress;
import fr.launcher.core.log.Log;
import fr.launcher.util.Http;
import fr.launcher.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Télécharge les mods du serveur et supprime ceux qui n'en font pas partie.
 *
 * <p>Le manifeste vient d'Internet : il est bridé. Il ne peut « gérer » (vider des fichiers en trop) que les
 * dossiers de contenu, ne peut jamais écrire dans les mondes, captures, journaux, options ou données du launcher,
 * et chaque fichier doit être accompagné de son empreinte SHA-1.</p>
 */
public final class ModpackSync {

    private static final Logger LOG = Log.get(ModpackSync.class);

    /** Seuls dossiers dont le serveur peut retirer les fichiers en trop. */
    static final Set<String> MANAGEABLE = Set.of("mods", "resourcepacks", "shaderpacks");
    /** Données du joueur et du launcher : le modpack n'y écrit jamais. */
    static final List<String> PROTECTED = List.of(
            "saves", "screenshots", "logs", "crash-reports", "launcher", "runtime", "options.txt", "servers.dat");

    private final Path gameDir;
    private final Path cachedManifest;

    public ModpackSync(Path gameDir) {
        this.gameDir = gameDir.toAbsolutePath().normalize();
        this.cachedManifest = this.gameDir.resolve(".modpack.json");
    }

    /**
     * Manifeste du serveur. En ligne, il est téléchargé et mis en cache ; hors ligne (ou si le serveur ne répond pas),
     * la dernière copie est utilisée.
     */
    public Optional<ModpackManifest> fetch(boolean online) throws IOException, InterruptedException {
        if (LauncherConfig.MODPACK_URL.isBlank()) return Optional.empty();
        if (online) {
            try {
                ModpackManifest manifest = Json.GSON.fromJson(Http.getJson(LauncherConfig.MODPACK_URL), ModpackManifest.class);
                Json.write(cachedManifest, manifest);
                return Optional.of(manifest);
            } catch (IOException e) {
                if (!Files.isRegularFile(cachedManifest)) throw e;
                LOG.warning("Modpack injoignable (" + e.getMessage() + "), dernière copie utilisée");
            }
        }
        if (!Files.isRegularFile(cachedManifest)) {
            throw new IOException("Liste des mods du serveur jamais téléchargée : une connexion est nécessaire.");
        }
        return Optional.of(Json.read(cachedManifest, ModpackManifest.class));
    }

    public void sync(ModpackManifest manifest, Progress progress, boolean online) throws IOException, InterruptedException {
        List<DownloadTask> tasks = new ArrayList<>();
        Set<Path> expected = new HashSet<>();
        for (ModpackManifest.FileEntry entry : nullSafe(manifest.files())) {
            Path target = writableTarget(entry.path());
            if (entry.sha1() == null || !entry.sha1().matches("[0-9a-fA-F]{40}")) {
                throw new IOException("Empreinte SHA-1 manquante ou invalide pour " + entry.path()
                        + " : fichier refusé.");
            }
            expected.add(target);
            tasks.add(new DownloadTask(entry.url(), target, entry.sha1(), entry.size(), true));
        }
        Downloader.downloadAll(progress.step(), tasks, progress, online);

        for (String folder : nullSafe(manifest.managedFolders())) {
            Path dir = managedFolder(folder);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    if (!expected.contains(file.toAbsolutePath().normalize())) {
                        LOG.info("Fichier en trop retiré : " + gameDir.relativize(file));
                        Files.delete(file);
                    }
                }
            }
        }
    }

    /** Un dossier « géré » doit être exactement l'un des dossiers de contenu autorisés. */
    Path managedFolder(String folder) throws IOException {
        Path dir = safeResolve(folder);
        String name = gameDir.relativize(dir).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (!MANAGEABLE.contains(name)) {
            throw new IOException("Le modpack ne peut pas gérer le dossier « " + folder + " » (autorisés : "
                    + String.join(", ", MANAGEABLE) + ").");
        }
        return dir;
    }

    /** Un fichier du modpack ne peut pas atterrir dans les données du joueur ou du launcher. */
    Path writableTarget(String relative) throws IOException {
        Path target = safeResolve(relative);
        String first = gameDir.relativize(target).getName(0).toString().toLowerCase(Locale.ROOT);
        if (PROTECTED.contains(first) || first.startsWith(".")) {
            throw new IOException("Le modpack ne peut pas écrire « " + relative + " » : données protégées du joueur.");
        }
        return target;
    }

    /** Le manifeste vient d'Internet : on refuse tout chemin qui sortirait du dossier du jeu. */
    Path safeResolve(String relative) throws IOException {
        Path path = gameDir.resolve(relative).normalize();
        if (!path.startsWith(gameDir) || path.equals(gameDir)) {
            throw new IOException("Chemin interdit dans le modpack : " + relative);
        }
        return path;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
