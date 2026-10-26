package fr.launcher.core.settings;

import fr.launcher.core.log.Log;
import fr.launcher.util.Json;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Préférences de l'utilisateur, sauvegardées dans settings.json.
 * Toute valeur invalide (fichier modifié à la main, ancienne version) est corrigée au chargement.
 */
public final class Settings {

    private static final Logger LOG = Log.get(Settings.class);

    /** Un jeu moddé ne démarre pas correctement avec moins de 2 Go. */
    public static final int MIN_RAM_MB = 2048;
    public static final int MIN_WIDTH = 854;
    public static final int MIN_HEIGHT = 480;
    public static final int MAX_SIZE = 7680;

    public int ramMb = 4096;
    public String jvmArgs = "";
    public boolean hideWhilePlaying = true;
    public boolean autoConnect = true;

    /** Taille de la fenêtre du jeu (si {@link #customResolution}), ou plein écran. */
    public boolean customResolution = false;
    public int windowWidth = 1280;
    public int windowHeight = 720;
    public boolean fullscreen = false;

    /** Faux = mode hors ligne : on lance avec les fichiers déjà installés, sans rien demander au réseau. */
    public boolean checkUpdates = true;
    /** Journal détaillé (niveau FINE), pour le diagnostic. */
    public boolean verboseLog = false;
    /** Dossier du jeu choisi par le joueur ; vide = dossier par défaut du launcher. Pris en compte au redémarrage. */
    public String gameDir = "";

    private transient Path file;

    public static Settings load(Path file) {
        Settings settings;
        try {
            settings = Files.isRegularFile(file) ? Json.read(file, Settings.class) : new Settings();
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Préférences illisibles, valeurs par défaut utilisées", e);
            Json.quarantine(file);
            settings = new Settings();
        }
        if (settings == null) settings = new Settings();
        settings.file = file;
        settings.sanitize();
        return settings;
    }

    /** Ramène chaque valeur dans un domaine valide. */
    void sanitize() {
        ramMb = Math.clamp(ramMb, Math.min(MIN_RAM_MB, maxRamMb()), maxRamMb());
        windowWidth = Math.clamp(windowWidth, MIN_WIDTH, MAX_SIZE);
        windowHeight = Math.clamp(windowHeight, MIN_HEIGHT, MAX_SIZE);
        if (jvmArgs == null) jvmArgs = "";
        JvmArgs.Parsed parsed = JvmArgs.parse(jvmArgs);
        if (!parsed.valid()) {
            LOG.warning("Options Java ignorées : " + parsed.error());
            jvmArgs = "";
        }
        if (gameDir == null) gameDir = "";
        GameDirectory.problem(gameDir).ifPresent(problem -> {
            LOG.warning("Dossier du jeu ignoré (" + gameDir + ") : " + problem);
            gameDir = "";
        });
    }

    /** Mémoire physique totale du PC, en Mo. */
    public static long systemRamMb() {
        return ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
                .getTotalMemorySize() / 1_048_576;
    }

    /** Mémoire maximale allouable au jeu : on laisse toujours au moins 2 Go au système. */
    public static int maxRamMb() {
        return (int) Math.max(2048, Math.min(32768, (systemRamMb() - 2048) / 512 * 512));
    }

    public void save() {
        try {
            Json.write(file, this);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Impossible de sauvegarder les paramètres", e);
        }
    }
}
