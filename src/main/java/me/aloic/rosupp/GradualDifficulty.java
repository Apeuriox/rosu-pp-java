package me.aloic.rosupp;

import me.aloic.rosupp.internal.NativeBridge;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicReference;

public final class GradualDifficulty implements AutoCloseable {
    private final AtomicReference<MemorySegment> handle;
    GradualDifficulty(MemorySegment handle) { this.handle = new AtomicReference<>(handle); }
    public DifficultyResult next() { return NativeBridge.INSTANCE.gradualDifficultyNext(nativeHandle()); }
    /** Processes all remaining objects and returns the final attributes. */
    public DifficultyResult finish() { return NativeBridge.INSTANCE.gradualDifficultyLast(nativeHandle()); }
    private MemorySegment nativeHandle() {
        MemorySegment value = handle.get();
        if (value.equals(MemorySegment.NULL)) throw new IllegalStateException("GradualDifficulty is closed");
        return value;
    }
    @Override public void close() {
        MemorySegment value = handle.getAndSet(MemorySegment.NULL);
        if (!value.equals(MemorySegment.NULL)) NativeBridge.INSTANCE.gradualFree(value);
    }
}
