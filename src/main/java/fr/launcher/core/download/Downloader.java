package fr.launcher.core.download;

import fr.launcher.util.Hash;
import fr.launcher.util.Http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
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
 *
 * <p>Annulation : interrompre le thread appelant arrête tous les transferts, supprime les fichiers partiels
 * et lève {@link InterruptedException}. Rien n'est jamais laissé à moitié écrit.</p>
 */
public final class Downloader {

    private static final int PARALLEL = 24;
    private static final int RETRIES = 3;

    private Downloader() {
    }

    public static void downloadAll(String step, List<DownloadTask> tasks, Progress progress)
            throws IOException, InterruptedException {
        downloadAll(step, tasks, progress, true);
    }

    /**
     * @param online faux = hors ligne : on vérifie seulement ; un fichier absent ou abîmé est une erreur claire
     */
    public static void downloadAll(String step, List<DownloadTask> tasks, Progress progress, boolean online)
            throws IOException, InterruptedException {
        long total = tasks.stream().mapToLong(DownloadTask::size).sum();
        progress.begin(step, total, tasks.size());
        if (!online) {
            verifyOffline(tasks, progress);
            return;
        }
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
            try {
                for (Future<?> future : futures) {
                    try {
                        future.get();
                    } catch (ExecutionException e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof InterruptedException || cause instanceof InterruptedIOException) {
                            throw new InterruptedException("Téléchargement annulé");
                        }
                        if (cause instanceof IOException io) throw io;
                        throw new IOException(cause);
                    }
                }
            } catch (IOException | InterruptedException e) {
                // Annulation ou échec : on interrompt tous les transferts. Surtout pas Future.cancel() : la tâche
                // serait considérée finie aussitôt et close() n'attendrait pas son nettoyage (fichier .part).
                // Avec shutdownNow(), close() attend que chaque transfert soit réellement arrêté.
                executor.shutdownNow();
                throw e;
            }
        }
    }

    private static void verifyOffline(List<DownloadTask> tasks, Progress progress) throws IOException {
        List<String> missing = new ArrayList<>();
        for (DownloadTask task : tasks) {
            if (isValid(task)) progress.addBytes(task.size());
            else missing.add(task.target().getFileName().toString());
            progress.fileDone();
        }
        if (!missing.isEmpty()) {
            throw new OfflineException(missing.size() + " fichier(s) manquant(s) ou abîmé(s), par exemple "
                    + missing.getFirst() + " : une connexion Internet est nécessaire pour les télécharger.");
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
            } catch (InterruptedIOException e) {
                throw e; // annulation : surtout pas de nouvel essai
            } catch (IOException e) {
                last = e;
                if (attempt < RETRIES) Thread.sleep(400L * attempt);
            }
        }
        throw new IOException("Échec du téléchargement de " + task.target().getFileName() + " : "
                + last.getMessage(), last);
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
        boolean completed = false;
        try {
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedIOException("Téléchargement annulé");
                    }
                    out.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    written += read;
                    progress.addNetworkBytes(read);
                    if (task.size() > 0) progress.addBytes(read);
                }
            } catch (IOException e) {
                // Progression remise à son état d'avant ce transfert.
                if (task.size() > 0) progress.addBytes(-written);
                if (e instanceof InterruptedIOException || Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Téléchargement annulé");
                }
                throw e;
            }

            if (task.size() <= 0) {
                progress.addUnknownSize(written);
                progress.addBytes(written);
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (task.sha1() != null && !task.sha1().equalsIgnoreCase(actual)) {
                if (task.size() > 0) progress.addBytes(-written);
                throw new IOException("Fichier corrompu : " + target.getFileName());
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            completed = true;
        } finally {
            // Quelle que soit la sortie (erreur, annulation, exception inattendue), aucun fichier partiel ne reste.
            if (!completed) Files.deleteIfExists(tmp);
        }
    }

    /** Hors ligne, des fichiers manquent : l'interface peut proposer de réessayer une fois connecté. */
    public static final class OfflineException extends IOException {
        public OfflineException(String message) {
            super(message);
        }
    }
}
