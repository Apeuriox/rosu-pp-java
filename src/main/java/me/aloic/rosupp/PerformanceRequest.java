package me.aloic.rosupp;

public final class PerformanceRequest {
    static final long ACCURACY = 1L;
    static final long COMBO = 1L << 1;
    static final long MISSES = 1L << 2;
    static final long N300 = 1L << 3;
    static final long N100 = 1L << 4;
    static final long N50 = 1L << 5;
    static final long N_GEKI = 1L << 6;
    static final long N_KATU = 1L << 7;
    static final long LARGE_TICK_HITS = 1L << 8;
    static final long SMALL_TICK_HITS = 1L << 9;
    static final long SLIDER_END_HITS = 1L << 10;
    public static final long LEGACY_TOTAL_SCORE = 1L << 11;

    private final DifficultyRequest difficulty;
    private final long scoreFields;
    private final double accuracy;
    private final int combo, misses, n300, n100, n50, nGeki, nKatu;
    private final int largeTickHits, smallTickHits, sliderEndHits, legacyTotalScore;

    private PerformanceRequest(Builder b) {
        difficulty = b.difficulty; scoreFields = b.fields; accuracy = b.accuracy;
        combo = b.combo; misses = b.misses; n300 = b.n300; n100 = b.n100; n50 = b.n50;
        nGeki = b.nGeki; nKatu = b.nKatu; largeTickHits = b.largeTickHits;
        smallTickHits = b.smallTickHits; sliderEndHits = b.sliderEndHits;
        legacyTotalScore = b.legacyTotalScore;
    }

    public static Builder builder(DifficultyRequest difficulty) { return new Builder(difficulty); }
    public DifficultyRequest difficulty() { return difficulty; }
    public long scoreFields() { return scoreFields; }
    public double accuracyRaw() { return accuracy; }
    public int comboRaw() { return combo; }
    public int missesRaw() { return misses; }
    public int n300Raw() { return n300; }
    public int n100Raw() { return n100; }
    public int n50Raw() { return n50; }
    public int nGekiRaw() { return nGeki; }
    public int nKatuRaw() { return nKatu; }
    public int largeTickHitsRaw() { return largeTickHits; }
    public int smallTickHitsRaw() { return smallTickHits; }
    public int sliderEndHitsRaw() { return sliderEndHits; }
    public int legacyTotalScoreRaw() { return legacyTotalScore; }

    public static final class Builder {
        private final DifficultyRequest difficulty;
        private long fields;
        private double accuracy;
        private int combo, misses, n300, n100, n50, nGeki, nKatu;
        private int largeTickHits, smallTickHits, sliderEndHits, legacyTotalScore;
        private Builder(DifficultyRequest difficulty) { this.difficulty = java.util.Objects.requireNonNull(difficulty); }
        private int nonNegative(String name, int value) {
            if (value < 0) throw new IllegalArgumentException(name + " must be non-negative");
            return value;
        }
        public Builder accuracy(double v) { accuracy = v; fields |= ACCURACY; return this; }
        public Builder combo(int v) { combo = nonNegative("combo", v); fields |= COMBO; return this; }
        public Builder misses(int v) { misses = nonNegative("misses", v); fields |= MISSES; return this; }
        public Builder n300(int v) { n300 = nonNegative("n300", v); fields |= N300; return this; }
        public Builder n100(int v) { n100 = nonNegative("n100", v); fields |= N100; return this; }
        public Builder n50(int v) { n50 = nonNegative("n50", v); fields |= N50; return this; }
        public Builder nGeki(int v) { nGeki = nonNegative("nGeki", v); fields |= N_GEKI; return this; }
        public Builder nKatu(int v) { nKatu = nonNegative("nKatu", v); fields |= N_KATU; return this; }
        public Builder largeTickHits(int v) { largeTickHits = nonNegative("largeTickHits", v); fields |= LARGE_TICK_HITS; return this; }
        public Builder smallTickHits(int v) { smallTickHits = nonNegative("smallTickHits", v); fields |= SMALL_TICK_HITS; return this; }
        public Builder sliderEndHits(int v) { sliderEndHits = nonNegative("sliderEndHits", v); fields |= SLIDER_END_HITS; return this; }
        public Builder legacyTotalScore(int v) { legacyTotalScore = nonNegative("legacyTotalScore", v); fields |= LEGACY_TOTAL_SCORE; return this; }
        public PerformanceRequest build() { return new PerformanceRequest(this); }
    }
}
