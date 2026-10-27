// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.auth;

import fr.launcher.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AccountTest {

    @TempDir
    Path dir;

    private static final Account MICROSOFT = new Account(Account.Type.MICROSOFT, "069a79f444e94726a5befca90e38aaf5",
            "Steve", "jeton-acces-secret", "jeton-renouvellement-secret", 4_000_000_000_000L);

    @Test
    void uuidHorsLigneStable() {
        assertEquals(Account.offline("Steve").uuid(), Account.offline("Steve").uuid());
        assertEquals(32, Account.offline("Steve").uuid().length());
        assertFalse(Account.offline("Steve").needsRefresh());
    }

    @Test
    void compteMicrosoftExpire() {
        Account expired = new Account(Account.Type.MICROSOFT, "id", "Steve", "t", "r", System.currentTimeMillis());
        assertTrue(expired.needsRefresh());
    }

    @Test
    void enregistrementEtRelecture() throws IOException {
        AccountStore store = new AccountStore(dir);
        store.save(MICROSOFT);
        assertEquals(MICROSOFT, new AccountStore(dir).load().orElseThrow());
    }

    @Test
    void jetonsJamaisEnClairSurLeDisque() throws IOException {
        assumeTrue(AccountStore.ENCRYPTED, "chiffrement DPAPI : Windows uniquement");
        new AccountStore(dir).save(MICROSOFT);
        assertFalse(Files.exists(dir.resolve("account.json")));
        String stored = new String(Files.readAllBytes(dir.resolve("account.dat")), StandardCharsets.ISO_8859_1);
        assertFalse(stored.contains("secret"), "aucun jeton lisible dans le fichier");
        assertFalse(stored.contains("Steve"));
    }

    @Test
    void ancienFichierEnClairMigre() throws IOException {
        assumeTrue(AccountStore.ENCRYPTED, "chiffrement DPAPI : Windows uniquement");
        Json.write(dir.resolve("account.json"), MICROSOFT);
        assertEquals(MICROSOFT, new AccountStore(dir).load().orElseThrow());
        assertFalse(Files.exists(dir.resolve("account.json")), "la version en clair est supprimée");
        assertTrue(Files.exists(dir.resolve("account.dat")));
    }

    @Test
    void fichierCorrompuSupprime() throws IOException {
        Path file = dir.resolve(AccountStore.ENCRYPTED ? "account.dat" : "account.json");
        Files.writeString(file, "{ corrompu");
        assertTrue(new AccountStore(dir).load().isEmpty());
        assertFalse(Files.exists(file), "un compte illisible peut contenir des jetons : il ne doit pas traîner");
    }

    @Test
    void dpapiAllerRetourEtAltération() throws IOException {
        assumeTrue(SecretBox.SUPPORTED);
        byte[] data = "donnée sensible".getBytes(StandardCharsets.UTF_8);
        byte[] sealed = SecretBox.protect(data);
        assertArrayEquals(data, SecretBox.unprotect(sealed));
        sealed[sealed.length / 2] ^= 0x5A;
        assertThrows(IOException.class, () -> SecretBox.unprotect(sealed), "une altération est détectée");
    }
}
