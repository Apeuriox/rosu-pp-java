package me.aloic.rosupp;

/** Stable algorithm identifiers. These values are part of cache and serialization keys. */
public enum AlgorithmVersion {
    PRECSR_202210(20_221_000, "precsr-202210-rosu-pp-1.0.0"),
    REWORK_202411(20_241_100, "rework-202411-rosu-pp-2.0.0"),
    REWORK_202502(20_250_200, "rework-202502-rosu-pp-3.1.0"),
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
