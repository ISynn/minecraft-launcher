package fr.launcher.core.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.launcher.core.download.DownloadTask;

import java.nio.file.Path;
import java.util.List;

/**
 * Une version prête à lancer : Minecraft vanilla fusionné avec le profil Fabric.
 *
 * @param vanilla le JSON de version Mojang (client.jar, assets, version de Java…)
 */
public record GameProfile(
        String minecraftVersion,
        String loaderVersion,
        String mainClass,
        JsonObject vanilla,
        List<Library> libraries,
        List<JsonElement> jvmArgs,
        List<JsonElement> gameArgs) {

    public record Library(String name, Path path, String url, String sha1, long size) {
        DownloadTask task() {
            return new DownloadTask(url, path, sha1, size, true);
        }
    }

    public String displayName() {
        return "Minecraft " + minecraftVersion + " · Fabric " + loaderVersion;
    }
}
