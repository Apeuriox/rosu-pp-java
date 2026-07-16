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
- Docker Desktop in Linux-container mode when building both native platforms from Windows

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
6. The matching native library embedded in the JAR under `META-INF/native/<platform>/`

The platform library name is:

- Windows: `rosu_pp_ffi.dll`
- Linux: `librosu_pp_ffi.so`

The system property and environment variable must point to the native library file itself, not only to its containing directory. The fallback paths are resolved relative to the Java process working directory.

The universal JAR contains both native libraries:

```text
META-INF/native/windows-x86_64/rosu_pp_ffi.dll
META-INF/native/linux-x86_64/librosu_pp_ffi.so
```

When no external library is found, the loader extracts the library for the current platform to a private temporary directory and keeps it loaded for the lifetime of the JVM. Explicit external paths remain useful for development and overriding the bundled library.

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

The JSON is parsed for the beatmap's effective game mode by the selected backend. Legacy `.mods(Mods)` and `.modsJson(String)` are mutually exclusive. Malformed JSON, unknown settings, and unsupported mod acronyms produce an explicit native error instead of being ignored.

## Building and testing

Build and test the Rust workspace:

```bash
cd native
cargo fmt --all -- --check
cargo test --workspace
cargo build --release -p rosu-pp-ffi
```

Build and test Java:

```powershell
.\mvnw.cmd -B test
.\mvnw.cmd -B package -DskipTests
```

On Linux, use `./mvnw` instead of `mvnw.cmd`. Ensure that `JAVA_HOME` points to JDK 21 because Maven Wrapper gives `JAVA_HOME` precedence over the `java` executable found on `PATH`.

The platform-local release native library is written to `native/target/release/`. A normal Maven build produces the Java classes but only includes native libraries that were staged in `target/native-resources` before the Maven resource phase.

### Building a universal JAR locally on Windows

The included PowerShell script builds the Windows DLL locally, builds the Linux SO inside the fixed `rust:1.97.0-bookworm` Docker image, stages both libraries, runs the Java tests, and verifies the JAR entries:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
.\scripts\build-universal.ps1
```

The Docker Linux build uses Debian Bookworm and therefore targets glibc 2.36, which is below the project's GLIBC 2.38 ceiling. If a compatible Linux SO has already been built, Docker can be skipped:

```powershell
.\scripts\build-universal.ps1 `
  -LinuxLibrary 'C:\path\to\librosu_pp_ffi.so'
```

The resulting universal artifact is:

```text
target/rosu-pp-java-0.0.1.jar
```

A Linux host cannot normally produce the MSVC Windows DLL with the standard Rust toolchain. Build it on Windows or download the `rosu-pp-native-windows-x86_64` artifact from GitHub Actions, then stage it together with the locally built Linux SO using the same resource paths shown above.

## GitHub Actions artifacts

The workflow builds and tests both native platforms independently and then creates the universal JAR in a dependent aggregation job. Successful workflow runs expose these artifacts in the run's **Artifacts** section:

- `rosu-pp-native-linux-x86_64`
- `rosu-pp-native-windows-x86_64`
- `rosu-pp-java-universal`

`rosu-pp-java-universal` contains `rosu-pp-java-0.0.1.jar`, the public C header, and standalone copies of both native libraries. The JAR itself also contains both native libraries and normally requires no `rosu.pp.native.path` override.
