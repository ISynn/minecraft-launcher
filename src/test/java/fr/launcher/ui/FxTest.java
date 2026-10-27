// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FxTest {

    @Test
    void taillesLisibles() {
        assertEquals("0 Ko", Fx.size(0));
        assertEquals("1 Ko", Fx.size(10));
        assertEquals("512 Ko", Fx.size(512 * 1024));
        assertEquals("475 Mo", Fx.size(475L * 1024 * 1024));
        assertEquals("1,5 Go", Fx.size(1536L * 1024 * 1024));
    }
}
