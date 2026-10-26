package fr.launcher.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Lecture/écriture JSON sur disque, avec écriture atomique pour ne jamais corrompre un fichier. */
public final class Json {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private Json() {
    }

    public static <T> T read(Path file, Class<T> type) throws IOException {
        return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), type);
    }

    public static JsonObject readObject(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    public static void write(Path file, Object value) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(value), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Met de côté un fichier illisible ({@code nom.corrupt}) au lieu de l'écraser,
     * pour pouvoir comprendre ou récupérer son contenu plus tard.
     */
    public static void quarantine(Path file) {
        try {
            if (Files.isRegularFile(file)) {
                Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // au pire le fichier sera écrasé à la prochaine sauvegarde
        }
    }

    public static String string(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    public static long number(JsonObject o, String key, long fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsLong() : fallback;
    }
}
