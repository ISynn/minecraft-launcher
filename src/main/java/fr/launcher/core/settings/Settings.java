package fr.launcher.core.settings;

import fr.launcher.core.log.Log;
import fr.launcher.util.Json;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Préférences de l'utilisateur, sauvegardées dans settings.json. */
public final class Settings {

    private static final Logger LOG = Log.get(Settings.class);

    public int ramMb = 4096;
    public String jvmArgs = "";
    public boolean hideWhilePlaying = true;
    public boolean autoConnect = true;

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
        settings.ramMb = Math.clamp(settings.ramMb, 1024, maxRamMb());
        return settings;
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
