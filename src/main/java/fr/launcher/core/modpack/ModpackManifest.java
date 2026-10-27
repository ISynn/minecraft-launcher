// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.modpack;

import java.util.List;

/**
 * Contenu du fichier modpack.json hébergé en ligne (généré avec l'outil ManifestGenerator).
 *
 * @param minecraft      version de Minecraft imposée par le serveur (optionnel)
 * @param fabricLoader   version de Fabric Loader imposée (optionnel)
 * @param files          fichiers à installer, chemins relatifs au dossier du jeu
 * @param managedFolders dossiers contrôlés par le serveur : tout fichier en trop y est supprimé
 */
public record ModpackManifest(String minecraft, String fabricLoader, List<FileEntry> files, List<String> managedFolders) {

    public record FileEntry(String path, String url, String sha1, long size) {
    }
}
