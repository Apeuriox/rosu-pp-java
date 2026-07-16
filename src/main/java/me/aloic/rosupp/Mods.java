package me.aloic.rosupp;

import java.util.Arrays;

/** Stable bridge mod bitset based on osu!'s public legacy mod bits, not a Rust enum layout. */
public record Mods(long bits) {
    public static final Mods NONE = new Mods(0);
    public static final Mods NO_FAIL = new Mods(1L);
    public static final Mods EASY = new Mods(1L << 1);
    public static final Mods TOUCH_DEVICE = new Mods(1L << 2);
    public static final Mods HIDDEN = new Mods(1L << 3);
    public static final Mods HARD_ROCK = new Mods(1L << 4);
    public static final Mods SUDDEN_DEATH = new Mods(1L << 5);
    public static final Mods DOUBLE_TIME = new Mods(1L << 6);
    public static final Mods RELAX = new Mods(1L << 7);
    public static final Mods HALF_TIME = new Mods(1L << 8);
    public static final Mods NIGHTCORE = new Mods(1L << 9);
    public static final Mods FLASHLIGHT = new Mods(1L << 10);
    public static final Mods SPUN_OUT = new Mods(1L << 12);
    public static final Mods PERFECT = new Mods(1L << 14);
    public static final Mods CLASSIC = new Mods(1L << 29);

    public Mods {
        if ((bits & 0xffff_ffff_0000_0000L) != 0) {
            throw new IllegalArgumentException("Mod bits above bit 31 are reserved for future ABI versions");
        }
    }

    public static Mods of(Mods... mods) {
        return new Mods(Arrays.stream(mods).mapToLong(Mods::bits).reduce(0, (a, b) -> a | b));
    }

    public Mods plus(Mods other) { return new Mods(bits | other.bits); }
    public boolean contains(Mods other) { return (bits & other.bits) == other.bits; }
}
