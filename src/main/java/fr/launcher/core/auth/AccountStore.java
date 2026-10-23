package fr.launcher.core.auth;

import fr.launcher.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Mémorise le compte connecté entre deux lancements. */
public final class AccountStore {

    private final Path file;

    public AccountStore(Path file) {
        this.file = file;
    }

    public Optional<Account> load() {
        try {
            if (!Files.isRegularFile(file)) return Optional.empty();
            return Optional.ofNullable(Json.read(file, Account.class));
        } catch (IOException | RuntimeException e) {
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
