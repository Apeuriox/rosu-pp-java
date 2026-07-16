package me.aloic.rosupp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.*;

class NativeIntegrationTest {
    private static byte[] goldenMap() throws IOException {
        try (var in = NativeIntegrationTest.class.getResourceAsStream("/golden/standard.osu")) {
            assertNotNull(in);
            return in.readAllBytes();
        }
    }

    @Test
    void discoversBothPinnedAlgorithmsAndCapabilities() {
        var algorithms = RosuPp.availableAlgorithms();
        assertEquals(2, algorithms.size());
        var byVersion = new EnumMap<AlgorithmVersion, AlgorithmInfo>(AlgorithmVersion.class);
        algorithms.forEach(info -> byVersion.put(info.algorithm(), info));
        assertEquals("rework-202510-rosu-pp-4.0.1", byVersion.get(AlgorithmVersion.REWORK_202510).version());
        assertEquals("rework-20260706-9a073d2", byVersion.get(AlgorithmVersion.REWORK_20260706).version());
        assertFalse(byVersion.get(AlgorithmVersion.REWORK_202510).capabilities().readingSkill());
        assertTrue(byVersion.get(AlgorithmVersion.REWORK_20260706).capabilities().readingSkill());
    }

    @Test
    void twoVersionsCoexistWithoutSharingState() throws IOException {
        byte[] bytes = goldenMap();
        try (var old = RosuPp.forVersion(AlgorithmVersion.REWORK_202510);
             var rework = RosuPp.forVersion(AlgorithmVersion.REWORK_20260706);
             var oldMap = old.loadBeatmap(bytes);
             var reworkMap = rework.loadBeatmap(bytes)) {
            var oldResult = old.calculateDifficulty(oldMap, DifficultyRequest.defaults());
            var newResult = rework.calculateDifficulty(reworkMap, DifficultyRequest.defaults());
            assertEquals(AlgorithmVersion.REWORK_202510, oldResult.algorithmId());
            assertEquals(AlgorithmVersion.REWORK_20260706, newResult.algorithmId());
            assertFalse(oldResult.hasReading());
            assertTrue(newResult.hasReading());
            assertNotEquals(oldResult.stars(), newResult.stars());
            assertThrows(IllegalArgumentException.class,
                    () -> old.calculateDifficulty(reworkMap, DifficultyRequest.defaults()));
        }
    }

    @Test
    void goldenResultsRemainIndependent() throws IOException {
        record Golden(double stars, double pp) {}
        var expected = new EnumMap<AlgorithmVersion, EnumMap<GameMode, Golden>>(AlgorithmVersion.class);
        var old = new EnumMap<GameMode, Golden>(GameMode.class);
        old.put(GameMode.OSU, new Golden(6.476089622889547, 501.5658126493332));
        old.put(GameMode.TAIKO, new Golden(4.282593854949352, 391.27544664357595));
        old.put(GameMode.CATCH, new Golden(5.002235413321431, 371.8771666789953));
        old.put(GameMode.MANIA, new Golden(3.0006536891485234, 88.17804888576262));
        expected.put(AlgorithmVersion.REWORK_202510, old);

        var rework = new EnumMap<GameMode, Golden>(GameMode.class);
        rework.put(GameMode.OSU, new Golden(6.837401009398216, 504.0988431084113));
        rework.put(GameMode.TAIKO, new Golden(4.2840381752865575, 391.10115948741486));
        rework.put(GameMode.CATCH, new Golden(4.994849677115802, 370.7789737132061));
        rework.put(GameMode.MANIA, new Golden(3.0006536891485234, 88.17804888576262));
        expected.put(AlgorithmVersion.REWORK_20260706, rework);

        for (var version : AlgorithmVersion.values()) {
            try (var pp = RosuPp.forVersion(version); var map = pp.loadBeatmap(goldenMap())) {
                for (var mode : GameMode.values()) {
                    var request = DifficultyRequest.builder().mode(mode).build();
                    var difficulty = pp.calculateDifficulty(map, request);
                    var performance = pp.calculatePerformance(map, goldenPerformanceRequest(request, difficulty));
                    var golden = expected.get(version).get(mode);
                    assertEquals(golden.stars(), difficulty.stars(), 1e-9, version + " " + mode);
                    assertEquals(golden.pp(), performance.pp(), 1e-9, version + " " + mode);
                    System.out.println(performance);
                }
                assertEquals(version.stableKey(), version == AlgorithmVersion.REWORK_202510
                        ? "rework-202510" : "rework-20260706-9a073d2");
            }
        }
    }

    @Test
    void fullAndGradualFinalResultsMatchForEachVersion() throws IOException {
        for (var version : AlgorithmVersion.values()) {
            try (var pp = RosuPp.forVersion(version); var map = pp.loadBeatmap(goldenMap())) {
                for (var mode : GameMode.values()) {
                    var builder = DifficultyRequest.builder().mode(mode);
                    if (mode == GameMode.OSU) builder.scoreMode(ScoreMode.STABLE);
                    var request = builder.build();
                    var fullDifficulty = pp.calculateDifficulty(map, request);
                    DifficultyResult gradualDifficulty;
                    try (var gradual = pp.gradualDifficulty(map, request)) {
                        gradualDifficulty = gradual.finish();
                    }
                    assertEquals(fullDifficulty.stars(), gradualDifficulty.stars(), 1e-12,
                            version + " " + mode + " difficulty");

                    var performanceRequest = perfectPerformanceRequest(request, fullDifficulty);
                    var fullPerformance = pp.calculatePerformance(map, performanceRequest);
                    PerformanceResult gradualPerformance;
                    try (var gradual = pp.gradualPerformance(map, request)) {
                        gradualPerformance = gradual.finish(perfectScoreState(fullDifficulty));
                    }
                    assertEquals(fullPerformance.pp(), gradualPerformance.pp(), 1e-12,
                            version + " " + mode + " performance");
                }
            }
        }
    }

    @Test
    void maniaRemainsIdenticalAcrossTheUpdate() throws IOException {
        byte[] bytes = goldenMap();
        try (var old = RosuPp.forVersion(AlgorithmVersion.REWORK_202510);
             var rework = RosuPp.forVersion(AlgorithmVersion.REWORK_20260706);
             var oldMap = old.loadBeatmap(bytes);
             var newMap = rework.loadBeatmap(bytes)) {
            var request = DifficultyRequest.builder().mode(GameMode.MANIA).build();
            var oldDifficulty = old.calculateDifficulty(oldMap, request);
            var newDifficulty = rework.calculateDifficulty(newMap, request);
            assertEquals(oldDifficulty.stars(), newDifficulty.stars(), 0.0);
            assertEquals(
                    old.calculatePerformance(oldMap, goldenPerformanceRequest(request, oldDifficulty)).pp(),
                    rework.calculatePerformance(newMap, goldenPerformanceRequest(request, newDifficulty)).pp(),
                    0.0);
        }
    }

    private static PerformanceRequest goldenPerformanceRequest(
            DifficultyRequest request, DifficultyResult difficulty) {
        return switch (difficulty.mode()) {
            case OSU -> PerformanceRequest.builder(request)
                    .accuracy(100.0).combo(4295).misses(0).build();
            case TAIKO -> PerformanceRequest.builder(request)
                    .accuracy(100.0).combo(difficulty.maxCombo()).misses(0).build();
            case CATCH -> PerformanceRequest.builder(request)
                    .combo(difficulty.maxCombo())
                    .n300(difficulty.fruitCount())
                    .n100(difficulty.dropletCount())
                    .n50(difficulty.tinyDropletCount())
                    .nKatu(0).misses(0).build();
            case MANIA -> PerformanceRequest.builder(request).accuracy(100.0).misses(0).build();
        };
    }

    private static PerformanceRequest perfectPerformanceRequest(
            DifficultyRequest request, DifficultyResult difficulty) {
        return switch (difficulty.mode()) {
            case OSU -> PerformanceRequest.builder(request)
                    .combo(difficulty.maxCombo()).n300(difficulty.objectCount())
                    .n100(0).n50(0).misses(0).build();
            case TAIKO -> PerformanceRequest.builder(request)
                    .combo(difficulty.maxCombo()).n300(difficulty.objectCount())
                    .n100(0).misses(0).build();
            case CATCH -> PerformanceRequest.builder(request)
                    .combo(difficulty.maxCombo())
                    .n300(difficulty.fruitCount())
                    .n100(difficulty.dropletCount())
                    .n50(difficulty.tinyDropletCount())
                    .nKatu(0).misses(0).build();
            case MANIA -> PerformanceRequest.builder(request)
                    .nGeki(difficulty.objectCount()).n300(0).nKatu(0)
                    .n100(0).n50(0).misses(0).build();
        };
    }

    private static ScoreState perfectScoreState(DifficultyResult difficulty) {
        return switch (difficulty.mode()) {
            case OSU, TAIKO -> new ScoreState(
                    difficulty.maxCombo(), 0, 0, 0, 0, 0,
                    difficulty.objectCount(), 0, 0, 0, null);
            case CATCH -> new ScoreState(
                    difficulty.maxCombo(), 0, 0, 0, 0, 0,
                    difficulty.fruitCount(), difficulty.dropletCount(),
                    difficulty.tinyDropletCount(), 0, null);
            case MANIA -> new ScoreState(
                    0, 0, 0, 0, difficulty.objectCount(), 0,
                    0, 0, 0, 0, null);
        };
    }

    @Test
    void unsupportedOptionsAndIdempotentCloseAreSafe() throws IOException {
        var pp = RosuPp.forVersion(AlgorithmVersion.REWORK_202510);
        var map = pp.loadBeatmap(goldenMap());
        var invalid = DifficultyRequest.builder().mode(GameMode.MANIA).ar(9).build();
        assertThrows(UnsupportedOptionException.class, () -> pp.calculateDifficulty(map, invalid));
        map.close();
        map.close();
        assertThrows(IllegalStateException.class, () -> pp.calculateDifficulty(map, DifficultyRequest.defaults()));
        pp.close();
        pp.close();
        assertThrows(IllegalStateException.class, () -> pp.loadBeatmap(goldenMap()));
    }
}
