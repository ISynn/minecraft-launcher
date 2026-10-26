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
     * Nom du dossier du jeu (%APPDATA%/.pokeisland-prototype sous Windows).
     * Ne doit jamais valoir « pokeisland » : Windows ignore la casse et ce serait le dossier du launcher officiel
     * (voir {@link fr.launcher.util.Os#isOfficialLauncherDir}).
     */
    public static final String DIR_NAME = "pokeisland-prototype";

    /** Adresse du serveur : utilisée pour le statut en ligne et la connexion automatique. */
    public static final String SERVER_HOST = "localhost";
    public static final int SERVER_PORT = 25565;

    /**
     * URL du fichier modpack.json (voir README). Vide = Minecraft + Fabric sans mods.
     * Si le modpack précise une version de Minecraft ou de Fabric, elle remplace celles ci-dessous.
     */
    public static final String MODPACK_URL = "";
    /** Version du serveur PokeIsland (relevée dans l'installation officielle : Minecraft 1.21.1, Fabric 0.17.3). */
    public static final String MINECRAFT_VERSION = "1.21.1";
    /** Version figée : tous les joueurs ont exactement le même chargeur. Vide = dernière version stable. */
    public static final String FABRIC_LOADER = "0.17.3";

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
