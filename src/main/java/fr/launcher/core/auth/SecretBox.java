package fr.launcher.core.auth;

import fr.launcher.util.Os;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Chiffrement des données sensibles (jetons de session) avec la DPAPI de Windows.
 *
 * <p>Le résultat n'est déchiffrable que par le même utilisateur Windows, sur la même machine : copier le
 * fichier ailleurs ne donne rien. Appel natif direct (API Foreign Function), sans bibliothèque externe.</p>
 */
final class SecretBox {

    /** DPAPI disponible : Windows uniquement. Ailleurs, le fichier est protégé par ses permissions. */
    static final boolean SUPPORTED = Os.CURRENT == Os.WINDOWS;

    private static final int CRYPTPROTECT_UI_FORBIDDEN = 0x1;
    /** Sel propre au prototype : un autre programme de l'utilisateur ne peut pas déchiffrer sans le connaître. */
    private static final byte[] ENTROPY = "PokeIsland-Prototype-Launcher/compte".getBytes(StandardCharsets.UTF_8);

    /** DATA_BLOB { DWORD cbData; BYTE* pbData; } (alignement 64 bits : 4 octets de remplissage). */
    private static final StructLayout BLOB = MemoryLayout.structLayout(
            JAVA_INT.withName("cbData"), MemoryLayout.paddingLayout(4), ADDRESS.withName("pbData"));

    private SecretBox() {
    }

    static byte[] protect(byte[] data) throws IOException {
        return call(true, data);
    }

    static byte[] unprotect(byte[] data) throws IOException {
        return call(false, data);
    }

    // Appels natifs volontaires, autorisés au lancement par --enable-native-access=fr.launcher.
    @SuppressWarnings("restricted")
    private static byte[] call(boolean protect, byte[] data) throws IOException {
        if (!SUPPORTED) throw new IOException("Chiffrement DPAPI indisponible sur ce système");
        if (data.length == 0) throw new IOException("Rien à chiffrer");
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            MethodHandle crypt = linker.downcallHandle(
                    SymbolLookup.libraryLookup("crypt32", arena)
                            .find(protect ? "CryptProtectData" : "CryptUnprotectData").orElseThrow(),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
            MethodHandle localFree = linker.downcallHandle(
                    SymbolLookup.libraryLookup("kernel32", arena).find("LocalFree").orElseThrow(),
                    FunctionDescriptor.of(ADDRESS, ADDRESS));

            MemorySegment input = blob(arena, data);
            MemorySegment entropy = blob(arena, ENTROPY);
            MemorySegment output = arena.allocate(BLOB);
            int ok = (int) crypt.invokeExact(input, MemorySegment.NULL, entropy, MemorySegment.NULL,
                    MemorySegment.NULL, CRYPTPROTECT_UI_FORBIDDEN, output);
            if (ok == 0) {
                throw new IOException(protect ? "Chiffrement refusé par Windows"
                        : "Déchiffrement impossible (fichier abîmé, ou créé par un autre utilisateur ou un autre PC)");
            }
            int size = output.get(JAVA_INT, 0);
            MemorySegment result = output.get(ADDRESS, 8).reinterpret(size);
            try {
                return result.toArray(JAVA_BYTE);
            } finally {
                // Windows a alloué le résultat : on l'efface puis on le rend.
                result.fill((byte) 0);
                MemorySegment ignored = (MemorySegment) localFree.invokeExact(result);
            }
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("Appel DPAPI impossible : " + t.getMessage(), t);
        }
    }

    private static MemorySegment blob(Arena arena, byte[] bytes) {
        MemorySegment blob = arena.allocate(BLOB);
        blob.set(JAVA_INT, 0, bytes.length);
        blob.set(ADDRESS, 8, arena.allocateFrom(JAVA_BYTE, bytes));
        return blob;
    }
}
