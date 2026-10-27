// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Un compte joueur.
 *
 * @param accessToken  jeton Minecraft (valable ~24 h)
 * @param refreshToken jeton Microsoft qui permet de se reconnecter sans redemander le mot de passe
 * @param expiresAt    expiration du jeton Minecraft (epoch millis)
 */
public record Account(Type type, String uuid, String name, String accessToken, String refreshToken, long expiresAt) {

    public enum Type { MICROSOFT, OFFLINE }

    /** Compte local pour tester sans Microsoft (mode développeur uniquement). */
    public static Account offline(String name) {
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return new Account(Type.OFFLINE, uuid.toString().replace("-", ""), name, "0", null, Long.MAX_VALUE);
    }

    public boolean needsRefresh() {
        return type == Type.MICROSOFT && System.currentTimeMillis() > expiresAt - 5 * 60_000;
    }

    public String userType() {
        return type == Type.MICROSOFT ? "msa" : "legacy";
    }
}
