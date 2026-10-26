package fr.launcher;

import java.util.Set;

/**
 * Toute la personnalisation du launcher est ici : identité, serveur, version, liens.
 *
 * <p>Prototype indépendant : il n'est ni édité ni approuvé par PokeIsland, Mojang ou Microsoft.</p>
 */
public final class LauncherConfig {

    /** Nom affiché partout dans le launcher. */
    public static final String NAME = "PokeIsland";
    public static final String WINDOW_TITLE = "PokeIsland Launcher";
    /** Mention affichée en permanence : ce launcher n'est pas un produit officiel. */
    public static final String DISCLAIMER = "Prototype non officiel";
    public static final String VERSION = "2.0.0-alpha";

    /**
     * Nom du dossier du jeu (%APPDATA%/.nova sous Windows).
     * Ne doit jamais valoir « pokeisland » : Windows ignore la casse et ce serait le dossier du launcher officiel.
     */
    public static final String DIR_NAME = "nova";

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

    /** Liens publics, relevés sur pokeisland.fr le 26/10/2026. */
    public static final String WEBSITE_URL = "https://pokeisland.fr/";
    public static final String SHOP_URL = "https://pokeisland.fr/boutique/";
    public static final String WIKI_URL = "https://pokeisland.fr/wiki/";
    public static final String VOTE_URL = "https://pokeisland.fr/vote/";
    public static final String BLOG_URL = "https://pokeisland.fr/blog/";
    public static final String DISCORD_URL = "https://discord.gg/9SzwnBaAbT";
    public static final String YOUTUBE_URL = "https://www.youtube.com/@PokeIslandMC/";
    public static final String TIKTOK_URL = "https://www.tiktok.com/@pokeislandmc";

    /** Seuls ces domaines peuvent être ouverts dans le navigateur depuis le launcher. */
    public static final Set<String> TRUSTED_HOSTS = Set.of(
            "pokeisland.fr", "discord.gg", "www.youtube.com", "www.tiktok.com",
            "www.microsoft.com", "microsoft.com", "login.live.com", "www.minecraft.net");

    private LauncherConfig() {
    }
}
