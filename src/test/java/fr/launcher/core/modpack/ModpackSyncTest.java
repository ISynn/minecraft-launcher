package fr.launcher.core.modpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModpackSyncTest {

    @TempDir
    Path game;

    @Test
    void cheminNormalAccepte() throws IOException {
        ModpackSync sync = new ModpackSync(game);
        assertEquals(game.toAbsolutePath().normalize().resolve("mods/a.jar"), sync.safeResolve("mods/a.jar"));
    }

    @Test
    void sortieDuDossierRefusee() {
        ModpackSync sync = new ModpackSync(game);
        assertThrows(IOException.class, () -> sync.safeResolve("../../Windows/evil.dll"));
        assertThrows(IOException.class, () -> sync.safeResolve("mods/../../evil.jar"));
        assertThrows(IOException.class, () -> sync.safeResolve("."), "le dossier du jeu lui-même est interdit");
    }

    @Test
    void cheminAbsoluRefuse() {
        ModpackSync sync = new ModpackSync(game);
        Path outside = game.getParent().resolve("ailleurs.jar").toAbsolutePath();
        assertThrows(IOException.class, () -> sync.safeResolve(outside.toString()));
    }
}
