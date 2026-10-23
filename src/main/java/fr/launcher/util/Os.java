package fr.launcher.util;

import fr.launcher.LauncherConfig;

import java.nio.file.Path;
import java.util.Locale;

/** Détection du système et dossiers du launcher. */
public enum Os {
    WINDOWS("windows"), MAC("osx"), LINUX("linux");

    /** Nom utilisé dans les règles des JSON de Mojang. */
    public final String mojangName;

    Os(String mojangName) {
        this.mojangName = mojangName;
    }

    public static final Os CURRENT = detect();
    public static final String ARCH = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
    public static final boolean ARM64 = ARCH.equals("aarch64") || ARCH.equals("arm64");
    public static final boolean X86 = ARCH.equals("x86") || ARCH.equals("i386") || ARCH.equals("i686");

    private static Os detect() {
        String name = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (name.contains("win")) return WINDOWS;
        if (name.contains("mac")) return MAC;
        return LINUX;
    }

    /** Dossier racine du jeu : %APPDATA%/.nova, ~/Library/Application Support/nova ou ~/.nova. */
    public static Path gameDir() {
        String home = System.getProperty("user.home");
        return switch (CURRENT) {
            case WINDOWS -> {
                String appData = System.getenv("APPDATA");
                yield Path.of(appData != null ? appData : home, "." + LauncherConfig.DIR_NAME);
            }
            case MAC -> Path.of(home, "Library", "Application Support", LauncherConfig.DIR_NAME);
            case LINUX -> Path.of(home, "." + LauncherConfig.DIR_NAME);
        };
    }

    /** Clé de plateforme utilisée par l'index des runtimes Java de Mojang. */
    public static String javaRuntimePlatform() {
        return switch (CURRENT) {
            case WINDOWS -> ARM64 ? "windows-arm64" : X86 ? "windows-x86" : "windows-x64";
            case MAC -> ARM64 ? "mac-os-arm64" : "mac-os";
            case LINUX -> X86 ? "linux-i386" : "linux";
        };
    }
}
