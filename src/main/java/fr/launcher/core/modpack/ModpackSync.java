package fr.launcher.core.modpack;

import fr.launcher.LauncherConfig;
import fr.launcher.core.download.DownloadTask;
import fr.launcher.core.download.Downloader;
import fr.launcher.core.download.Progress;
import fr.launcher.util.Http;
import fr.launcher.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Télécharge les mods du serveur et supprime ceux qui n'en font pas partie. */
public final class ModpackSync {

    private final Path gameDir;

    public ModpackSync(Path gameDir) {
        this.gameDir = gameDir.toAbsolutePath().normalize();
    }

    public Optional<ModpackManifest> fetch() throws IOException, InterruptedException {
        if (LauncherConfig.MODPACK_URL.isBlank()) return Optional.empty();
        return Optional.of(Json.GSON.fromJson(Http.getJson(LauncherConfig.MODPACK_URL), ModpackManifest.class));
    }

    public void sync(ModpackManifest manifest, Progress progress) throws IOException, InterruptedException {
        List<DownloadTask> tasks = new ArrayList<>();
        Set<Path> expected = new HashSet<>();
        for (ModpackManifest.FileEntry entry : nullSafe(manifest.files())) {
            Path target = safeResolve(entry.path());
            expected.add(target);
            tasks.add(new DownloadTask(entry.url(), target, entry.sha1(), entry.size(), true));
        }
        Downloader.downloadAll(progress.step(), tasks, progress);

        for (String folder : nullSafe(manifest.managedFolders())) {
            Path dir = safeResolve(folder);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    if (!expected.contains(file.toAbsolutePath().normalize())) Files.delete(file);
                }
            }
        }
    }

    /** Le manifest vient d'Internet : on refuse tout chemin qui sortirait du dossier du jeu. */
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
