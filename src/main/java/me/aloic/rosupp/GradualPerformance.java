package me.aloic.rosupp;

import me.aloic.rosupp.internal.NativeBridge;

import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class GradualPerformance implements AutoCloseable {
    private final AtomicReference<MemorySegment> handle;
    GradualPerformance(MemorySegment handle) { this.handle = new AtomicReference<>(handle); }
    public PerformanceResult next(ScoreState state) {
        return NativeBridge.INSTANCE.gradualPerformanceNext(nativeHandle(), Objects.requireNonNull(state));
    }
    /** Processes all remaining objects with the supplied final score state. */
    public PerformanceResult finish(ScoreState state) {
        return NativeBridge.INSTANCE.gradualPerformanceLast(nativeHandle(), Objects.requireNonNull(state));
    }
    private MemorySegment nativeHandle() {
        MemorySegment value = handle.get();
        if (value.equals(MemorySegment.NULL)) throw new IllegalStateException("GradualPerformance is closed");
        return value;
    }
    @Override public void close() {
        MemorySegment value = handle.getAndSet(MemorySegment.NULL);
        if (!value.equals(MemorySegment.NULL)) NativeBridge.INSTANCE.gradualFree(value);
    }
}
