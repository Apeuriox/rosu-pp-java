package me.aloic.rosupp.internal;

import me.aloic.rosupp.NativeLibraryLoadException;

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class NativeLibraryLoader {
    private NativeLibraryLoader() {}

    public static SymbolLookup load() {
        String explicit = System.getProperty("rosu.pp.native.path");
        if (explicit == null || explicit.isBlank()) explicit = System.getenv("ROSU_PP_NATIVE_PATH");
        List<Path> candidates = new ArrayList<>();
        if (explicit != null && !explicit.isBlank()) candidates.add(Path.of(explicit));
        String file = isWindows() ? "rosu_pp_ffi.dll" : "librosu_pp_ffi.so";
        candidates.add(Path.of("native", "target", "release", file));
        candidates.add(Path.of("target", "native", file));
        candidates.add(Path.of(file));

        for (Path candidate : candidates) {
            Path absolute = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(absolute)) {
                try {
                    return SymbolLookup.libraryLookup(absolute, Arena.global());
                } catch (RuntimeException | UnsatisfiedLinkError error) {
                    throw new NativeLibraryLoadException("Failed to load native bridge: " + absolute, error);
                }
            }
        }
        throw new NativeLibraryLoadException(
                "Native bridge not found. Build `cargo build --manifest-path native/Cargo.toml --release` " +
                        "or set -Drosu.pp.native.path=<absolute library path>. Checked: " + candidates,
                new java.io.FileNotFoundException(file));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}
