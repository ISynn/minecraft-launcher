package fr.launcher.core.auth;

import fr.launcher.core.log.Log;
import fr.launcher.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Mémorise le compte connecté entre deux lancements. */
public final class AccountStore {

    private static final Logger LOG = Log.get(AccountStore.class);

    private final Path file;

    public AccountStore(Path file) {
        this.file = file;
    }

    public Optional<Account> load() {
        try {
            if (!Files.isRegularFile(file)) return Optional.empty();
            return Optional.ofNullable(Json.read(file, Account.class));
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Compte mémorisé illisible : une nouvelle connexion sera demandée", e);
            // Pas de copie de côté : le fichier peut contenir des jetons.
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // il sera réécrit à la prochaine connexion
            }
            return Optional.empty();
        }
    }

    public void save(Account account) throws IOException {
        Json.write(file, account);
    }

    public void clear() throws IOException {
        Files.deleteIfExists(file);
    }
}
