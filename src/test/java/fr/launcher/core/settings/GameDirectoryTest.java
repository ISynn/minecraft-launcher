package fr.launcher.core.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GameDirectoryTest {

    @TempDir
    Path dir;

    @Test
    void dossierPersonnelAccepte() {
        assertTrue(GameDirectory.problem(dir.resolve("jeu").toString()).isEmpty());
        assertTrue(GameDirectory.problem("").isEmpty(), "vide = dossier par défaut");
    }

    @Test
    void dossierDuLauncherOfficielRefuseQuelleQueSoitLaCasse() {
        assertTrue(GameDirectory.problem(dir.resolve(".Pokeisland").toString()).isPresent());
        assertTrue(GameDirectory.problem(dir.resolve(".POKEISLAND").resolve("Pokeisland").toString()).isPresent());
    }

    @Test
    void racineEtFichierRefuses() throws IOException {
        assertTrue(GameDirectory.problem(dir.getRoot().toString()).isPresent());
        Path file = Files.writeString(dir.resolve("fichier.txt"), "x");
        assertTrue(GameDirectory.problem(file.toString()).isPresent());
    }
}
