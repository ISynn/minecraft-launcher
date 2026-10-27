// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameWindowWatcherTest {

    @TempDir
    Path dir;

    /** Processus factice : seul son état « vivant » compte pour le guetteur. */
    private static final class FakeProcess extends Process {
        volatile boolean alive = true;

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            alive = false;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }

    @Test
    void detecteLaFenetreQuandLeMarqueurArrive() throws Exception {
        Path log = dir.resolve("game-output.log");
        Files.writeString(log, "[main/INFO]: Loading Minecraft 26.3 with Fabric Loader 0.20.1\n");
        FakeProcess game = new FakeProcess();
        Thread writer = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(400);
                Files.writeString(log, "[Render thread/INFO]: Backend library: LWJGL version 3.4.3\n",
                        StandardOpenOption.APPEND);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(GameWindowWatcher.waitForWindow(log, game, Duration.ofSeconds(5)));
        writer.join();
    }

    @Test
    void abandonneSiLeJeuSeFermeAvant() throws Exception {
        Path log = dir.resolve("game-output.log");
        Files.writeString(log, "[main/ERROR]: crash\n");
        FakeProcess game = new FakeProcess();
        game.alive = false;
        assertFalse(GameWindowWatcher.waitForWindow(log, game, Duration.ofSeconds(5)));
    }

    @Test
    void abandonneApresLeDelai() throws Exception {
        assertFalse(GameWindowWatcher.waitForWindow(dir.resolve("absent.log"), new FakeProcess(), Duration.ofMillis(600)));
    }
}
