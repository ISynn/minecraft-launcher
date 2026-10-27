// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.game;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Détecte l'ouverture de la fenêtre de Minecraft en lisant sa sortie console au fil de l'eau.
 *
 * <p>Minecraft écrit « Backend library: LWJGL … » au moment où il crée sa fenêtre (toutes versions
 * récentes, avec ou sans Fabric). Jusque-là, le jeu charge : le launcher affiche « Lancement ».</p>
 */
public final class GameWindowWatcher {

    static final String WINDOW_MARKER = "Backend library:";
    private static final long POLL_MS = 250;

    private GameWindowWatcher() {
    }

    /**
     * Attend la fenêtre du jeu.
     *
     * @return true si la fenêtre est apparue ; false si le jeu s'est fermé avant ou si le délai est dépassé
     */
    public static boolean waitForWindow(Path output, Process process, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        long position = 0;
        StringBuilder pending = new StringBuilder();
        while (System.nanoTime() < deadline && process.isAlive()) {
            try {
                if (Files.isRegularFile(output)) {
                    try (RandomAccessFile file = new RandomAccessFile(output.toFile(), "r")) {
                        long length = file.length();
                        if (length < position) position = 0; // fichier recréé
                        if (length > position) {
                            byte[] chunk = new byte[(int) Math.min(length - position, 1 << 20)];
                            file.seek(position);
                            file.readFully(chunk);
                            position += chunk.length;
                            pending.append(new String(chunk, StandardCharsets.UTF_8));
                            if (pending.indexOf(WINDOW_MARKER) >= 0) return true;
                            // On ne garde que la fin, au cas où le marqueur serait coupé entre deux lectures.
                            if (pending.length() > WINDOW_MARKER.length()) {
                                pending.delete(0, pending.length() - WINDOW_MARKER.length());
                            }
                        }
                    }
                }
            } catch (IOException e) {
                // fichier momentanément verrouillé : nouvel essai au prochain tour
            }
            Thread.sleep(POLL_MS);
        }
        return false;
    }
}
