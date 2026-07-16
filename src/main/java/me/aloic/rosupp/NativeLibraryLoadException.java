package me.aloic.rosupp;

public final class NativeLibraryLoadException extends RosuPpException {
    public NativeLibraryLoadException(String message, Throwable cause) {
        super(-1, message);
        initCause(cause);
    }
}
