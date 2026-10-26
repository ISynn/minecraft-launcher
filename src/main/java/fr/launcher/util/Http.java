package fr.launcher.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.launcher.LauncherConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/** Client HTTP partagé (HTTP/2, connexions réutilisées) et helpers JSON. */
public final class Http {

    /** Identifie honnêtement le prototype auprès des API (Mojang, Fabric…), sans se faire passer pour le launcher officiel. */
    public static final String USER_AGENT = LauncherConfig.NAME + "-Prototype-Launcher/" + LauncherConfig.VERSION;

    public static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private Http() {
    }

    /** Erreur HTTP qui garde le corps de la réponse (utile pour lire les erreurs OAuth). */
    public static final class HttpException extends IOException {
        public final int status;
        public final String body;

        HttpException(int status, String body, URI uri) {
            super("HTTP " + status + " sur " + uri.getHost() + uri.getPath());
            this.status = status;
            this.body = body;
        }

        public JsonObject json() {
            try {
                return JsonParser.parseString(body).getAsJsonObject();
            } catch (RuntimeException e) {
                return new JsonObject();
            }
        }
    }

    public static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT);
    }

    public static JsonElement send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new HttpException(response.statusCode(), response.body(), request.uri());
        }
        return JsonParser.parseString(response.body());
    }

    /** Page texte (HTML…), avec un délai court : une page lente ne doit pas faire attendre l'interface. */
    public static String getText(String url) throws IOException, InterruptedException {
        HttpRequest request = request(url).timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new HttpException(response.statusCode(), "", request.uri());
        return response.body();
    }

    public static JsonElement get(String url) throws IOException, InterruptedException {
        return send(request(url).GET().build());
    }

    public static JsonObject getJson(String url) throws IOException, InterruptedException {
        return get(url).getAsJsonObject();
    }

    public static JsonObject getJson(String url, String bearer) throws IOException, InterruptedException {
        return send(request(url).header("Authorization", "Bearer " + bearer).GET().build()).getAsJsonObject();
    }

    public static JsonObject postJson(String url, JsonObject body) throws IOException, InterruptedException {
        return send(request(url)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build()).getAsJsonObject();
    }

    public static JsonObject postForm(String url, Map<String, String> form) throws IOException, InterruptedException {
        String encoded = form.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        return send(request(url)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encoded))
                .build()).getAsJsonObject();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
