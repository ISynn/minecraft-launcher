// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GameOutputRotationTest {

    @TempDir
    Path logs;

    @Test
    void lesTroisDernieresPartiesSontConservees() throws IOException {
        Path output = logs.resolve("game-output.log");
        for (int game = 1; game <= 5; game++) {
            GameLauncher.rotate(output);
            Files.writeString(output, "partie " + game);
        }
        assertEquals("partie 5", Files.readString(output));
        assertEquals("partie 4", Files.readString(logs.resolve("game-output-1.log")));
        assertEquals("partie 3", Files.readString(logs.resolve("game-output-2.log")));
        assertEquals("partie 2", Files.readString(logs.resolve("game-output-3.log")));
        assertFalse(Files.exists(logs.resolve("game-output-4.log")));
    }
}
