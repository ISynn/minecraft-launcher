package fr.launcher.core.download;

import fr.launcher.util.Hash;
import fr.launcher.util.Http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Télécharge des milliers de fichiers en parallèle avec des threads virtuels.
 * Un sémaphore limite le nombre de connexions simultanées pour ne pas saturer le réseau.
 */
public final class Downloader {

    private static final int PARALLEL = 24;
    private static final int RETRIES = 3;

    private Downloader() {
    }

    public static void downloadAll(String step, List<DownloadTask> tasks, Progress progress)
            throws IOException, InterruptedException {
        long total = tasks.stream().mapToLong(DownloadTask::size).sum();
        progress.begin(step, total, tasks.size());
        Semaphore permits = new Semaphore(PARALLEL);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(tasks.size());
            for (DownloadTask task : tasks) {
                futures.add(executor.submit(() -> {
                    if (isValid(task)) {
                        progress.addBytes(task.size());
                    } else {
                        permits.acquire();
                        try {
                            download(task, progress);
                        } finally {
                            permits.release();
                        }
                    }
                    progress.fileDone();
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    futures.forEach(f -> f.cancel(true));
                    if (e.getCause() instanceof IOException io) throw io;
                    throw new IOException(e.getCause());
                }
            }
        }
    }

    /** Le fichier est-il déjà présent et correct ? */
    static boolean isValid(DownloadTask task) throws IOException {
        Path file = task.target();
        if (!Files.isRegularFile(file)) return false;
        if (task.size() > 0 && Files.size(file) != task.size()) return false;
        if (task.strict() && task.sha1() != null) return task.sha1().equalsIgnoreCase(Hash.sha1(file));
        return true;
    }

    private static void download(DownloadTask task, Progress progress) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= RETRIES; attempt++) {
            try {
                transfer(task, progress);
                return;
            } catch (IOException e) {
                last = e;
                if (e instanceof CountedIOException counted) progress.addBytes(-counted.counted);
                if (attempt < RETRIES) Thread.sleep(400L * attempt);
            }
        }
        throw new IOException("Échec du téléchargement de " + task.target().getFileName() + " : " + last.getMessage(), last);
    }

    /** Écrit dans un fichier temporaire, vérifie le hash, puis remplace : jamais de fichier à moitié écrit. */
    private static void transfer(DownloadTask task, Progress progress) throws IOException, InterruptedException {
        Path target = task.target();
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");

        HttpRequest request = Http.request(task.url()).timeout(Duration.ofMinutes(5)).GET().build();
        HttpResponse<InputStream> response = Http.CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("HTTP " + response.statusCode() + " pour " + task.url());
        }

        MessageDigest digest = Hash.sha1();
        long written = 0;
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                written += read;
                progress.addNetworkBytes(read);
                if (task.size() > 0) progress.addBytes(read);
            }
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw new CountedIOException(e, task.size() > 0 ? written : 0);
        }

        if (task.size() <= 0) {
            progress.addUnknownSize(written);
            progress.addBytes(written);
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (task.sha1() != null && !task.sha1().equalsIgnoreCase(actual)) {
            Files.deleteIfExists(tmp);
            if (task.size() > 0) progress.addBytes(-written);
            throw new IOException("Fichier corrompu : " + target.getFileName());
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Permet d'annuler la progression comptée quand un téléchargement échoue en cours de route. */
    private static final class CountedIOException extends IOException {
        CountedIOException(IOException cause, long counted) {
            super(cause.getMessage(), cause);
            this.counted = counted;
        }

        final long counted;
    }
}
