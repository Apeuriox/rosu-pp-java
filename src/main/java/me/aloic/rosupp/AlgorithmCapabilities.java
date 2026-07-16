package me.aloic.rosupp;

public record AlgorithmCapabilities(long bits) {
    public static final long DIFFICULTY = 1L;
    public static final long PERFORMANCE = 1L << 1;
    public static final long GRADUAL_DIFFICULTY = 1L << 2;
    public static final long GRADUAL_PERFORMANCE = 1L << 3;
    public static final long READING_SKILL = 1L << 4;
    public static final long LAZER_SLIDER_ACCURACY = 1L << 5;
    public static final long STRUCTURED_MODS = 1L << 6;

    public boolean has(long capability) { return (bits & capability) != 0; }
    public boolean difficulty() { return has(DIFFICULTY); }
    public boolean performance() { return has(PERFORMANCE); }
    public boolean gradualDifficulty() { return has(GRADUAL_DIFFICULTY); }
    public boolean gradualPerformance() { return has(GRADUAL_PERFORMANCE); }
    public boolean readingSkill() { return has(READING_SKILL); }
    public boolean lazerSliderAccuracy() { return has(LAZER_SLIDER_ACCURACY); }
    public boolean structuredMods() { return has(STRUCTURED_MODS); }
}
