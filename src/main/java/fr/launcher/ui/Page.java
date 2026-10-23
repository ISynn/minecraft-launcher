package fr.launcher.ui;

/** Pages accessibles depuis la barre latérale. */
public enum Page {
    HOME("Accueil", Icons.HOME),
    SETTINGS("Paramètres", Icons.SETTINGS);

    public final String label;
    public final String icon;

    Page(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }
}
