// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.settings;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmArgsTest {

    @Test
    void optionsCourantesAcceptees() {
        JvmArgs.Parsed parsed = JvmArgs.parse("-XX:+UseZGC  -Dfile.encoding=UTF-8 -Xss2M");
        assertTrue(parsed.valid());
        assertEquals(List.of("-XX:+UseZGC", "-Dfile.encoding=UTF-8", "-Xss2M"), parsed.args());
    }

    @Test
    void guillemetsRespectes() {
        JvmArgs.Parsed parsed = JvmArgs.parse("-Dchemin=\"C:/Mes Jeux/mc\" -XX:+UseG1GC");
        assertEquals(List.of("-Dchemin=C:/Mes Jeux/mc", "-XX:+UseG1GC"), parsed.args());
        assertFalse(JvmArgs.parse("-Dx=\"ouvert").valid());
    }

    @Test
    void videAccepte() {
        assertTrue(JvmArgs.parse("   ").valid());
        assertTrue(JvmArgs.parse(null).args().isEmpty());
    }

    @Test
    void optionsDangereusesRefusees() {
        for (String bad : List.of("-javaagent:x.jar", "-agentlib:jdwp=transport=dt_socket",
                "-XX:OnOutOfMemoryError=calc.exe", "-XX:OnError=cmd", "-cp autre.jar", "-jar x.jar")) {
            assertFalse(JvmArgs.parse(bad).valid(), bad);
        }
    }

    @Test
    void memoireGereeParLeCurseur() {
        JvmArgs.Parsed parsed = JvmArgs.parse("-Xmx8G");
        assertFalse(parsed.valid());
        assertTrue(parsed.error().contains("curseur"));
    }

    @Test
    void optionInconnueRefusee() {
        assertFalse(JvmArgs.parse("--add-opens java.base/java.lang=ALL-UNNAMED").valid());
    }
}
