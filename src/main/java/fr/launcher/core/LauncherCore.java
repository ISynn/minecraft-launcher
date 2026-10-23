package fr.launcher.core;

import fr.launcher.core.auth.AccountStore;
import fr.launcher.core.auth.MicrosoftAuth;
import fr.launcher.core.game.GameInstaller;
import fr.launcher.core.game.GameLauncher;
import fr.launcher.core.settings.Settings;
import fr.launcher.util.Os;

import java.nio.file.Path;

/** Point d'accès unique aux services du launcher, sans aucune dépendance à l'interface. */
public final class LauncherCore {

    public final Path gameDir = Os.gameDir();
    public final Settings settings = Settings.load(gameDir.resolve("launcher/settings.json"));
    public final AccountStore accounts = new AccountStore(gameDir.resolve("launcher/account.json"));
    public final MicrosoftAuth auth = new MicrosoftAuth();
    public final GameInstaller installer = new GameInstaller(gameDir);
    public final GameLauncher launcher = new GameLauncher(installer);
}
