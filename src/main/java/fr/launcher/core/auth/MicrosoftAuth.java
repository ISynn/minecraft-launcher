// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.launcher.LauncherConfig;
import fr.launcher.util.Http;
import fr.launcher.util.Json;

import java.io.IOException;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Connexion Microsoft → Xbox Live → XSTS → Minecraft.
 * Utilise le « device code flow » : l'utilisateur entre un code sur microsoft.com/link,
 * le launcher n'a jamais accès à son mot de passe.
 */
public final class MicrosoftAuth {

    private static final String MS_OAUTH = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";
    private static final String SCOPE = "XboxLive.signin offline_access";

    public record DeviceCode(String userCode, String verificationUri, String deviceCode, int interval, int expiresIn) {
    }

    public static boolean isConfigured() {
        return !LauncherConfig.AZURE_CLIENT_ID.isBlank();
    }

    /** Étape 1 : demande un code à afficher à l'utilisateur. */
    public DeviceCode requestDeviceCode() throws IOException, InterruptedException {
        JsonObject r = Http.postForm(MS_OAUTH + "devicecode", Map.of(
                "client_id", LauncherConfig.AZURE_CLIENT_ID,
                "scope", SCOPE));
        return new DeviceCode(
                r.get("user_code").getAsString(),
                r.get("verification_uri").getAsString(),
                r.get("device_code").getAsString(),
                r.get("interval").getAsInt(),
                r.get("expires_in").getAsInt());
    }

    /** Étape 2 : attend que l'utilisateur ait validé le code, puis termine la connexion. */
    public Account waitForLogin(DeviceCode code, BooleanSupplier cancelled) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + code.expiresIn() * 1000L;
        long interval = code.interval() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(interval);
            if (cancelled.getAsBoolean()) throw new InterruptedException("Connexion annulée");
            try {
                JsonObject token = Http.postForm(MS_OAUTH + "token", Map.of(
                        "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                        "client_id", LauncherConfig.AZURE_CLIENT_ID,
                        "device_code", code.deviceCode()));
                return loginWithMicrosoft(token);
            } catch (Http.HttpException e) {
                String error = Json.string(e.json(), "error", "");
                switch (error) {
                    case "authorization_pending" -> { }
                    case "slow_down" -> interval += 5000;
                    case "authorization_declined" -> throw new AuthException("Connexion refusée.");
                    case "expired_token" -> throw new AuthException("Le code a expiré, recommence.");
                    default -> throw e;
                }
            }
        }
        throw new AuthException("Le code a expiré, recommence.");
    }

    /** Reconnexion silencieuse grâce au refresh token. */
    public Account refresh(Account account) throws IOException, InterruptedException {
        if (account.type() != Account.Type.MICROSOFT) return account;
        JsonObject token = Http.postForm(MS_OAUTH + "token", Map.of(
                "grant_type", "refresh_token",
                "client_id", LauncherConfig.AZURE_CLIENT_ID,
                "refresh_token", account.refreshToken(),
                "scope", SCOPE));
        return loginWithMicrosoft(token);
    }

    private Account loginWithMicrosoft(JsonObject msToken) throws IOException, InterruptedException {
        String msAccess = msToken.get("access_token").getAsString();
        String msRefresh = msToken.get("refresh_token").getAsString();

        // Xbox Live
        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProps.addProperty("RpsTicket", "d=" + msAccess);
        JsonObject xblBody = new JsonObject();
        xblBody.add("Properties", xblProps);
        xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblBody.addProperty("TokenType", "JWT");
        JsonObject xbl = Http.postJson("https://user.auth.xboxlive.com/user/authenticate", xblBody);
        String xblToken = xbl.get("Token").getAsString();

        // XSTS
        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        xstsProps.add("UserTokens", userTokens);
        JsonObject xstsBody = new JsonObject();
        xstsBody.add("Properties", xstsProps);
        xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.addProperty("TokenType", "JWT");
        JsonObject xsts;
        try {
            xsts = Http.postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody);
        } catch (Http.HttpException e) {
            throw new AuthException(xstsError(Json.number(e.json(), "XErr", 0)));
        }
        String xstsToken = xsts.get("Token").getAsString();
        String uhs = xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui")
                .get(0).getAsJsonObject().get("uhs").getAsString();

        // Minecraft
        JsonObject mcBody = new JsonObject();
        mcBody.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        JsonObject mc;
        try {
            mc = Http.postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcBody);
        } catch (Http.HttpException e) {
            if (e.status == 403) {
                throw new AuthException("Application Azure pas encore autorisée par Mojang (voir README).");
            }
            throw e;
        }
        String mcToken = mc.get("access_token").getAsString();
        long expiresAt = System.currentTimeMillis() + mc.get("expires_in").getAsLong() * 1000L;

        JsonObject profile;
        try {
            profile = Http.getJson("https://api.minecraftservices.com/minecraft/profile", mcToken);
        } catch (Http.HttpException e) {
            if (e.status == 404) throw new AuthException("Ce compte ne possède pas Minecraft Java Edition.");
            throw e;
        }
        return new Account(Account.Type.MICROSOFT,
                profile.get("id").getAsString(),
                profile.get("name").getAsString(),
                mcToken, msRefresh, expiresAt);
    }

    private static String xstsError(long code) {
        if (code == 2148916233L) return "Ce compte Microsoft n'a pas de profil Xbox. Crée-le sur xbox.com puis réessaie.";
        if (code == 2148916235L) return "Xbox Live n'est pas disponible dans ton pays.";
        if (code == 2148916236L || code == 2148916237L) return "Ce compte doit être vérifié comme adulte sur xbox.com.";
        if (code == 2148916238L) return "Compte enfant : il doit être ajouté à une famille Microsoft par un adulte.";
        return "Erreur Xbox (" + code + ").";
    }

    public static final class AuthException extends IOException {
        public AuthException(String message) {
            super(message);
        }
    }
}
