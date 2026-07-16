package me.aloic.rosupp;

public record ScoreState(
        int maxCombo, int largeTickHits, int smallTickHits, int sliderEndHits,
        int nGeki, int nKatu, int n300, int n100, int n50, int misses,
        Integer legacyTotalScore) {
    public ScoreState {
        int[] values = {maxCombo, largeTickHits, smallTickHits, sliderEndHits, nGeki, nKatu, n300, n100, n50, misses};
        for (int value : values) if (value < 0) throw new IllegalArgumentException("Score state values must be non-negative");
        if (legacyTotalScore != null && legacyTotalScore < 0) throw new IllegalArgumentException("legacyTotalScore must be non-negative");
    }
    public static ScoreState empty() { return new ScoreState(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null); }
}
