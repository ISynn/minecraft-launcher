package fr.launcher.core.settings;

import fr.launcher.util.Os;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/** Validation d'un dossier de jeu choisi par le joueur. */
public final class GameDirectory {

    private static final int MAX_LENGTH = 180;

    private GameDirectory() {
    }

    /** @return un message d'erreur, ou vide si le dossier convient */
    public static Optional<String> problem(String value) {
        if (value == null || value.isBlank()) return Optional.empty(); // dossier par défaut
        Path dir;
        try {
            dir = Path.of(value).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return Optional.of("Chemin invalide.");
        }
        if (dir.getParent() == null) return Optional.of("Choisis un dossier, pas la racine d'un disque.");
        if (value.length() > MAX_LENGTH) return Optional.of("Chemin trop long : Minecraft risque de ne pas démarrer.");
        if (Os.isOfficialLauncherDir(dir)) {
            return Optional.of("C'est le dossier du launcher officiel de PokeIsland : "
                    + "le prototype ne doit pas y toucher.");
        }
        String lower = dir.toString().toLowerCase(Locale.ROOT);
        for (String system : new String[]{"\\windows", "\\program files", "\\programdata"}) {
            if (lower.length() > 2 && lower.substring(2).startsWith(system)) {
                return Optional.of("Dossier système : choisis un dossier personnel.");
            }
        }
        if (Files.exists(dir) && !Files.isDirectory(dir)) return Optional.of("Ce chemin désigne un fichier.");
        return writable(dir) ? Optional.empty() : Optional.of("Impossible d'écrire dans ce dossier.");
    }

    private static boolean writable(Path dir) {
        try {
            Files.createDirectories(dir);
            Path probe = Files.createTempFile(dir, ".ecriture", ".tmp");
            Files.delete(probe);
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }
}
