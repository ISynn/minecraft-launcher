package fr.launcher.ui.theme;

import fr.launcher.core.log.Log;
import javafx.scene.image.Image;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Ressources de marque facultatives (illustration de fond, logo…), lues sur le disque et jamais embarquées.
 *
 * <p>Les visuels de PokeIsland appartiennent à leurs auteurs : ils ne sont pas versionnés avec le code.
 * On les cherche dans {@code ./branding/} (développement, ignoré par Git) puis dans
 * {@code <dossier du jeu>/launcher/branding/}. Sans eux, le launcher garde un rendu complet de secours.</p>
 */
public final class Branding {

    private static final Logger LOG = Log.get(Branding.class);
    private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg"};

    private final List<Path> directories = new ArrayList<>();

    public Branding(Path gameDir) {
        directories.add(Path.of("branding"));
        directories.add(gameDir.resolve("launcher").resolve("branding"));
    }

    /**
     * Charge une image en arrière-plan (sans bloquer l'interface).
     *
     * @param name nom sans extension, par ex. « background »
     */
    public Optional<Image> image(String name) {
        for (Path dir : directories) {
            for (String ext : EXTENSIONS) {
                Path file = dir.resolve(name + ext);
                if (Files.isRegularFile(file)) {
                    LOG.info("Visuel « " + name + " » chargé depuis " + file.toAbsolutePath());
                    return Optional.of(new Image(file.toUri().toString(), true));
                }
            }
        }
        return Optional.empty();
    }
}
