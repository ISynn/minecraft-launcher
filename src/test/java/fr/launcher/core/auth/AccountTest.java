package fr.launcher.core.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountTest {

    @TempDir
    Path dir;

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
    void fichierDeCompteCorrompuSupprime() throws IOException {
        Path file = dir.resolve("account.json");
        Files.writeString(file, "{ corrompu");
        assertTrue(new AccountStore(file).load().isEmpty());
        assertFalse(Files.exists(file), "un compte illisible peut contenir des jetons : il ne doit pas traîner");
        assertFalse(Files.exists(dir.resolve("account.json.corrupt")));
    }
}
