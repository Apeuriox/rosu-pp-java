package me.aloic.rosupp;

public class RosuPpException extends RuntimeException {
    private final int status;
    public RosuPpException(int status, String message) {
        super(message);
        this.status = status;
    }
    public int status() { return status; }
}
