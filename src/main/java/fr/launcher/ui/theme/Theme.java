package fr.launcher.ui.theme;

import fr.launcher.core.log.Log;
import javafx.scene.Scene;
import javafx.scene.text.Font;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Logger;

/**
 * Thème visuel : police Kanit (celle de pokeisland.fr) et feuilles de style.
 *
 * <p>Les couleurs, rayons et espacements sont définis une seule fois dans {@code theme.css}
 * (couleurs « looked-up » de JavaFX) : changer la palette ne touche qu'un bloc.</p>
 */
public final class Theme {

    private static final Logger LOG = Log.get(Theme.class);
    private static final String ASSETS = "/fr/launcher/assets/";
    private static final String[] WEIGHTS = {"Regular", "Medium", "SemiBold", "Bold", "ExtraBold", "Black"};
    private static final String[] STYLESHEETS = {"theme.css", "shell.css", "screens.css"};

    private Theme() {
    }

    public static void loadFonts() {
        for (String weight : WEIGHTS) {
            try (InputStream in = Theme.class.getResourceAsStream(ASSETS + "fonts/Kanit-" + weight + ".ttf")) {
                if (in == null || Font.loadFont(in, 13) == null) LOG.warning("Police Kanit " + weight + " introuvable");
            } catch (IOException e) {
                LOG.warning("Police Kanit " + weight + " illisible : " + e.getMessage());
            }
        }
    }

    public static void apply(Scene scene) {
        for (String sheet : STYLESHEETS) {
            URL url = Theme.class.getResource(ASSETS + "css/" + sheet);
            if (url == null) {
                LOG.severe("Feuille de style manquante : " + sheet);
                continue;
            }
            scene.getStylesheets().add(url.toExternalForm());
        }
    }
}
