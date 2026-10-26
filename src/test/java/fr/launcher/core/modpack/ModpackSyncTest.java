package fr.launcher.core.modpack;

import fr.launcher.core.download.Progress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void seulsLesDossiersDeContenuPeuventEtreGeres() throws IOException {
        ModpackSync sync = new ModpackSync(game);
        assertEquals(game.toAbsolutePath().normalize().resolve("mods"), sync.managedFolder("mods"));
        for (String folder : List.of("saves", "config", "screenshots", "mods/../saves", "launcher")) {
            assertThrows(IOException.class, () -> sync.managedFolder(folder), folder);
        }
    }

    @Test
    void lesDonneesDuJoueurNeSontJamaisEcrasees() throws IOException {
        ModpackSync sync = new ModpackSync(game);
        sync.writableTarget("mods/cobblemon.jar");
        sync.writableTarget("config/cobblemon/main.json");
        for (String path : List.of("saves/monde/level.dat", "options.txt", "launcher/account.dat",
                "screenshots/a.png", "servers.dat", ".modpack.json", "runtime/java.exe")) {
            assertThrows(IOException.class, () -> sync.writableTarget(path), path);
        }
    }

    @Test
    void empreinteObligatoire() {
        ModpackSync sync = new ModpackSync(game);
        ModpackManifest manifest = new ModpackManifest(null, null,
                List.of(new ModpackManifest.FileEntry("mods/a.jar", "https://exemple.fr/a.jar", null, 10)),
                List.of());
        IOException e = assertThrows(IOException.class, () -> sync.sync(manifest, new Progress(), false));
        assertTrue(e.getMessage().contains("SHA-1"));
    }
}
