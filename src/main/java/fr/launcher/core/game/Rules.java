// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.launcher.util.Json;
import fr.launcher.util.Os;

import java.util.Map;
import java.util.regex.Pattern;

/** Évalue les « rules » des JSON de Mojang (bibliothèques et arguments selon l'OS et les fonctionnalités). */
final class Rules {

    private static final String OS_VERSION = osVersion();

    private Rules() {
    }

    static boolean allowed(JsonElement rules, Map<String, Boolean> features) {
        if (rules == null || !rules.isJsonArray()) return true;
        boolean allowed = false;
        for (JsonElement element : rules.getAsJsonArray()) {
            JsonObject rule = element.getAsJsonObject();
            if (matches(rule, features)) {
                allowed = "allow".equals(Json.string(rule, "action", "allow"));
            }
        }
        return allowed;
    }

    private static boolean matches(JsonObject rule, Map<String, Boolean> features) {
        if (rule.has("os")) {
            JsonObject os = rule.getAsJsonObject("os");
            if (os.has("name") && !os.get("name").getAsString().equals(Os.CURRENT.mojangName)) return false;
            if (os.has("arch") && os.get("arch").getAsString().equals("x86") != Os.X86) return false;
            if (os.has("version") && !Pattern.compile(os.get("version").getAsString()).matcher(OS_VERSION).find()) {
                return false;
            }
            if (os.has("versionRange")) {
                JsonObject range = os.getAsJsonObject("versionRange");
                if (range.has("min") && compare(OS_VERSION, range.get("min").getAsString()) < 0) return false;
                if (range.has("max") && compare(OS_VERSION, range.get("max").getAsString()) >= 0) return false;
            }
        }
        if (rule.has("features")) {
            for (var entry : rule.getAsJsonObject("features").entrySet()) {
                boolean expected = entry.getValue().getAsBoolean();
                if (features.getOrDefault(entry.getKey(), false) != expected) return false;
            }
        }
        return true;
    }

    /**
     * Java renvoie « 10.0 » sous Windows 10 et 11, sans numéro de build.
     * On considère alors un Windows récent, ce qui est le cas de toute machine capable de lancer le jeu.
     */
    private static String osVersion() {
        String version = System.getProperty("os.version", "0");
        if (Os.CURRENT == Os.WINDOWS && version.split("\\.").length < 3) return version + ".99999";
        return version;
    }

    private static int compare(String a, String b) {
        String[] pa = a.split("[.\\-]");
        String[] pb = b.split("[.\\-]");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int va = i < pa.length ? parse(pa[i]) : 0;
            int vb = i < pb.length ? parse(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
