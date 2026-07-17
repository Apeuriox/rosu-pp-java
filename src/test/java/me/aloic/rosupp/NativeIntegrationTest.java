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
    void discoversAllPinnedAlgorithmsAndCapabilities() {
        var algorithms = RosuPp.availableAlgorithms();
        assertEquals(5, algorithms.size());
        var byVersion = new EnumMap<AlgorithmVersion, AlgorithmInfo>(AlgorithmVersion.class);
        algorithms.forEach(info -> byVersion.put(info.algorithm(), info));
        assertEquals("precsr-202210-rosu-pp-1.0.0", byVersion.get(AlgorithmVersion.PRECSR_202210).version());
        assertEquals("rework-202411-rosu-pp-2.0.0", byVersion.get(AlgorithmVersion.REWORK_202411).version());
        assertEquals("rework-202502-rosu-pp-3.1.0", byVersion.get(AlgorithmVersion.REWORK_202502).version());
        assertEquals("rework-202510-rosu-pp-4.0.1", byVersion.get(AlgorithmVersion.REWORK_202510).version());
        assertEquals("rework-20260706-9a073d2", byVersion.get(AlgorithmVersion.REWORK_20260706).version());
        for (var version : AlgorithmVersion.values()) {
            assertEquals(version != AlgorithmVersion.PRECSR_202210,
                    byVersion.get(version).capabilities().structuredMods(), version.toString());
            assertEquals(version != AlgorithmVersion.PRECSR_202210,
                    byVersion.get(version).capabilities().lazerSliderAccuracy(), version.toString());
            assertEquals(version == AlgorithmVersion.REWORK_20260706,
                    byVersion.get(version).capabilities().readingSkill(), version.toString());
        }
        assertTrue(byVersion.get(AlgorithmVersion.REWORK_20260706).capabilities().readingSkill());
    }

    @Test
    void structuredModsAreParsedByBothBackendsAndPreservedForGradualCalculation() throws IOException {
        String customDt = """
                [{"acronym":"DT","settings":{"speed_change":1.2}}]
                """;
        var jsonRequest = DifficultyRequest.builder()
                .mode(GameMode.OSU)
                .modsJson(customDt)
                .build();
        var legacyDtRequest = DifficultyRequest.builder()
                .mode(GameMode.OSU)
                .mods(Mods.DOUBLE_TIME)
                .build();

        for (var version : AlgorithmVersion.values()) {
            if (version == AlgorithmVersion.PRECSR_202210) continue;
            try (var pp = RosuPp.forVersion(version); var map = pp.loadBeatmap(goldenMap())) {
                var custom = pp.calculateDifficulty(map, jsonRequest);
                var legacy = pp.calculateDifficulty(map, legacyDtRequest);
                assertNotEquals(legacy.stars(), custom.stars(), version.toString());

                try (var gradual = pp.gradualDifficulty(map, jsonRequest)) {
                    assertEquals(custom.stars(), gradual.finish().stars(), 1e-12, version.toString());
                }

                var performance = pp.calculatePerformance(
                        map,
                        PerformanceRequest.builder(jsonRequest).accuracy(98.5).misses(1).build());
                assertEquals(version, performance.algorithmId());
            }
        }

        try (var pp = RosuPp.forVersion(AlgorithmVersion.PRECSR_202210);
             var map = pp.loadBeatmap(goldenMap())) {
            assertThrows(UnsupportedOptionException.class,
                    () -> pp.calculateDifficulty(map, jsonRequest));
        }
    }

    @Test
    void malformedAndUnsupportedStructuredModsReturnExplicitErrors() throws IOException {
        for (var version : AlgorithmVersion.values()) {
            if (version == AlgorithmVersion.PRECSR_202210) continue;
            try (var pp = RosuPp.forVersion(version); var map = pp.loadBeatmap(goldenMap())) {
                var malformed = DifficultyRequest.builder().modsJson("[{]").build();
                assertThrows(RosuPpException.class, () -> pp.calculateDifficulty(map, malformed),
                        version.toString());

                var unknownSetting = DifficultyRequest.builder()
                        .modsJson("[{\"acronym\":\"DT\",\"settings\":{\"not_a_setting\":1}}]")
                        .build();
                assertThrows(RosuPpException.class, () -> pp.calculateDifficulty(map, unknownSetting),
                        version.toString());

                var unknownMod = DifficultyRequest.builder()
                        .modsJson("[{\"acronym\":\"ZZ\"}]")
                        .build();
                assertThrows(UnsupportedOptionException.class,
                        () -> pp.calculateDifficulty(map, unknownMod), version.toString());
            }
        }
    }

    @Test
    void allVersionsCoexistWithoutSharingState() throws IOException {
        byte[] bytes = goldenMap();
        try (var precsr = RosuPp.forVersion(AlgorithmVersion.PRECSR_202210);
             var rework202411 = RosuPp.forVersion(AlgorithmVersion.REWORK_202411);
             var rework202502 = RosuPp.forVersion(AlgorithmVersion.REWORK_202502);
             var rework202510 = RosuPp.forVersion(AlgorithmVersion.REWORK_202510);
             var rework202607 = RosuPp.forVersion(AlgorithmVersion.REWORK_20260706);
             var precsrMap = precsr.loadBeatmap(bytes);
             var map202411 = rework202411.loadBeatmap(bytes);
             var map202502 = rework202502.loadBeatmap(bytes);
             var map202510 = rework202510.loadBeatmap(bytes);
             var map202607 = rework202607.loadBeatmap(bytes)) {
            var request = DifficultyRequest.defaults();
            assertEquals(AlgorithmVersion.PRECSR_202210,
                    precsr.calculateDifficulty(precsrMap, request).algorithmId());
            assertEquals(AlgorithmVersion.REWORK_202411,
                    rework202411.calculateDifficulty(map202411, request).algorithmId());
            assertEquals(AlgorithmVersion.REWORK_202502,
                    rework202502.calculateDifficulty(map202502, request).algorithmId());
            assertEquals(AlgorithmVersion.REWORK_202510,
                    rework202510.calculateDifficulty(map202510, request).algorithmId());
            var newest = rework202607.calculateDifficulty(map202607, request);
            assertEquals(AlgorithmVersion.REWORK_20260706, newest.algorithmId());
            assertTrue(newest.hasReading());
            assertThrows(IllegalArgumentException.class,
                    () -> precsr.calculateDifficulty(map202607, request));
        }
    }

    @Test
    void goldenResultsRemainIndependent() throws IOException {
        record Golden(double stars, double pp) {}
        var expected = new EnumMap<AlgorithmVersion, EnumMap<GameMode, Golden>>(AlgorithmVersion.class);
        var precsr = new EnumMap<GameMode, Golden>(GameMode.class);
        precsr.put(GameMode.OSU, new Golden(6.493222707809602, 493.18592285663505));
        precsr.put(GameMode.TAIKO, new Golden(5.169815713559883, 368.0874703919316));
        precsr.put(GameMode.CATCH, new Golden(5.005506181464555, 372.3640194903018));
        precsr.put(GameMode.MANIA, new Golden(3.00065368915447, 88.17804888616732));
        expected.put(AlgorithmVersion.PRECSR_202210, precsr);

        var rework202411 = new EnumMap<GameMode, Golden>(GameMode.class);
        rework202411.put(GameMode.OSU, new Golden(6.4881491246971965, 493.23068934772715));
        rework202411.put(GameMode.TAIKO, new Golden(5.161815738471442, 384.4559012161384));
        rework202411.put(GameMode.CATCH, new Golden(5.002235413321431, 371.8771666789953));
        rework202411.put(GameMode.MANIA, new Golden(3.0006536891485234, 88.17804888576262));
        expected.put(AlgorithmVersion.REWORK_202411, rework202411);

        var rework202502 = new EnumMap<GameMode, Golden>(GameMode.class);
        rework202502.put(GameMode.OSU, new Golden(6.476760463610541, 491.5649464451657));
        rework202502.put(GameMode.TAIKO, new Golden(4.343191524346165, 304.3730735846037));
        rework202502.put(GameMode.CATCH, new Golden(5.002235413321431, 371.8771666789953));
        rework202502.put(GameMode.MANIA, new Golden(3.0006536891485234, 88.17804888576262));
        expected.put(AlgorithmVersion.REWORK_202502, rework202502);

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
//                    System.out.println(performance);
                }
                assertEquals(switch (version) {
                    case PRECSR_202210 -> "precsr-202210-rosu-pp-1.0.0";
                    case REWORK_202411 -> "rework-202411-rosu-pp-2.0.0";
                    case REWORK_202502 -> "rework-202502-rosu-pp-3.1.0";
                    case REWORK_202510 -> "rework-202510";
                    case REWORK_20260706 -> "rework-20260706-9a073d2";
                }, version.stableKey());
            }
        }
    }
    @Test
    void outputFullResult() throws IOException {
        try (var pp = RosuPp.forVersion(AlgorithmVersion.REWORK_20260706); var map = pp.loadBeatmap(goldenMap())) {
            var performance = pp.calculatePerformance(map, PerformanceRequest.builder(DifficultyRequest.builder().mode(GameMode.OSU)
                            .modsJson("""
                                    [\
                                        {
                                          "acronym": "HR"
                                        }
                                      ]""").build())
                    .accuracy(100.0).combo(4295).build());
            System.out.println(performance);
        }
    }

    @Test
    void fullAndGradualFinalResultsMatchForEachVersion() throws IOException {
        for (var version : AlgorithmVersion.values()) {
            try (var pp = RosuPp.forVersion(version); var map = pp.loadBeatmap(goldenMap())) {
                for (var mode : GameMode.values()) {
                    var builder = DifficultyRequest.builder().mode(mode);
                    if (mode == GameMode.OSU && version != AlgorithmVersion.PRECSR_202210) {
                        builder.scoreMode(ScoreMode.STABLE);
                    }
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

    @Test
    void historicalBackendsRejectInputsTheyCannotRepresent() throws IOException {
        try (var pp = RosuPp.forVersion(AlgorithmVersion.PRECSR_202210);
             var map = pp.loadBeatmap(goldenMap())) {
            var scoreMode = DifficultyRequest.builder().scoreMode(ScoreMode.LAZER).build();
            assertThrows(UnsupportedOptionException.class,
                    () -> pp.calculateDifficulty(map, scoreMode));

            try (var gradual = pp.gradualPerformance(map, DifficultyRequest.defaults())) {
                var lazerState = new ScoreState(1, 1, 0, 0, 0, 0, 1, 0, 0, 0, null);
                assertThrows(UnsupportedOptionException.class, () -> gradual.next(lazerState));
            }
        }

        for (var version : new AlgorithmVersion[]{
                AlgorithmVersion.REWORK_202411, AlgorithmVersion.REWORK_202502}) {
            try (var pp = RosuPp.forVersion(version);
                 var map = pp.loadBeatmap(goldenMap());
                 var gradual = pp.gradualPerformance(map, DifficultyRequest.defaults())) {
                var legacyScoreState = new ScoreState(1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1_000_000);
                assertThrows(UnsupportedOptionException.class,
                        () -> gradual.next(legacyScoreState), version.toString());
            }
        }
    }
}
