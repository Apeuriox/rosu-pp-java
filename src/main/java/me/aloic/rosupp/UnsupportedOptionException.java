package me.aloic.rosupp;

public final class UnsupportedOptionException extends RosuPpException {
    public UnsupportedOptionException(String message) { super(-6, message); }
}
