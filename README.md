# rosu-pp-java

`rosu-pp-java` is a Java 21 library that uses the preview Foreign Function & Memory API to call Rust `rosu-pp` implementations through a single native bridge. Multiple pinned algorithm versions can be loaded in the same process and selected at runtime.

Two algorithm versions are currently supported:

| Java enum | Stable version key | Rust source |
| --- | --- | --- |
| `REWORK_202510` | `rework-202510` | crates.io `rosu-pp = "=4.0.1"` |
| `REWORK_20260706` | `rework-20260706-9a073d2` | `Apeuriox/rosu-pp` commit `9a073d29611ef936b581a08d3c5b288ef5427301` |

The algorithm version is part of every result and must also be included in application cache keys. Do not treat values produced by different algorithm versions as having identical semantics.

## Requirements

- Java 21
- Rust 1.97 or newer when building the native bridge locally
- Windows x86_64 or Linux x86_64

Java 21 still exposes FFM as a preview API, so compilation and execution require:

```text
--enable-preview
--enable-native-access=ALL-UNNAMED
```

The Maven configuration already supplies these flags for compilation and tests.

## Native library loading

The native library is loaded automatically when `RosuPp` is used for the first time. `NativeLibraryLoader` searches in this order:

1. The `rosu.pp.native.path` system property
2. The `ROSU_PP_NATIVE_PATH` environment variable
3. `native/target/release/<library>`
4. `target/native/<library>`
5. `<library>` in the current working directory

The platform library name is:

- Windows: `rosu_pp_ffi.dll`
- Linux: `librosu_pp_ffi.so`

The system property and environment variable must point to the native library file itself, not only to its containing directory. The fallback paths are resolved relative to the Java process working directory. The native library is distributed separately and is not extracted from the JAR.

For example:

```powershell
java --enable-preview --enable-native-access=ALL-UNNAMED `
  -Drosu.pp.native.path=X:\path\to\rosu_pp_ffi.dll `
  -jar your-application.jar
```

```bash
java --enable-preview --enable-native-access=ALL-UNNAMED \
  -Drosu.pp.native.path=/path/to/librosu_pp_ffi.so \
  -jar your-application.jar
```

## Java usage

```java
import me.aloic.rosupp.AlgorithmVersion;
import me.aloic.rosupp.Beatmap;
import me.aloic.rosupp.DifficultyRequest;
import me.aloic.rosupp.DifficultyResult;
import me.aloic.rosupp.Mods;
import me.aloic.rosupp.PerformanceRequest;
import me.aloic.rosupp.PerformanceResult;
import me.aloic.rosupp.RosuPp;
import me.aloic.rosupp.ScoreMode;

import java.nio.file.Path;

try (RosuPp legacy = RosuPp.forVersion(AlgorithmVersion.REWORK_202510);
     RosuPp rework = RosuPp.forVersion(
         AlgorithmVersion.REWORK_20260706
     );
     Beatmap legacyMap = legacy.loadBeatmap(Path.of("map.osu"));
     Beatmap reworkMap = rework.loadBeatmap(Path.of("map.osu"))) {

    DifficultyRequest difficultyRequest = DifficultyRequest.builder()
        .mods(Mods.of(Mods.HIDDEN, Mods.HARD_ROCK))
        .clockRate(1.0)
        .scoreMode(ScoreMode.LAZER)
        .build();

    DifficultyResult oldDifficulty = legacy.calculateDifficulty(
        legacyMap,
        difficultyRequest
    );
    DifficultyResult newDifficulty = rework.calculateDifficulty(
        reworkMap,
        difficultyRequest
    );

    PerformanceRequest performanceRequest = PerformanceRequest.builder(
            difficultyRequest
        )
        .accuracy(98.5)
        .combo(800)
        .misses(2)
        .n300(900)
        .n100(30)
        .n50(5)
        .largeTickHits(100)
        .sliderEndHits(100)
        .build();

    PerformanceResult oldPerformance = legacy.calculatePerformance(
        legacyMap,
        performanceRequest
    );
    PerformanceResult newPerformance = rework.calculatePerformance(
        reworkMap,
        performanceRequest
    );

    System.out.println(oldDifficulty.algorithmVersion());
    System.out.println(newDifficulty.algorithmVersion());
    System.out.println(oldPerformance.pp());
    System.out.println(newPerformance.pp());
}
```

A beatmap handle is bound to the calculator and algorithm version that created it. Load the same `.osu` file separately for each calculator, as shown above. All native-backed Java objects implement `AutoCloseable`; `close()` is idempotent, and using an object after it has been closed throws an exception.

### Structured lazer mods

Legacy mod constants and structured lazer mod JSON are both supported. Use `modsJson` for mods with custom settings:

```java
DifficultyRequest request = DifficultyRequest.builder()
    .modsJson("""
        [
          {"acronym":"HD"},
          {"acronym":"DT","settings":{"speed_change":1.2}}
        ]
        """)
    .build();
```

The JSON is parsed for the beatmap's effective game mode by the selected backend. Legacy `.mods(Mods)` and `.modsJson(String)` are mutually exclusive. Malformed JSON, unknown settings, and unsupported mod acronyms produce an explicit native error instead of being ignored. The osu! API's numeric `MR.settings.reflection` values (`0`, `1`, or `2`) and the equivalent rosu-mods string values are both accepted.

## Building and testing

Build and test the Rust workspace:

```bash
cd native
cargo fmt --all -- --check
cargo test --workspace
cargo build --release -p rosu-pp-ffi
```

Build and test Java:

```bash
mvn -B test
mvn -B package -DskipTests
```

The release native library is written to `native/target/release/`. The Java artifact is written to `target/rosu-pp-java-0.0.1.jar`.
