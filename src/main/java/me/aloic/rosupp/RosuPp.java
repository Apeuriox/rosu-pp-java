package me.aloic.rosupp;

import me.aloic.rosupp.internal.NativeBridge;

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** An algorithm-bound calculator. Instances for both versions may coexist in one process. */
public final class RosuPp implements AutoCloseable {
    private static final NativeBridge NATIVE = NativeBridge.INSTANCE;
    private final AlgorithmVersion algorithm;
    private final AtomicReference<MemorySegment> handle;

    private RosuPp(AlgorithmVersion algorithm) {
        this.algorithm = Objects.requireNonNull(algorithm);
        this.handle = new AtomicReference<>(NATIVE.calculatorCreate(algorithm));
    }

    public static RosuPp forVersion(AlgorithmVersion algorithm) { return new RosuPp(algorithm); }
    public static List<AlgorithmInfo> availableAlgorithms() { return NATIVE.algorithms(); }
    public AlgorithmVersion algorithm() { return algorithm; }

    public Beatmap loadBeatmap(Path path) {
        Objects.requireNonNull(path);
        return new Beatmap(this, NATIVE.beatmapFromPath(nativeHandle(), path));
    }

    public Beatmap loadBeatmap(byte[] osuBytes) {
        Objects.requireNonNull(osuBytes);
        return new Beatmap(this, NATIVE.beatmapFromBytes(nativeHandle(), osuBytes));
    }

    public DifficultyResult calculateDifficulty(Beatmap beatmap, DifficultyRequest request) {
        requireOwned(beatmap);
        return NATIVE.difficulty(nativeHandle(), beatmap.nativeHandle(), Objects.requireNonNull(request));
    }

    public PerformanceResult calculatePerformance(Beatmap beatmap, PerformanceRequest request) {
        requireOwned(beatmap);
        return NATIVE.performance(nativeHandle(), beatmap.nativeHandle(), Objects.requireNonNull(request));
    }

    public GradualDifficulty gradualDifficulty(Beatmap beatmap, DifficultyRequest request) {
        requireOwned(beatmap);
        return new GradualDifficulty(NATIVE.gradualCreate(nativeHandle(), beatmap.nativeHandle(), request, false));
    }

    public GradualPerformance gradualPerformance(Beatmap beatmap, DifficultyRequest request) {
        requireOwned(beatmap);
        return new GradualPerformance(NATIVE.gradualCreate(nativeHandle(), beatmap.nativeHandle(), request, true));
    }

    private void requireOwned(Beatmap beatmap) {
        Objects.requireNonNull(beatmap);
        if (beatmap.owner() != this) throw new IllegalArgumentException("Beatmap belongs to a different algorithm-bound RosuPp instance");
    }

    MemorySegment nativeHandle() {
        MemorySegment value = handle.get();
        if (value.equals(MemorySegment.NULL)) throw new IllegalStateException("RosuPp calculator is closed");
        return value;
    }

    @Override public void close() {
        MemorySegment value = handle.getAndSet(MemorySegment.NULL);
        if (!value.equals(MemorySegment.NULL)) NATIVE.calculatorFree(value);
    }
}
