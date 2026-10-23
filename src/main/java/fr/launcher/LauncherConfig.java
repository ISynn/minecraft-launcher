package fr.launcher;

/**
 * Toute la personnalisation du launcher est ici : nom, serveur, version, modpack.
 */
public final class LauncherConfig {

    /** Nom affiché partout dans le launcher. */
    public static final String NAME = "Nova";
    /** Petite phrase sous le titre. */
    public static final String TAGLINE = "Serveur Minecraft moddé";
    /** Nom du dossier du jeu (%APPDATA%/.nova sous Windows). */
    public static final String DIR_NAME = "nova";
    public static final String VERSION = "1.0.0";

    /** Adresse du serveur : utilisée pour le statut en ligne et la connexion automatique. */
    public static final String SERVER_HOST = "localhost";
    public static final int SERVER_PORT = 25565;

    /**
     * URL du fichier modpack.json (voir README). Vide = Minecraft + Fabric sans mods.
     * Si le modpack précise une version de Minecraft ou de Fabric, elle remplace celles ci-dessous.
     */
    public static final String MODPACK_URL = "";
    public static final String MINECRAFT_VERSION = "26.3";
    /** Vide = dernière version stable de Fabric Loader pour cette version de Minecraft. */
    public static final String FABRIC_LOADER = "";

    /** Client ID de l'application Azure (voir README). Vide = connexion Microsoft désactivée. */
    public static final String AZURE_CLIENT_ID = "";

    /** Mode développeur : autorise un compte hors-ligne pour tester. À passer à false avant de distribuer. */
    public static final boolean DEV_MODE = true;

    private LauncherConfig() {
    }
}
