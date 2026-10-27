// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Interroge un serveur Minecraft avec le protocole « Server List Ping » (comme la liste multijoueur du jeu). */
public final class ServerPinger {

    private static final int TIMEOUT_MS = 3000;

    public record Status(boolean online, int players, int maxPlayers, long pingMs, String motd, String version) {
        public static final Status OFFLINE = new Status(false, 0, 0, -1, "", "");
    }

    private ServerPinger() {
    }

    public static Status ping(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Handshake (état suivant = 1 : statut)
            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(handshake);
            writeVarInt(h, 0x00);
            writeVarInt(h, -1);
            writeString(h, host);
            h.writeShort(port);
            writeVarInt(h, 1);
            writePacket(out, handshake.toByteArray());
            writePacket(out, new byte[]{0x00});

            readVarInt(in); // taille du paquet
            if (readVarInt(in) != 0x00) throw new IOException("Réponse inattendue");
            byte[] json = new byte[readVarInt(in)];
            in.readFully(json);

            // Ping/pong pour mesurer la latence réelle
            long start = System.nanoTime();
            ByteArrayOutputStream ping = new ByteArrayOutputStream();
            DataOutputStream p = new DataOutputStream(ping);
            writeVarInt(p, 0x01);
            p.writeLong(start);
            writePacket(out, ping.toByteArray());
            readVarInt(in);
            readVarInt(in);
            in.readLong();
            long pingMs = (System.nanoTime() - start) / 1_000_000;

            JsonObject status = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject players = status.getAsJsonObject("players");
            JsonObject version = status.getAsJsonObject("version");
            return new Status(true,
                    players != null ? players.get("online").getAsInt() : 0,
                    players != null ? players.get("max").getAsInt() : 0,
                    pingMs,
                    plainText(status.get("description")),
                    version != null ? version.get("name").getAsString() : "");
        } catch (IOException | RuntimeException e) {
            return Status.OFFLINE;
        }
    }

    /** Le MOTD peut être une chaîne ou un composant de texte JSON : on garde le texte brut. */
    private static String plainText(JsonElement component) {
        if (component == null || component.isJsonNull()) return "";
        if (component.isJsonPrimitive()) return component.getAsString().replaceAll("§.", "");
        StringBuilder sb = new StringBuilder();
        if (component.isJsonObject()) {
            JsonObject o = component.getAsJsonObject();
            if (o.has("text")) sb.append(plainText(o.get("text")));
            if (o.has("extra")) o.getAsJsonArray("extra").forEach(e -> sb.append(plainText(e)));
        } else if (component.isJsonArray()) {
            component.getAsJsonArray().forEach(e -> sb.append(plainText(e)));
        }
        return sb.toString();
    }

    private static void writePacket(DataOutputStream out, byte[] data) throws IOException {
        writeVarInt(out, data.length);
        out.write(data);
        out.flush();
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            byte b = in.readByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        throw new IOException("VarInt trop long");
    }
}
