package fr.launcher.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsTest {

    @Test
    void dossierOfficielReconnu() {
        assertTrue(Os.isOfficialLauncherDir(Path.of("C:/Users/a/AppData/Roaming/.Pokeisland")));
        assertTrue(Os.isOfficialLauncherDir(Path.of("C:/Users/a/AppData/Roaming/.pokeisland/Pokeisland/mods")));
        assertFalse(Os.isOfficialLauncherDir(Path.of("C:/Users/a/AppData/Roaming/.pokeisland-prototype")));
    }

    @Test
    void leDossierDuPrototypeNEstPasLeDossierOfficiel() {
        assertFalse(Os.isOfficialLauncherDir(Os.gameDir()));
    }
}
