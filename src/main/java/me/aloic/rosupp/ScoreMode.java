package me.aloic.rosupp;

public enum ScoreMode {
    DEFAULT(0), STABLE(1), LAZER(2);
    private final int nativeId;
    ScoreMode(int nativeId) { this.nativeId = nativeId; }
    public int nativeId() { return nativeId; }
}
