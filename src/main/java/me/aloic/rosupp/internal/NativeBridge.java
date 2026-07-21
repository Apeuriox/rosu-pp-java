package me.aloic.rosupp.internal;

import me.aloic.rosupp.*;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.*;

public final class NativeBridge {
    public static final int ABI_VERSION = 2;
    private static final int MAX_C_STRING_BYTES = 4096;

    private static final MemoryLayout ALGORITHM_INFO = MemoryLayout.structLayout(
            JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS);
    private static final MemoryLayout DIFFICULTY_REQUEST = MemoryLayout.structLayout(
            JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG,
            JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_INT, JAVA_INT,
            ADDRESS, JAVA_LONG);
    private static final MemoryLayout PERFORMANCE_REQUEST = MemoryLayout.structLayout(
            DIFFICULTY_REQUEST, JAVA_LONG, JAVA_DOUBLE,
            JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
            JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT);
    private static final MemoryLayout SCORE_STATE = MemoryLayout.structLayout(
            JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
            JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG);
    private static final MemoryLayout DIFFICULTY_RESULT = MemoryLayout.structLayout(
            JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG, ADDRESS, ADDRESS,
            JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE,
            JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE,
            JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE,
            JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE,
            JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE,
            JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
            JAVA_INT, JAVA_INT);
    private static final MemoryLayout PERFORMANCE_RESULT = MemoryLayout.structLayout(
            JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG, ADDRESS, ADDRESS,
            JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE,
            JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE,
            JAVA_DOUBLE, JAVA_DOUBLE, DIFFICULTY_RESULT);

    public static final NativeBridge INSTANCE = new NativeBridge();

    private final Linker linker = Linker.nativeLinker();
    private final SymbolLookup symbols = NativeLibraryLoader.load();
    private final MethodHandle abiVersion = downcall("rosu_abi_version", FunctionDescriptor.of(JAVA_INT));
    private final MethodHandle algorithmCount = downcall("rosu_algorithm_count", FunctionDescriptor.of(JAVA_INT));
    private final MethodHandle algorithmInfo = downcall("rosu_algorithm_info", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS));
    private final MethodHandle calculatorCreate = downcall("rosu_calculator_create", FunctionDescriptor.of(ADDRESS, JAVA_INT));
    private final MethodHandle calculatorFree = downcall("rosu_calculator_free", FunctionDescriptor.ofVoid(ADDRESS));
    private final MethodHandle beatmapBytes = downcall("rosu_beatmap_from_bytes", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));
    private final MethodHandle beatmapPath = downcall("rosu_beatmap_from_path", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));
    private final MethodHandle beatmapFree = downcall("rosu_beatmap_free", FunctionDescriptor.ofVoid(ADDRESS));
    private final MethodHandle calculateDifficulty = downcall("rosu_calculate_difficulty", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private final MethodHandle calculatePerformance = downcall("rosu_calculate_performance", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private final MethodHandle gradualDifficultyCreate = downcall("rosu_gradual_difficulty_create", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private final MethodHandle gradualPerformanceCreate = downcall("rosu_gradual_performance_create", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private final MethodHandle gradualDifficultyNext = downcall("rosu_gradual_difficulty_next", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private final MethodHandle gradualPerformanceNext = downcall("rosu_gradual_performance_next", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    private final MethodHandle gradualDifficultyLast = downcall("rosu_gradual_difficulty_last", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private final MethodHandle gradualPerformanceLast = downcall("rosu_gradual_performance_last", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    private final MethodHandle gradualFree = downcall("rosu_gradual_free", FunctionDescriptor.ofVoid(ADDRESS));
    private final MethodHandle errorLength = downcall("rosu_last_error_length", FunctionDescriptor.of(JAVA_LONG));
    private final MethodHandle errorCopy = downcall("rosu_last_error_copy", FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_LONG));

    private NativeBridge() {
        int actual = invokeInt(abiVersion);
        if (actual != ABI_VERSION) throw new RosuPpException(-4, "Native ABI mismatch: Java=" + ABI_VERSION + ", native=" + actual);
        if (DIFFICULTY_REQUEST.byteSize() != 96 || PERFORMANCE_REQUEST.byteSize() != 160
                || SCORE_STATE.byteSize() != 64 || DIFFICULTY_RESULT.byteSize() != 328
                || PERFORMANCE_RESULT.byteSize() != 488) {
            throw new ExceptionInInitializerError("Unexpected Java platform C layout sizes");
        }
    }

    public List<AlgorithmInfo> algorithms() {
        int count = invokeInt(algorithmCount);
        List<AlgorithmInfo> result = new ArrayList<>(count);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ALGORITHM_INFO);
            for (int i = 0; i < count; i++) {
                initializeOutput(out, ALGORITHM_INFO);
                check(invokeInt(algorithmInfo, i, out));
                int id = out.get(JAVA_INT, 8);
                result.add(new AlgorithmInfo(AlgorithmVersion.fromNativeId(id), cString(out.get(ADDRESS, 24)),
                        cString(out.get(ADDRESS, 32)), cString(out.get(ADDRESS, 40)),
                        new AlgorithmCapabilities(out.get(JAVA_LONG, 16))));
            }
        }
        return List.copyOf(result);
    }

    public MemorySegment calculatorCreate(AlgorithmVersion version) {
        MemorySegment handle = invokeAddress(calculatorCreate, version.nativeId());
        if (handle.equals(MemorySegment.NULL)) throw lastException(-2);
        return handle;
    }
    public void calculatorFree(MemorySegment handle) { invokeVoid(calculatorFree, handle); }

    public MemorySegment beatmapFromBytes(MemorySegment calculator, byte[] bytes) {
        if (bytes.length == 0) throw new IllegalArgumentException("Beatmap bytes must not be empty");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocate(bytes.length);
            data.copyFrom(MemorySegment.ofArray(bytes));
            MemorySegment handle = invokeAddress(beatmapBytes, calculator, data, (long) bytes.length);
            if (handle.equals(MemorySegment.NULL)) throw lastException(-5);
            return handle;
        }
    }

    public MemorySegment beatmapFromPath(MemorySegment calculator, Path path) {
        byte[] utf8 = path.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocate(utf8.length);
            data.copyFrom(MemorySegment.ofArray(utf8));
            MemorySegment handle = invokeAddress(beatmapPath, calculator, data, (long) utf8.length);
            if (handle.equals(MemorySegment.NULL)) throw lastException(-5);
            return handle;
        }
    }
    public void beatmapFree(MemorySegment handle) { invokeVoid(beatmapFree, handle); }

    public DifficultyResult difficulty(MemorySegment calculator, MemorySegment map, DifficultyRequest request) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment req = writeDifficulty(arena, request);
            MemorySegment out = arena.allocate(DIFFICULTY_RESULT);
            initializeOutput(out, DIFFICULTY_RESULT);
            check(invokeInt(calculateDifficulty, calculator, map, req, out));
            return readDifficulty(out);
        }
    }

    public PerformanceResult performance(MemorySegment calculator, MemorySegment map, PerformanceRequest request) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment req = writePerformance(arena, request);
            MemorySegment out = arena.allocate(PERFORMANCE_RESULT);
            initializeOutput(out, PERFORMANCE_RESULT);
            check(invokeInt(calculatePerformance, calculator, map, req, out));
            return readPerformance(out);
        }
    }

    public MemorySegment gradualCreate(MemorySegment calculator, MemorySegment map, DifficultyRequest request, boolean performance) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment req = writeDifficulty(arena, request);
            MemorySegment handle = invokeAddress(performance ? gradualPerformanceCreate : gradualDifficultyCreate, calculator, map, req);
            if (handle.equals(MemorySegment.NULL)) throw lastException(-1);
            return handle;
        }
    }

    public DifficultyResult gradualDifficultyNext(MemorySegment handle) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(DIFFICULTY_RESULT);
            initializeOutput(out, DIFFICULTY_RESULT);
            check(invokeInt(gradualDifficultyNext, handle, out));
            return readDifficulty(out);
        }
    }

    public PerformanceResult gradualPerformanceNext(MemorySegment handle, ScoreState state) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment rawState = writeScoreState(arena, state);
            MemorySegment out = arena.allocate(PERFORMANCE_RESULT);
            initializeOutput(out, PERFORMANCE_RESULT);
            check(invokeInt(gradualPerformanceNext, handle, rawState, out));
            return readPerformance(out);
        }
    }

    public DifficultyResult gradualDifficultyLast(MemorySegment handle) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(DIFFICULTY_RESULT);
            initializeOutput(out, DIFFICULTY_RESULT);
            check(invokeInt(gradualDifficultyLast, handle, out));
            return readDifficulty(out);
        }
    }

    public PerformanceResult gradualPerformanceLast(MemorySegment handle, ScoreState state) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment rawState = writeScoreState(arena, state);
            MemorySegment out = arena.allocate(PERFORMANCE_RESULT);
            initializeOutput(out, PERFORMANCE_RESULT);
            check(invokeInt(gradualPerformanceLast, handle, rawState, out));
            return readPerformance(out);
        }
    }
    public void gradualFree(MemorySegment handle) { invokeVoid(gradualFree, handle); }

    private MemorySegment writeDifficulty(Arena arena, DifficultyRequest request) {
        MemorySegment out = arena.allocate(DIFFICULTY_REQUEST);
        out.set(JAVA_INT, 0, (int) DIFFICULTY_REQUEST.byteSize());
        out.set(JAVA_INT, 4, ABI_VERSION);
        out.set(JAVA_INT, 8, request.mode().map(GameMode::nativeId).orElse(-1));
        out.set(JAVA_INT, 12, request.scoreModeRaw().nativeId());
        out.set(JAVA_LONG, 16, request.mods().bits());
        out.set(JAVA_LONG, 24, request.optionFlags());
        out.set(JAVA_DOUBLE, 32, request.clockRateRaw());
        out.set(JAVA_DOUBLE, 40, request.arRaw());
        out.set(JAVA_DOUBLE, 48, request.odRaw());
        out.set(JAVA_DOUBLE, 56, request.csRaw());
        out.set(JAVA_DOUBLE, 64, request.hpRaw());
        out.set(JAVA_INT, 72, request.passedObjectsRaw());
        String modsJson = request.modsJsonRaw();
        if (modsJson == null) {
            out.set(ADDRESS, 80, MemorySegment.NULL);
            out.set(JAVA_LONG, 88, 0);
        } else {
            byte[] utf8 = modsJson.getBytes(StandardCharsets.UTF_8);
            MemorySegment data = arena.allocate(utf8.length);
            data.copyFrom(MemorySegment.ofArray(utf8));
            out.set(ADDRESS, 80, data);
            out.set(JAVA_LONG, 88, utf8.length);
        }
        return out;
    }

    private void initializeOutput(MemorySegment output, MemoryLayout layout) {
        output.set(JAVA_INT, 0, (int) layout.byteSize());
        output.set(JAVA_INT, 4, ABI_VERSION);
    }

    private MemorySegment writePerformance(Arena arena, PerformanceRequest request) {
        MemorySegment out = arena.allocate(PERFORMANCE_REQUEST);
        out.asSlice(0, DIFFICULTY_REQUEST.byteSize()).copyFrom(writeDifficulty(arena, request.difficulty()));
        out.set(JAVA_LONG, 96, request.scoreFields());
        out.set(JAVA_DOUBLE, 104, request.accuracyRaw());
        int[] values = {request.comboRaw(), request.missesRaw(), request.n300Raw(), request.n100Raw(), request.n50Raw(),
                request.nGekiRaw(), request.nKatuRaw(), request.largeTickHitsRaw(), request.smallTickHitsRaw(),
                request.sliderEndHitsRaw(), request.legacyTotalScoreRaw(), 0};
        for (int i = 0; i < values.length; i++) out.set(JAVA_INT, 112L + i * 4L, values[i]);
        return out;
    }

    private MemorySegment writeScoreState(Arena arena, ScoreState state) {
        MemorySegment out = arena.allocate(SCORE_STATE);
        int[] values = {(int) SCORE_STATE.byteSize(), ABI_VERSION, state.maxCombo(), state.largeTickHits(),
                state.smallTickHits(), state.sliderEndHits(), state.nGeki(), state.nKatu(), state.n300(),
                state.n100(), state.n50(), state.misses(), state.legacyTotalScore() == null ? 0 : state.legacyTotalScore(), 0};
        for (int i = 0; i < values.length; i++) out.set(JAVA_INT, i * 4L, values[i]);
        out.set(JAVA_LONG, 56, state.legacyTotalScore() == null ? 0 : PerformanceRequest.LEGACY_TOTAL_SCORE);
        return out;
    }

    private DifficultyResult readDifficulty(MemorySegment in) {
        int algorithmId = in.get(JAVA_INT, 8);
        int mode = in.get(JAVA_INT, 12);
        long capabilities = in.get(JAVA_LONG, 16);
        long present = in.get(JAVA_LONG, 24);
        double[] d = new double[30];
        for (int i = 0; i < d.length; i++) d[i] = in.get(JAVA_DOUBLE, 48L + i * 8L);
        int[] n = new int[10];
        for (int i = 0; i < n.length; i++) n[i] = in.get(JAVA_INT, 288L + i * 4L);
        return new DifficultyResult(AlgorithmVersion.fromNativeId(algorithmId), cString(in.get(ADDRESS, 32)),
                cString(in.get(ADDRESS, 40)), new AlgorithmCapabilities(capabilities), GameMode.fromNativeId(mode), present,
                d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7], d[8], d[9], d[10], d[11], d[12], d[13], d[14], d[15],
                d[16], d[17], d[18], d[19], d[20], d[21], d[22], d[23], d[24], d[25], d[26], d[27], d[28], d[29],
                n[0], n[1], n[2], n[3], n[4], n[5], n[6], n[7], n[8], n[9] != 0);
    }

    private PerformanceResult readPerformance(MemorySegment in) {
        int algorithmId = in.get(JAVA_INT, 8);
        return new PerformanceResult(AlgorithmVersion.fromNativeId(algorithmId), cString(in.get(ADDRESS, 32)),
                cString(in.get(ADDRESS, 40)), new AlgorithmCapabilities(in.get(JAVA_LONG, 16)),
                GameMode.fromNativeId(in.get(JAVA_INT, 12)), in.get(JAVA_LONG, 24),
                in.get(JAVA_DOUBLE, 48), in.get(JAVA_DOUBLE, 56), in.get(JAVA_DOUBLE, 64),
                in.get(JAVA_DOUBLE, 72), in.get(JAVA_DOUBLE, 80), in.get(JAVA_DOUBLE, 88),
                in.get(JAVA_DOUBLE, 96), in.get(JAVA_DOUBLE, 104), in.get(JAVA_DOUBLE, 112),
                in.get(JAVA_DOUBLE, 120), in.get(JAVA_DOUBLE, 128), in.get(JAVA_DOUBLE, 136),
                in.get(JAVA_DOUBLE, 144), in.get(JAVA_DOUBLE, 152),
                readDifficulty(in.asSlice(160, DIFFICULTY_RESULT.byteSize())));
    }

    private MethodHandle downcall(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = symbols.find(name).orElseThrow(() -> new UnsatisfiedLinkError("Missing native symbol: " + name));
        return linker.downcallHandle(symbol, descriptor);
    }

    private String cString(MemorySegment address) {
        if (address.equals(MemorySegment.NULL)) return "";
        MemorySegment bytes = address.reinterpret(MAX_C_STRING_BYTES);
        int length = 0;
        while (length < MAX_C_STRING_BYTES && bytes.get(JAVA_BYTE, length) != 0) length++;
        if (length == MAX_C_STRING_BYTES) {
            throw new RosuPpException(-4, "Native UTF-8 string exceeds " + MAX_C_STRING_BYTES + " bytes");
        }
        return new String(bytes.asSlice(0, length).toArray(JAVA_BYTE), StandardCharsets.UTF_8);
    }

    private void check(int status) { if (status != 0) throw lastException(status); }
    private RosuPpException lastException(int status) {
        long length = invokeLong(errorLength);
        String message = "Native bridge error " + status;
        if (length > 0 && length <= Integer.MAX_VALUE) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment bytes = arena.allocate(length);
                invokeLong(errorCopy, bytes, length);
                message = new String(bytes.toArray(JAVA_BYTE), StandardCharsets.UTF_8);
            }
        }
        if (status == -6) return new UnsupportedOptionException(message);
        if (status == -127) return new NativePanicException(message);
        return new RosuPpException(status, message);
    }

    private int invokeInt(MethodHandle handle, Object... args) {
        try { return (int) handle.invokeWithArguments(args); } catch (Throwable e) { throw rethrow(e); }
    }
    private long invokeLong(MethodHandle handle, Object... args) {
        try { return (long) handle.invokeWithArguments(args); } catch (Throwable e) { throw rethrow(e); }
    }
    private MemorySegment invokeAddress(MethodHandle handle, Object... args) {
        try { return (MemorySegment) handle.invokeWithArguments(args); } catch (Throwable e) { throw rethrow(e); }
    }
    private void invokeVoid(MethodHandle handle, Object... args) {
        try { handle.invokeWithArguments(args); } catch (Throwable e) { throw rethrow(e); }
    }
    private RuntimeException rethrow(Throwable error) {
        return error instanceof RuntimeException runtime ? runtime : new RosuPpException(-1, error.toString());
    }
}
