// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.log;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogTest {

    @Test
    void masqueUnJwt() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
        assertEquals("jeton reçu : <masqué> fin", Log.redact("jeton reçu : " + jwt + " fin"));
    }

    @Test
    void masqueLesValeursNommees() {
        String out = Log.redact("{\"access_token\":\"abc123\",\"refresh_token\": \"def456\",\"name\":\"Steve\"}");
        assertFalse(out.contains("abc123"));
        assertFalse(out.contains("def456"));
        assertTrue(out.contains("\"name\":\"Steve\""), "les champs ordinaires restent lisibles");
    }

    @Test
    void masqueLaLigneDeCommandeDuJeu() {
        String out = Log.redact("java -cp x net.minecraft.Main --username Steve --accessToken 0123secret --version 26.3");
        assertFalse(out.contains("0123secret"));
        assertTrue(out.contains("--username Steve"));
        assertTrue(out.contains("--version 26.3"));
    }

    @Test
    void masqueLesEnTetesEtIdentitesXbox() {
        assertFalse(Log.redact("Authorization: Bearer tok.en-123").contains("tok.en-123"));
        assertFalse(Log.redact("identityToken=XBL3.0 x=uhs;xsts").contains("uhs;xsts"));
    }

    @Test
    void laisseLeTexteOrdinaireIntact() {
        String text = "Téléchargement de 4770 fichiers (1452 Mo) depuis resources.download.minecraft.net";
        assertEquals(text, Log.redact(text));
    }
}
