package me.aloic.rosupp;

import me.aloic.rosupp.internal.NativeBridge;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicReference;

/** A parsed beatmap bound permanently to its calculator's algorithm version. */
public final class Beatmap implements AutoCloseable {
    private final RosuPp owner;
    private final AtomicReference<MemorySegment> handle;

    Beatmap(RosuPp owner, MemorySegment handle) {
        this.owner = owner;
        this.handle = new AtomicReference<>(handle);
    }
    RosuPp owner() { return owner; }
    MemorySegment nativeHandle() {
        MemorySegment value = handle.get();
        if (value.equals(MemorySegment.NULL)) throw new IllegalStateException("Beatmap is closed");
        owner.nativeHandle();
        return value;
    }
    @Override public void close() {
        MemorySegment value = handle.getAndSet(MemorySegment.NULL);
        if (!value.equals(MemorySegment.NULL)) NativeBridge.INSTANCE.beatmapFree(value);
    }
}
