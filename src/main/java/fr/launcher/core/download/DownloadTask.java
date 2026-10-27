// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.download;

import java.nio.file.Path;

/**
 * Un fichier à télécharger.
 *
 * @param sha1   empreinte attendue (null si inconnue)
 * @param size   taille attendue en octets (0 si inconnue)
 * @param strict si true, un fichier déjà présent est revérifié par SHA-1 ; sinon la taille suffit
 *               (les assets sont nommés par leur hash, inutile de tout recalculer à chaque lancement)
 */
public record DownloadTask(String url, Path target, String sha1, long size, boolean strict) {

    public DownloadTask strict(boolean value) {
        return new DownloadTask(url, target, sha1, size, value);
    }
}
