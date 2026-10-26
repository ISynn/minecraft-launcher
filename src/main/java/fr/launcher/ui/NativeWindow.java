package fr.launcher.ui;

import javafx.stage.Stage;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Coins arrondis et ombre natifs de Windows 11, via l'API Foreign Function de Java (sans bibliothèque externe).
 *
 * <p>Pourquoi : une fenêtre JavaFX transparente est recomposée par le processeur à chaque image (~3× plus de CPU).
 * Avec une fenêtre opaque, c'est Windows (DWM) qui dessine les coins et l'ombre, gratuitement, sur le GPU.</p>
 */
public final class NativeWindow {

    /** Windows 11 uniquement : les coins arrondis DWM n'existent pas sur Windows 10. */
    public static final boolean SUPPORTED = "Windows 11".equals(System.getProperty("os.name"));

    private static final int DWMWA_WINDOW_CORNER_PREFERENCE = 33;
    private static final int DWMWA_BORDER_COLOR = 34;
    private static final int DWMWCP_ROUND = 2;
    /** Bordure fine assortie au thème (format COLORREF : 0x00BBGGRR). */
    private static final int BORDER_COLOR = 0x00302a26;

    private NativeWindow() {
    }

    /** À appeler une fois la fenêtre affichée. Renvoie false si Windows a refusé (la fenêtre reste carrée). */
    // Appels natifs volontaires, autorisés au lancement par --enable-native-access=fr.launcher.
    @SuppressWarnings("restricted")
    public static boolean decorate(Stage stage) {
        if (!SUPPORTED) return false;
        String title = stage.getTitle();
        // On retrouve la fenêtre native grâce à un titre unique temporaire.
        String marker = "launcher-" + UUID.randomUUID();
        stage.setTitle(marker);
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            MethodHandle findWindow = linker.downcallHandle(
                    SymbolLookup.libraryLookup("user32", arena).find("FindWindowW").orElseThrow(),
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
            MethodHandle setAttribute = linker.downcallHandle(
                    SymbolLookup.libraryLookup("dwmapi", arena).find("DwmSetWindowAttribute").orElseThrow(),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

            MemorySegment hwnd = (MemorySegment) findWindow.invokeExact(
                    MemorySegment.NULL, arena.allocateFrom(marker, StandardCharsets.UTF_16LE));
            if (hwnd.address() == 0) return false;

            int corners = (int) setAttribute.invokeExact(
                    hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, arena.allocateFrom(JAVA_INT, DWMWCP_ROUND), 4);
            int border = (int) setAttribute.invokeExact(
                    hwnd, DWMWA_BORDER_COLOR, arena.allocateFrom(JAVA_INT, BORDER_COLOR), 4);
            return corners == 0 && border == 0;
        } catch (Throwable t) {
            return false;
        } finally {
            stage.setTitle(title);
        }
    }
}
