package me.aloic.rosupp;

import java.util.OptionalDouble;

/** Superset of difficulty attributes. Consult {@link #present()} before using optional fields. */
public record DifficultyResult(
        AlgorithmVersion algorithmId,
        String algorithmName,
        String algorithmVersion,
        AlgorithmCapabilities capabilities,
        GameMode mode,
        long present,
        double stars,
        double aim,
        double speed,
        double flashlight,
        double reading,
        double sliderFactor,
        double aimDifficultSliderCount,
        double speedNoteCount,
        double readingDifficultNoteCount,
        double aimTopWeightedSliderFactor,
        double speedTopWeightedSliderFactor,
        double aimDifficultStrainCount,
        double speedDifficultStrainCount,
        double nestedScorePerObject,
        double legacyScoreBaseMultiplier,
        double maximumLegacyComboScore,
        double stamina,
        double rhythm,
        double color,
        double taikoReading,
        double monoStaminaFactor,
        double mechanicalDifficulty,
        double consistencyFactor,
        double catchPreempt,
        double ar,
        double od,
        double hp,
        double greatHitWindow,
        double okHitWindow,
        double mehHitWindow,
        int maxCombo,
        int objectCount,
        int circleCount,
        int sliderCount,
        int spinnerCount,
        int fruitCount,
        int dropletCount,
        int tinyDropletCount,
        int holdNoteCount,
        boolean convert) {

    public static final long AIM = 1L;
    public static final long SPEED = 1L << 1;
    public static final long FLASHLIGHT = 1L << 2;
    public static final long READING = 1L << 3;
    public static final long SLIDER_FACTOR = 1L << 4;
    public static final long AIM_DIFFICULT_SLIDER_COUNT = 1L << 5;
    public static final long SPEED_NOTE_COUNT = 1L << 6;
    public static final long READING_DIFFICULT_NOTE_COUNT = 1L << 7;
    public static final long AR = 1L << 8;
    public static final long OD = 1L << 9;
    public static final long HP = 1L << 10;
    public static final long HIT_WINDOWS = 1L << 11;
    public static final long OBJECT_COUNTS = 1L << 12;
    public static final long IS_CONVERT = 1L << 13;
    public static final long TAIKO_SKILLS = 1L << 25;
    public static final long CATCH_PREEMPT = 1L << 26;
    public static final long LEGACY_SCORE = 1L << 27;

    public boolean has(long field) { return (present & field) != 0; }
    public boolean hasReading() { return has(READING); }
    public OptionalDouble readingOptional() { return hasReading() ? OptionalDouble.of(reading) : OptionalDouble.empty(); }
    public OptionalDouble arOptional() { return has(AR) ? OptionalDouble.of(ar) : OptionalDouble.empty(); }
    public OptionalDouble odOptional() { return has(OD) ? OptionalDouble.of(od) : OptionalDouble.empty(); }
    public OptionalDouble hpOptional() { return has(HP) ? OptionalDouble.of(hp) : OptionalDouble.empty(); }

    @Override
    public String toString()
    {
        return "DifficultyResult{" +
                "algorithmId=" + algorithmId +
                ", algorithmName='" + algorithmName + '\'' +
                ", algorithmVersion='" + algorithmVersion + '\'' +
                ", capabilities=" + capabilities +
                ", mode=" + mode +
                ", present=" + present +
                ", stars=" + stars +
                ", aim=" + aim +
                ", speed=" + speed +
                ", flashlight=" + flashlight +
                ", reading=" + reading +
                ", sliderFactor=" + sliderFactor +
                ", aimDifficultSliderCount=" + aimDifficultSliderCount +
                ", speedNoteCount=" + speedNoteCount +
                ", readingDifficultNoteCount=" + readingDifficultNoteCount +
                ", aimTopWeightedSliderFactor=" + aimTopWeightedSliderFactor +
                ", speedTopWeightedSliderFactor=" + speedTopWeightedSliderFactor +
                ", aimDifficultStrainCount=" + aimDifficultStrainCount +
                ", speedDifficultStrainCount=" + speedDifficultStrainCount +
                ", nestedScorePerObject=" + nestedScorePerObject +
                ", legacyScoreBaseMultiplier=" + legacyScoreBaseMultiplier +
                ", maximumLegacyComboScore=" + maximumLegacyComboScore +
                ", stamina=" + stamina +
                ", rhythm=" + rhythm +
                ", color=" + color +
                ", taikoReading=" + taikoReading +
                ", monoStaminaFactor=" + monoStaminaFactor +
                ", mechanicalDifficulty=" + mechanicalDifficulty +
                ", consistencyFactor=" + consistencyFactor +
                ", catchPreempt=" + catchPreempt +
                ", ar=" + ar +
                ", od=" + od +
                ", hp=" + hp +
                ", greatHitWindow=" + greatHitWindow +
                ", okHitWindow=" + okHitWindow +
                ", mehHitWindow=" + mehHitWindow +
                ", maxCombo=" + maxCombo +
                ", objectCount=" + objectCount +
                ", circleCount=" + circleCount +
                ", sliderCount=" + sliderCount +
                ", spinnerCount=" + spinnerCount +
                ", fruitCount=" + fruitCount +
                ", dropletCount=" + dropletCount +
                ", tinyDropletCount=" + tinyDropletCount +
                ", holdNoteCount=" + holdNoteCount +
                ", convert=" + convert +
                '}';
    }
}
