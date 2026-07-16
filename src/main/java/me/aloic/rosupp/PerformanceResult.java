package me.aloic.rosupp;

import java.util.OptionalDouble;

public record PerformanceResult(
        AlgorithmVersion algorithmId,
        String algorithmName,
        String algorithmVersion,
        AlgorithmCapabilities capabilities,
        GameMode mode,
        long present,
        double pp,
        double ppAim,
        double ppSpeed,
        double ppAccuracy,
        double ppFlashlight,
        double ppReading,
        double ppDifficulty,
        double effectiveMissCount,
        double speedDeviation,
        double estimatedUnstableRate,
        double comboBasedEstimatedMissCount,
        double scoreBasedEstimatedMissCount,
        double aimEstimatedSliderBreaks,
        double speedEstimatedSliderBreaks,
        DifficultyResult difficulty) {
    public static final long PP_AIM = 1L << 16;
    public static final long PP_SPEED = 1L << 17;
    public static final long PP_ACCURACY = 1L << 18;
    public static final long PP_FLASHLIGHT = 1L << 19;
    public static final long PP_READING = 1L << 20;
    public static final long PP_DIFFICULTY = 1L << 21;
    public static final long EFFECTIVE_MISS_COUNT = 1L << 22;
    public static final long SPEED_DEVIATION = 1L << 23;
    public static final long ESTIMATED_UNSTABLE_RATE = 1L << 24;
    public static final long COMBO_BASED_ESTIMATED_MISS_COUNT = 1L << 28;
    public static final long SCORE_BASED_ESTIMATED_MISS_COUNT = 1L << 29;
    public static final long AIM_ESTIMATED_SLIDER_BREAKS = 1L << 30;
    public static final long SPEED_ESTIMATED_SLIDER_BREAKS = 1L << 31;

    public boolean has(long field) { return (present & field) != 0; }
    public boolean hasReadingPerformance() { return has(PP_READING); }
    public OptionalDouble readingPerformanceOptional() {
        return hasReadingPerformance() ? OptionalDouble.of(ppReading) : OptionalDouble.empty();
    }
    public OptionalDouble speedDeviationOptional() {
        return has(SPEED_DEVIATION) ? OptionalDouble.of(speedDeviation) : OptionalDouble.empty();
    }
    public OptionalDouble estimatedUnstableRateOptional() {
        return has(ESTIMATED_UNSTABLE_RATE) ? OptionalDouble.of(estimatedUnstableRate) : OptionalDouble.empty();
    }

    @Override
    public String toString()
    {
        return "PerformanceResult{" +
                "algorithmId=" + algorithmId +
                ", algorithmName='" + algorithmName + '\'' +
                ", algorithmVersion='" + algorithmVersion + '\'' +
                ", capabilities=" + capabilities +
                ", mode=" + mode +
                ", present=" + present +
                ", pp=" + pp +
                ", ppAim=" + ppAim +
                ", ppSpeed=" + ppSpeed +
                ", ppAccuracy=" + ppAccuracy +
                ", ppFlashlight=" + ppFlashlight +
                ", ppReading=" + ppReading +
                ", ppDifficulty=" + ppDifficulty +
                ", effectiveMissCount=" + effectiveMissCount +
                ", speedDeviation=" + speedDeviation +
                ", estimatedUnstableRate=" + estimatedUnstableRate +
                ", comboBasedEstimatedMissCount=" + comboBasedEstimatedMissCount +
                ", scoreBasedEstimatedMissCount=" + scoreBasedEstimatedMissCount +
                ", aimEstimatedSliderBreaks=" + aimEstimatedSliderBreaks +
                ", speedEstimatedSliderBreaks=" + speedEstimatedSliderBreaks +
                ", difficulty=" + difficulty +
                '}';
    }
}
