// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.download;

import fr.launcher.util.Hash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloaderTest {

    @TempDir
    Path dir;

    private Path file(String content) throws IOException {
        return Files.writeString(dir.resolve("f.bin"), content);
    }

    @Test
    void fichierAbsentInvalide() throws IOException {
        assertFalse(Downloader.isValid(new DownloadTask("u", dir.resolve("absent"), null, 0, true)));
    }

    @Test
    void tailleDifferenteInvalide() throws IOException {
        Path f = file("abc");
        assertFalse(Downloader.isValid(new DownloadTask("u", f, null, 4, false)));
        assertTrue(Downloader.isValid(new DownloadTask("u", f, null, 3, false)));
    }

    @Test
    void modeStrictVerifieLeSha1() throws IOException {
        Path f = file("abc");
        String good = Hash.sha1(f);
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", good);
        assertTrue(Downloader.isValid(new DownloadTask("u", f, good, 3, true)));
        String bad = "0000000000000000000000000000000000000000";
        assertFalse(Downloader.isValid(new DownloadTask("u", f, bad, 3, true)), "fichier corrompu de même taille");
        assertTrue(Downloader.isValid(new DownloadTask("u", f, bad, 3, false)), "hors mode strict, la taille suffit");
    }

    @Test
    void fichiersDejaPresentsAucunTelechargement() throws Exception {
        Path f = file("abc");
        Progress progress = new Progress();
        // URL invalide : le test échouerait si le Downloader tentait une connexion.
        Downloader.downloadAll("test", List.of(new DownloadTask("http://invalid.invalid/f", f, Hash.sha1(f), 3, true)),
                progress);
        assertEquals(1, progress.doneFiles());
        assertEquals(1.0, progress.fraction());
    }
}
