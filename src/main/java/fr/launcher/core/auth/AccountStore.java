package fr.launcher.core.auth;

import fr.launcher.core.log.Log;
import fr.launcher.util.Json;
import fr.launcher.util.Os;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mémorise le compte connecté entre deux lancements.
 *
 * <p>Sous Windows, le compte (et donc ses jetons) est chiffré avec la DPAPI dans {@code account.dat} :
 * seul l'utilisateur Windows qui l'a créé, sur ce PC, peut le relire. Ailleurs, {@code account.json}
 * n'est lisible que par son propriétaire. Un ancien {@code account.json} en clair est migré puis supprimé.</p>
 */
public final class AccountStore {

    private static final Logger LOG = Log.get(AccountStore.class);
    /** Vrai si le compte est chiffré sur ce système (Windows). */
    public static final boolean ENCRYPTED = SecretBox.SUPPORTED;

    private final Path encrypted;
    private final Path plain;

    public AccountStore(Path dir) {
        this.encrypted = dir.resolve("account.dat");
        this.plain = dir.resolve("account.json");
    }

    public Optional<Account> load() {
        try {
            if (SecretBox.SUPPORTED && Files.isRegularFile(encrypted)) {
                byte[] json = SecretBox.unprotect(Files.readAllBytes(encrypted));
                return Optional.ofNullable(Json.GSON.fromJson(new String(json, StandardCharsets.UTF_8), Account.class));
            }
            if (Files.isRegularFile(plain)) {
                Account account = Json.read(plain, Account.class);
                if (account != null && SecretBox.SUPPORTED) {
                    save(account); // migration : chiffre puis efface la version en clair
                    LOG.info("Compte mémorisé migré vers un stockage chiffré");
                }
                return Optional.ofNullable(account);
            }
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Compte mémorisé illisible : une nouvelle connexion sera demandée", e);
            // Pas de copie de côté : le fichier peut contenir des jetons.
            clearQuietly();
            return Optional.empty();
        }
    }

    public void save(Account account) throws IOException {
        byte[] json = Json.GSON.toJson(account).getBytes(StandardCharsets.UTF_8);
        if (SecretBox.SUPPORTED) {
            writeAtomically(encrypted, SecretBox.protect(json));
            Files.deleteIfExists(plain);
        } else {
            writeAtomically(plain, json);
            if (Os.CURRENT != Os.WINDOWS) {
                Files.setPosixFilePermissions(plain, PosixFilePermissions.fromString("rw-------"));
            }
        }
    }

    public void clear() throws IOException {
        Files.deleteIfExists(encrypted);
        Files.deleteIfExists(plain);
    }

    private void clearQuietly() {
        try {
            clear();
        } catch (IOException ignored) {
            // il sera réécrit à la prochaine connexion
        }
    }

    private static void writeAtomically(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
