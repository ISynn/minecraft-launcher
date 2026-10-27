// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionResolverTest {

    @TempDir
    Path versions;

    private void install(String id) throws IOException {
        Files.createDirectories(versions.resolve(id));
        Files.writeString(versions.resolve(id).resolve(id + ".json"), "{}");
    }

    @Test
    void horsLigneOnReprendLeFabricInstalleLePlusRecent() throws IOException {
        install("fabric-loader-0.16.10-1.21.1");
        install("fabric-loader-0.17.3-1.21.1");
        install("fabric-loader-0.9.2-1.21.1");
        install("fabric-loader-0.18.0-26.3"); // autre version de Minecraft : ignorée
        Files.createDirectories(versions.resolve("fabric-loader-0.99.0-1.21.1")); // dossier sans fiche : ignoré
        assertEquals(Optional.of("0.17.3"), VersionResolver.installedLoader(versions, "1.21.1"));
    }

    @Test
    void aucunFabricInstalle() {
        assertTrue(VersionResolver.installedLoader(versions, "1.21.1").isEmpty());
    }

    @Test
    void comparaisonNumeriqueDesVersions() {
        assertTrue(VersionResolver.compareVersions("0.17.3", "0.9.10") > 0);
        assertTrue(VersionResolver.compareVersions("0.16.10", "0.16.9") > 0);
        assertEquals(0, VersionResolver.compareVersions("1.0", "1.0.0"));
    }
}
