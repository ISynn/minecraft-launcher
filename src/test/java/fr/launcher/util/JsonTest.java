package fr.launcher.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JsonTest {

    @TempDir
    Path dir;

    @Test
    void ecritureAtomiqueSansFichierTemporaireResiduel() throws IOException {
        Path file = dir.resolve("a/b/c.json");
        Json.write(file, Map.of("cle", "valeur"));
        assertEquals("valeur", Json.readObject(file).get("cle").getAsString());
        assertFalse(Files.exists(dir.resolve("a/b/c.json.tmp")));
    }
}
