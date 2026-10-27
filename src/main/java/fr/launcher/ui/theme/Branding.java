// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
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
 * On les cherche dans {@code ./branding/} (développement, ignoré par Git), à côté de l'exécutable (version
 * empaquetée avec {@code build-windows.ps1 -Branding}) puis dans {@code <dossier du launcher>/launcher/branding/}.
 * Sans eux, le launcher garde un rendu complet de secours.</p>
 */
public final class Branding {

    private static final Logger LOG = Log.get(Branding.class);
    private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg"};

    private final List<Path> directories = new ArrayList<>();

    public Branding(Path dataDir) {
        directories.add(Path.of("branding"));
        // Propriété posée par le lanceur de jpackage : chemin du .exe de la version empaquetée.
        String exe = System.getProperty("jpackage.app-path");
        if (exe != null) directories.add(Path.of(exe).toAbsolutePath().getParent().resolve("branding"));
        directories.add(dataDir.resolve("launcher").resolve("branding"));
    }

    /**
     * Charge une image en arrière-plan (sans bloquer l'interface).
     *
     * @param name nom sans extension, par ex. « background »
     */
    public Optional<Image> image(String name) {
        return image(name, 0);
    }

    /**
     * @param maxHeight hauteur de décodage maximale en pixels (0 = taille d'origine) : un logo affiché à 165 px
     *                  n'a pas besoin d'être gardé en mémoire à 826 px
     */
    public Optional<Image> image(String name, int maxHeight) {
        for (Path dir : directories) {
            for (String ext : EXTENSIONS) {
                Path file = dir.resolve(name + ext);
                if (Files.isRegularFile(file)) {
                    LOG.info("Visuel « " + name + " » chargé depuis " + file.toAbsolutePath());
                    return Optional.of(new Image(file.toUri().toString(), 0, maxHeight, true, true, true));
                }
            }
        }
        return Optional.empty();
    }
}
