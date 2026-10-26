package fr.launcher.core.download;

import com.sun.net.httpserver.HttpServer;
import fr.launcher.util.Hash;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Téléchargements réels contre un petit serveur HTTP local (aucun accès à Internet). */
class DownloaderNetworkTest {

    private static final byte[] CONTENT = "contenu du fichier de test".repeat(200).getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dir;
    private HttpServer server;
    private String base;
    private final CountDownLatch slowStarted = new CountDownLatch(1);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ok", ex -> {
            ex.sendResponseHeaders(200, CONTENT.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(CONTENT);
            }
        });
        server.createContext("/corrompu", ex -> {
            byte[] bad = CONTENT.clone();
            bad[0] ^= 1;
            ex.sendResponseHeaders(200, bad.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bad);
            }
        });
        // Envoie quelques octets puis traîne : de quoi annuler en plein transfert.
        server.createContext("/lent", ex -> {
            ex.sendResponseHeaders(200, 10_000_000);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(new byte[64 * 1024]);
                out.flush();
                slowStarted.countDown();
                for (int i = 0; i < 100; i++) {
                    Thread.sleep(100);
                    out.write(new byte[1024]);
                    out.flush();
                }
            } catch (InterruptedException | IOException ignored) {
                // client parti : normal après une annulation
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static String sha1(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
    }

    @Test
    void telechargeEtVerifie() throws Exception {
        Path target = dir.resolve("a/fichier.bin");
        Progress progress = new Progress();
        Downloader.downloadAll("test", List.of(new DownloadTask(base + "/ok", target, sha1(CONTENT), CONTENT.length,
                true)), progress);
        assertEquals(sha1(CONTENT), Hash.sha1(target));
        assertEquals(CONTENT.length, progress.totalNetworkBytes());
        assertEquals(1.0, progress.fraction());
    }

    @Test
    void fichierCorrompuRefuseSansResidu() throws Exception {
        Path target = dir.resolve("fichier.bin");
        String expected = sha1(CONTENT);
        IOException e = assertThrows(IOException.class, () -> Downloader.downloadAll("test",
                List.of(new DownloadTask(base + "/corrompu", target, expected, CONTENT.length, true)),
                new Progress()));
        assertTrue(e.getMessage().contains("corrompu"), e.getMessage());
        assertFalse(Files.exists(target));
        assertFalse(Files.exists(dir.resolve("fichier.bin.part")), "aucun fichier partiel ne reste");
    }

    @Test
    void annulationEnPleinTransfert() throws Exception {
        Path target = dir.resolve("gros.bin");
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        Thread worker = Thread.ofPlatform().start(() -> {
            try {
                Downloader.downloadAll("test",
                        List.of(new DownloadTask(base + "/lent", target, null, 10_000_000, true)), new Progress());
            } catch (Throwable t) {
                outcome.set(t);
            }
        });
        assertTrue(slowStarted.await(10, TimeUnit.SECONDS), "le transfert a commencé");
        Thread.sleep(200);
        long before = System.nanoTime();
        worker.interrupt();
        worker.join(10_000);
        assertFalse(worker.isAlive(), "l'annulation rend la main");
        assertTrue((System.nanoTime() - before) / 1_000_000 < 5_000, "et rapidement");
        assertInstanceOf(InterruptedException.class, outcome.get());
        assertFalse(Files.exists(target));
        assertFalse(Files.exists(dir.resolve("gros.bin.part")), "le fichier partiel est supprimé avant le retour");
    }

    @Test
    void horsLigneUnFichierManquantEstSignale() {
        Path target = dir.resolve("absent.bin");
        Downloader.OfflineException e = assertThrows(Downloader.OfflineException.class, () -> Downloader.downloadAll(
                "test", List.of(new DownloadTask(base + "/ok", target, null, CONTENT.length, true)), new Progress(),
                false));
        assertTrue(e.getMessage().contains("connexion"));
        assertFalse(Files.exists(target), "rien n'est téléchargé hors ligne");
    }
}
