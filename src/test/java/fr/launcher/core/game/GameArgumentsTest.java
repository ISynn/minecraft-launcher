// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.core.game;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameArgumentsTest {

    @Test
    void cheminMavenSimpleEtAvecClassifier() {
        assertEquals("net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar",
                VersionResolver.mavenPath("net.fabricmc:fabric-loader:0.20.1"));
        assertEquals("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar",
                VersionResolver.mavenPath("org.lwjgl:lwjgl:3.3.3:natives-windows"));
    }

    @Test
    void substitutionDesVariables() {
        Map<String, String> vars = Map.of("auth_player_name", "Steve", "game_directory", "C:\\Jeu $1");
        assertEquals("Steve", GameLauncher.substitute("${auth_player_name}", vars));
        assertEquals("--gameDir=C:\\Jeu $1", GameLauncher.substitute("--gameDir=${game_directory}", vars),
                "les caractères spéciaux des chemins ne doivent pas être interprétés");
    }

    @Test
    void variableInconnueLaisseeTelleQuelle() {
        assertEquals("${resolution_width}", GameLauncher.substitute("${resolution_width}", Map.of()));
    }
}
