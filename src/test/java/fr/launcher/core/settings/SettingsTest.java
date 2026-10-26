package fr.launcher.core.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {

    @TempDir
    Path dir;

    @Test
    void sauvegardeEtRelecture() {
        Path file = dir.resolve("settings.json");
        Settings s = Settings.load(file);
        s.ramMb = 2048;
        s.hideWhilePlaying = false;
        s.save();
        Settings again = Settings.load(file);
        assertEquals(2048, again.ramMb);
        assertFalse(again.hideWhilePlaying);
    }

    @Test
    void memoireRameneeDansLesLimites() throws IOException {
        Path file = dir.resolve("settings.json");
        Files.writeString(file, "{\"ramMb\": 999999}");
        assertEquals(Settings.maxRamMb(), Settings.load(file).ramMb);
        Files.writeString(file, "{\"ramMb\": 12}");
        assertEquals(1024, Settings.load(file).ramMb);
    }

    @Test
    void fichierCorrompuMisDeCote() throws IOException {
        Path file = dir.resolve("settings.json");
        Files.writeString(file, "{ ceci n'est pas du json");
        Settings s = Settings.load(file);
        assertEquals(Math.min(4096, Settings.maxRamMb()), s.ramMb, "valeurs par défaut");
        assertTrue(Files.isRegularFile(dir.resolve("settings.json.corrupt")), "l'original est conservé pour analyse");
    }
}
