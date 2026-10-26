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

/** Point d'accès unique aux services du launcher, sans aucune dépendance à l'interface. */
public final class LauncherCore {

    public final Path gameDir = Os.gameDir();
    public final Path logsDir = gameDir.resolve("launcher").resolve("logs");
    public final Settings settings;
    public final AccountStore accounts;
    public final MicrosoftAuth auth = new MicrosoftAuth();
    public final GameInstaller installer;
    public final GameLauncher launcher;
    public final NewsService news;

    public LauncherCore() {
        // Le journal d'abord : le chargement des réglages peut déjà avoir quelque chose à signaler.
        Log.init(logsDir);
        settings = Settings.load(gameDir.resolve("launcher/settings.json"));
        accounts = new AccountStore(gameDir.resolve("launcher/account.json"));
        installer = new GameInstaller(gameDir);
        launcher = new GameLauncher(installer);
        news = new NewsService(new BlogNewsProvider(LauncherConfig.BLOG_URL),
                gameDir.resolve("launcher/cache/news.json"));
    }
}
