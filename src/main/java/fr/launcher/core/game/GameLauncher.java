package fr.launcher.core.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.launcher.LauncherConfig;
import fr.launcher.core.auth.Account;
import fr.launcher.core.settings.Settings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Construit la ligne de commande Java et démarre Minecraft. */
public final class GameLauncher {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z_]+)}");

    /** Réglages mémoire recommandés par Mojang quand la version n'en fournit pas. */
    private static final List<String> FALLBACK_JVM = List.of(
            "-XX:+UnlockExperimentalVMOptions", "-XX:+UseG1GC", "-XX:G1NewSizePercent=20",
            "-XX:G1ReservePercent=20", "-XX:MaxGCPauseMillis=50", "-XX:G1HeapRegionSize=32M");

    private final GameInstaller installer;

    public GameLauncher(GameInstaller installer) {
        this.installer = installer;
    }

    public Process launch(GameInstaller.Installation installation, Account account, Settings settings)
            throws IOException {
        GameProfile profile = installation.profile();
        Path gameDir = installer.gameDir();
        Path natives = gameDir.resolve("versions").resolve(profile.minecraftVersion()).resolve("natives");
        Files.createDirectories(natives);

        boolean quickPlay = settings.autoConnect && !LauncherConfig.SERVER_HOST.isBlank();
        Map<String, Boolean> features = Map.of(
                "is_quick_play_multiplayer", quickPlay,
                "is_demo_user", false,
                "has_custom_resolution", false);

        List<String> classpath = new ArrayList<>();
        profile.libraries().forEach(lib -> classpath.add(lib.path().toString()));
        classpath.add(installer.clientJar(profile).toString());

        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", account.name());
        vars.put("auth_uuid", account.uuid());
        vars.put("auth_access_token", account.accessToken());
        vars.put("auth_session", account.accessToken());
        vars.put("auth_xuid", "0");
        vars.put("clientid", "0");
        vars.put("user_type", account.userType());
        vars.put("user_properties", "{}");
        vars.put("version_name", profile.minecraftVersion());
        vars.put("version_type", LauncherConfig.NAME);
        vars.put("game_directory", gameDir.toString());
        vars.put("assets_root", installer.assetsDir().toString());
        vars.put("game_assets", installer.assetsDir().toString());
        vars.put("assets_index_name", profile.vanilla().getAsJsonObject("assetIndex").get("id").getAsString());
        vars.put("natives_directory", natives.toString());
        vars.put("library_directory", installer.librariesDir().toString());
        vars.put("classpath", String.join(File.pathSeparator, classpath));
        vars.put("classpath_separator", File.pathSeparator);
        vars.put("launcher_name", LauncherConfig.NAME);
        vars.put("launcher_version", LauncherConfig.VERSION);
        vars.put("quickPlayMultiplayer", LauncherConfig.SERVER_HOST + ":" + LauncherConfig.SERVER_PORT);

        List<String> command = new ArrayList<>();
        command.add(installation.java().toString());
        command.add("-Xmx" + settings.ramMb + "M");
        command.add("-Xms" + Math.min(settings.ramMb, 2048) + "M");
        command.addAll(defaultUserJvm(profile.vanilla(), features));
        if (!settings.jvmArgs.isBlank()) command.addAll(Arrays.asList(settings.jvmArgs.trim().split("\\s+")));

        List<String> jvm = expand(profile.jvmArgs(), features, vars);
        if (jvm.stream().noneMatch(a -> a.equals(vars.get("classpath")))) {
            // Anciennes versions : arguments JVM absents du JSON
            jvm.addAll(List.of("-Djava.library.path=" + natives, "-cp", vars.get("classpath")));
        }
        command.addAll(jvm);
        command.add(profile.mainClass());
        command.addAll(expand(profile.gameArgs(), features, vars));

        // La sortie du jeu va dans un fichier : si personne ne la lisait, le tampon se remplirait et bloquerait le jeu.
        Path output = gameDir.resolve("logs").resolve("game-output.log");
        Files.createDirectories(output.getParent());
        return new ProcessBuilder(command)
                .directory(gameDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
    }

    /**
     * Les versions récentes fournissent les options JVM conseillées par Mojang (ZGC, en-têtes d'objets compacts…).
     * On les reprend sans leurs -Xms/-Xmx, puisque la mémoire est réglée dans le launcher.
     */
    private static List<String> defaultUserJvm(JsonObject vanilla, Map<String, Boolean> features) {
        JsonObject arguments = vanilla.getAsJsonObject("arguments");
        JsonElement defaults = arguments == null ? null : arguments.get("default-user-jvm");
        if (defaults == null || !defaults.isJsonArray()) return FALLBACK_JVM;
        List<JsonElement> list = new ArrayList<>();
        defaults.getAsJsonArray().forEach(list::add);
        return expand(list, features, Map.of()).stream()
                .filter(a -> !a.startsWith("-Xmx") && !a.startsWith("-Xms"))
                .collect(Collectors.toList());
    }

    private static List<String> expand(List<JsonElement> args, Map<String, Boolean> features, Map<String, String> vars) {
        List<String> out = new ArrayList<>();
        for (JsonElement arg : args) {
            if (arg.isJsonPrimitive()) {
                out.add(substitute(arg.getAsString(), vars));
                continue;
            }
            JsonObject conditional = arg.getAsJsonObject();
            if (!Rules.allowed(conditional.get("rules"), features)) continue;
            JsonElement value = conditional.get("value");
            if (value.isJsonArray()) {
                for (JsonElement v : (JsonArray) value) out.add(substitute(v.getAsString(), vars));
            } else {
                out.add(substitute(value.getAsString(), vars));
            }
        }
        return out;
    }

    private static String substitute(String arg, Map<String, String> vars) {
        Matcher m = PLACEHOLDER.matcher(arg);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = vars.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
