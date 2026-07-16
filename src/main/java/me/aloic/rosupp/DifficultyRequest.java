package me.aloic.rosupp;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class DifficultyRequest {
    static final long CLOCK_RATE = 1L;
    static final long AR = 1L << 1;
    static final long OD = 1L << 2;
    static final long CS = 1L << 3;
    static final long HP = 1L << 4;
    static final long PASSED_OBJECTS = 1L << 5;
    static final long SCORE_MODE = 1L << 6;

    private final GameMode mode;
    private final Mods mods;
    private final long optionFlags;
    private final ScoreMode scoreMode;
    private final double clockRate, ar, od, cs, hp;
    private final int passedObjects;

    private DifficultyRequest(Builder b) {
        mode = b.mode; mods = b.mods; optionFlags = b.flags; scoreMode = b.scoreMode;
        clockRate = b.clockRate; ar = b.ar; od = b.od; cs = b.cs; hp = b.hp;
        passedObjects = b.passedObjects;
    }

    public static Builder builder() { return new Builder(); }
    public static DifficultyRequest defaults() { return builder().build(); }
    public Optional<GameMode> mode() { return Optional.ofNullable(mode); }
    public Mods mods() { return mods; }
    public Optional<ScoreMode> scoreMode() { return has(SCORE_MODE) ? Optional.of(scoreMode) : Optional.empty(); }
    public OptionalDouble clockRate() { return optional(CLOCK_RATE, clockRate); }
    public OptionalDouble ar() { return optional(AR, ar); }
    public OptionalDouble od() { return optional(OD, od); }
    public OptionalDouble cs() { return optional(CS, cs); }
    public OptionalDouble hp() { return optional(HP, hp); }
    public OptionalInt passedObjects() { return has(PASSED_OBJECTS) ? OptionalInt.of(passedObjects) : OptionalInt.empty(); }
    boolean has(long flag) { return (optionFlags & flag) != 0; }
    public long optionFlags() { return optionFlags; }
    public double clockRateRaw() { return clockRate; }
    public double arRaw() { return ar; }
    public double odRaw() { return od; }
    public double csRaw() { return cs; }
    public double hpRaw() { return hp; }
    public int passedObjectsRaw() { return passedObjects; }
    public ScoreMode scoreModeRaw() { return scoreMode; }
    private OptionalDouble optional(long flag, double value) { return has(flag) ? OptionalDouble.of(value) : OptionalDouble.empty(); }

    public static final class Builder {
        private GameMode mode;
        private Mods mods = Mods.NONE;
        private long flags;
        private ScoreMode scoreMode = ScoreMode.DEFAULT;
        private double clockRate, ar, od, cs, hp;
        private int passedObjects;

        public Builder mode(GameMode value) { mode = value; return this; }
        public Builder mods(Mods value) { mods = java.util.Objects.requireNonNull(value); return this; }
        public Builder clockRate(double value) { clockRate = value; flags |= CLOCK_RATE; return this; }
        public Builder ar(double value) { ar = value; flags |= AR; return this; }
        public Builder od(double value) { od = value; flags |= OD; return this; }
        public Builder cs(double value) { cs = value; flags |= CS; return this; }
        public Builder hp(double value) { hp = value; flags |= HP; return this; }
        public Builder passedObjects(int value) {
            if (value < 0) throw new IllegalArgumentException("passedObjects must be non-negative");
            passedObjects = value; flags |= PASSED_OBJECTS; return this;
        }
        public Builder scoreMode(ScoreMode value) {
            scoreMode = java.util.Objects.requireNonNull(value); flags |= SCORE_MODE; return this;
        }
        public DifficultyRequest build() { return new DifficultyRequest(this); }
    }
}
