package me.aloic.rosupp;

/** Stable algorithm identifiers. These values are part of cache and serialization keys. */
public enum AlgorithmVersion {
    REWORK_202510(20_251_000, "rework-202510"),
    REWORK_20260706(20_260_706, "rework-20260706-9a073d2");

    private final int nativeId;
    private final String stableKey;

    AlgorithmVersion(int nativeId, String stableKey) {
        this.nativeId = nativeId;
        this.stableKey = stableKey;
    }

    public int nativeId() { return nativeId; }
    public String stableKey() { return stableKey; }

    public static AlgorithmVersion fromNativeId(int id) {
        for (var value : values()) if (value.nativeId == id) return value;
        throw new IllegalArgumentException("Unknown native algorithm id: " + id);
    }
}
