package fr.launcher.core.download;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Avancement d'une installation, mis à jour depuis plusieurs threads.
 * L'interface le lit à son rythme (quelques fois par seconde) au lieu d'être notifiée à chaque octet.
 */
public final class Progress {

    private final AtomicLong totalBytes = new AtomicLong();
    private final AtomicLong doneBytes = new AtomicLong();
    private final AtomicInteger totalFiles = new AtomicInteger();
    private final AtomicInteger doneFiles = new AtomicInteger();
    /** Octets réellement reçus du réseau pendant l'étape (les fichiers déjà valides n'y comptent pas). */
    private final AtomicLong networkBytes = new AtomicLong();
    /** Total reçu du réseau depuis le début de l'opération (toutes étapes). */
    private final AtomicLong totalNetworkBytes = new AtomicLong();
    private volatile String step = "";
    /** Installation menée hors ligne (choix du joueur ou réseau injoignable). */
    private volatile boolean offline;
    private volatile int stage;
    private volatile int stages;

    /** Passe à l'étape {@code index} sur {@code count} (ex. « 3/5 · Téléchargement de Minecraft »). */
    public void stage(int index, int count, String step) {
        this.stage = index;
        this.stages = count;
        networkBytes.set(0);
        step(step);
    }

    /** Remet tout à zéro avant une nouvelle installation. */
    public void reset() {
        stage = 0;
        stages = 0;
        networkBytes.set(0);
        totalNetworkBytes.set(0);
        offline = false;
        begin("", 0, 0);
    }

    public int stageIndex() {
        return stage;
    }

    public int stageCount() {
        return stages;
    }

    public void begin(String step, long bytes, int files) {
        this.step = step;
        totalBytes.set(bytes);
        doneBytes.set(0);
        totalFiles.set(files);
        doneFiles.set(0);
    }

    public void step(String step) {
        begin(step, 0, 0);
    }

    void addBytes(long n) {
        doneBytes.addAndGet(n);
    }

    void addNetworkBytes(long n) {
        networkBytes.addAndGet(n);
        totalNetworkBytes.addAndGet(n);
    }

    public void setOffline(boolean value) {
        offline = value;
    }

    public boolean isOffline() {
        return offline;
    }

    public long totalNetworkBytes() {
        return totalNetworkBytes.get();
    }

    /** Vrai si l'étape en cours télécharge réellement quelque chose (et ne fait pas que vérifier). */
    public boolean isDownloading() {
        return networkBytes.get() > 0;
    }

    void addUnknownSize(long n) {
        totalBytes.addAndGet(n);
    }

    void fileDone() {
        doneFiles.incrementAndGet();
    }

    public String step() {
        return step;
    }

    public long doneBytes() {
        return doneBytes.get();
    }

    public int doneFiles() {
        return doneFiles.get();
    }

    public int totalFiles() {
        return totalFiles.get();
    }

    /** Entre 0 et 1, ou -1 si indéterminé. */
    public double fraction() {
        long total = totalBytes.get();
        if (total > 0) return Math.min(1, (double) doneBytes.get() / total);
        int files = totalFiles.get();
        return files > 0 ? (double) doneFiles.get() / files : -1;
    }
}
