package fr.launcher.core;

import fr.launcher.LauncherConfig;
import fr.launcher.core.auth.AccountStore;
import fr.launcher.core.auth.MicrosoftAuth;
import fr.launcher.core.game.GameInstaller;
import fr.launcher.core.game.GameLauncher;
import fr.launcher.core.log.Log;
import fr.launcher.core.news.BlogNewsProvider;
import fr.launcher.core.news.NewsService;
import fr.launcher.core.settings.Settings;
import fr.launcher.util.Os;

import java.nio.file.Path;

/**
 * Point d'accès unique aux services du launcher, sans aucune dépendance à l'interface.
 *
 * <p>Deux dossiers : celui du launcher (préférences, compte, journal, cache) est fixe ; celui du jeu
 * (Minecraft, Java, mods, mondes) est le même par défaut, mais le joueur peut en choisir un autre.</p>
 */
public final class LauncherCore {

    /** Dossier du launcher : %APPDATA%/.pokeisland-prototype. */
    public final Path dataDir = Os.gameDir();
    public final Path logsDir = dataDir.resolve("launcher").resolve("logs");
    public final Settings settings;
    public final Path gameDir;
    public final AccountStore accounts;
    public final MicrosoftAuth auth = new MicrosoftAuth();
    public final GameInstaller installer;
    public final GameLauncher launcher;
    public final NewsService news;
    public final InstanceLock instanceLock;

    public LauncherCore() {
        // Le journal d'abord : le chargement des réglages peut déjà avoir quelque chose à signaler.
        Log.init(logsDir);
        settings = Settings.load(dataDir.resolve("launcher/settings.json"));
        Log.setVerbose(settings.verboseLog);
        gameDir = settings.gameDir.isBlank() ? dataDir : Path.of(settings.gameDir).toAbsolutePath().normalize();
        instanceLock = new InstanceLock(dataDir.resolve("launcher/instance.lock"));
        accounts = new AccountStore(dataDir.resolve("launcher"));
        installer = new GameInstaller(gameDir);
        launcher = new GameLauncher(installer);
        news = new NewsService(new BlogNewsProvider(LauncherConfig.BLOG_URL),
                dataDir.resolve("launcher/cache/news.json"));
    }
}
