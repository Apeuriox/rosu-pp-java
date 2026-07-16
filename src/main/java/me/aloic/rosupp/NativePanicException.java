package me.aloic.rosupp;

public final class NativePanicException extends RosuPpException {
    public NativePanicException(String message) { super(-127, message); }
}
