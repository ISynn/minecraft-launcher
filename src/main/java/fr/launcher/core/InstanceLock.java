package fr.launcher.core;

import fr.launcher.core.log.Log;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Empêche d'ouvrir deux launchers à la fois : deux installations simultanées dans le même dossier
 * s'abîmeraient mutuellement. Le verrou est libéré par le système à la fermeture, même après un plantage.
 */
public final class InstanceLock {

    private static final Logger LOG = Log.get(InstanceLock.class);

    private final Path file;
    private FileChannel channel;
    private FileLock lock;

    InstanceLock(Path file) {
        this.file = file;
    }

    /** @return true si cette instance est la seule ouverte (le verrou est alors gardé jusqu'à la fermeture) */
    public synchronized boolean acquire() {
        if (lock != null) return true;
        try {
            Files.createDirectories(file.getParent());
            channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = channel.tryLock();
            if (lock == null) channel.close();
            return lock != null;
        } catch (OverlappingFileLockException e) {
            return false;
        } catch (IOException e) {
            // Sans verrou possible (disque en lecture seule…), on n'empêche pas de jouer : on le signale seulement.
            LOG.log(Level.WARNING, "Verrou d'instance indisponible", e);
            return true;
        }
    }

    public synchronized void release() {
        try {
            if (lock != null) lock.release();
            if (channel != null) channel.close();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Verrou non libéré proprement", e);
        } finally {
            lock = null;
            channel = null;
        }
    }
}
