package me.aloic.rosupp;

public enum GameMode {
    OSU(0), TAIKO(1), CATCH(2), MANIA(3);
    private final int nativeId;
    GameMode(int nativeId) { this.nativeId = nativeId; }
    public int nativeId() { return nativeId; }
    public static GameMode fromNativeId(int id) {
        for (var value : values()) if (value.nativeId == id) return value;
        throw new IllegalArgumentException("Unknown game mode: " + id);
    }
}
