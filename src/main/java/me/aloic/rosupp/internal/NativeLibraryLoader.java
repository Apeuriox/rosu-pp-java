package me.aloic.rosupp.internal;

import me.aloic.rosupp.NativeLibraryLoadException;

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NativeLibraryLoader {
    private NativeLibraryLoader() {}

    public static SymbolLookup load() {
        Platform platform = currentPlatform();
        String explicit = System.getProperty("rosu.pp.native.path");
        if (explicit == null || explicit.isBlank()) explicit = System.getenv("ROSU_PP_NATIVE_PATH");
        List<Path> candidates = new ArrayList<>();
        if (explicit != null && !explicit.isBlank()) candidates.add(Path.of(explicit));
        String file = platform.fileName();
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
        String resource = "/META-INF/native/" + platform.resourceDirectory() + "/" + file;
        try (InputStream input = NativeLibraryLoader.class.getResourceAsStream(resource)) {
            if (input != null) {
                Path directory = Files.createTempDirectory("rosu-pp-java-");
                Path extracted = directory.resolve(file);
                Files.copy(input, extracted, StandardCopyOption.REPLACE_EXISTING);
                extracted.toFile().deleteOnExit();
                directory.toFile().deleteOnExit();
                try {
                    return SymbolLookup.libraryLookup(extracted, Arena.global());
                } catch (RuntimeException | UnsatisfiedLinkError error) {
                    throw new NativeLibraryLoadException(
                            "Failed to load bundled native bridge extracted from " + resource + " to " + extracted,
                            error);
                }
            }
        } catch (IOException error) {
            throw new NativeLibraryLoadException("Failed to extract bundled native bridge " + resource, error);
        }
        throw new NativeLibraryLoadException(
                "Native bridge not found. Build `cargo build --manifest-path native/Cargo.toml --release` " +
                        "or set -Drosu.pp.native.path=<absolute library path>. Checked files: " + candidates +
                        "; checked classpath resource: " + resource,
                new java.io.FileNotFoundException(file));
    }

    private static Platform currentPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!architecture.equals("amd64") && !architecture.equals("x86_64")) {
            throw unsupportedPlatform(os, architecture);
        }
        if (os.contains("win")) return new Platform("windows-x86_64", "rosu_pp_ffi.dll");
        if (os.contains("linux")) return new Platform("linux-x86_64", "librosu_pp_ffi.so");
        throw unsupportedPlatform(os, architecture);
    }

    private static NativeLibraryLoadException unsupportedPlatform(String os, String architecture) {
        String message = "Unsupported native platform: os.name=" + os + ", os.arch=" + architecture;
        return new NativeLibraryLoadException(message, new UnsupportedOperationException(message));
    }

    private record Platform(String resourceDirectory, String fileName) {}
}
