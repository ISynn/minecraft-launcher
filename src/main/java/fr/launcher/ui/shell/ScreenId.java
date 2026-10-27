// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui.shell;

import fr.launcher.ui.Icons;

/** Écrans internes accessibles depuis le rail de navigation, dans l'ordre d'affichage. */
public enum ScreenId {
    HOME("Accueil", Icons.HOME),
    NEWS("Actualités", Icons.NEWS),
    INSTALL("Installation", Icons.DOWNLOAD),
    ACCOUNT("Mon compte", Icons.ACCOUNT),
    SETTINGS("Paramètres", Icons.SETTINGS),
    HELP("Aide", Icons.HELP);

    public final String label;
    public final String icon;

    ScreenId(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }
}
