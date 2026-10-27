// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import fr.launcher.util.Os;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesTest {

    private static JsonElement json(String s) {
        return JsonParser.parseString(s);
    }

    @Test
    void sansRegleToutEstAutorise() {
        assertTrue(Rules.allowed(null, Map.of()));
    }

    @Test
    void regleSurLeSystemeCourant() {
        String os = Os.CURRENT.mojangName;
        assertTrue(Rules.allowed(json("[{\"action\":\"allow\",\"os\":{\"name\":\"" + os + "\"}}]"), Map.of()));
        String other = Os.CURRENT == Os.WINDOWS ? "linux" : "windows";
        assertFalse(Rules.allowed(json("[{\"action\":\"allow\",\"os\":{\"name\":\"" + other + "\"}}]"), Map.of()));
    }

    @Test
    void laDerniereRegleQuiCorrespondGagne() {
        String os = Os.CURRENT.mojangName;
        String rules = "[{\"action\":\"allow\"},{\"action\":\"disallow\",\"os\":{\"name\":\"" + os + "\"}}]";
        assertFalse(Rules.allowed(json(rules), Map.of()));
    }

    @Test
    void reglesDeFonctionnalites() {
        JsonElement quickPlay = json("[{\"action\":\"allow\",\"features\":{\"is_quick_play_multiplayer\":true}}]");
        assertTrue(Rules.allowed(quickPlay, Map.of("is_quick_play_multiplayer", true)));
        assertFalse(Rules.allowed(quickPlay, Map.of("is_quick_play_multiplayer", false)));
        assertFalse(Rules.allowed(quickPlay, Map.of()), "une fonctionnalité absente vaut false");
    }
}
