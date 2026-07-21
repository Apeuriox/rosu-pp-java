# rosu-pp-java

`rosu-pp-java` uses the Foreign Function & Memory API to call Rust `rosu-pp` implementations through a single native bridge. Multiple pinned algorithm versions can be loaded in the same process and selected at runtime. The same public Java API is published in two bytecode variants:

| JAR | Runtime | FFM status |
| --- | --- | --- |
| `rosu-pp-java-0.0.1.jar` | Java 22 and newer, including Java 26 | Final API, no preview bytecode |
| `rosu-pp-java-0.0.1-java21-preview.jar` | Java 21 only | Java 21 preview API |

Put exactly one of these JARs on an application's classpath. Java preview class files are tied to their exact JDK release, so the Java 21 variant cannot run on Java 22 or newer even when `--enable-preview` is supplied.

Five algorithm versions are currently supported:

| Java enum | Stable version key | Rust source |
| --- | --- | --- |
| `PRECSR_202210` | `precsr-202210-rosu-pp-1.0.0` | crates.io `rosu-pp = "=1.0.0"` |
| `REWORK_202411` | `rework-202411-rosu-pp-2.0.0` | crates.io `rosu-pp = "=2.0.0"` |
| `REWORK_202502` | `rework-202502-rosu-pp-3.1.0` | crates.io `rosu-pp = "=3.1.0"` |
| `REWORK_202510` | `rework-202510` | crates.io `rosu-pp = "=4.0.1"` |
| `REWORK_20260706` | `rework-20260706-9a073d2` | `Apeuriox/rosu-pp` commit `9a073d29611ef936b581a08d3c5b288ef5427301` |

All crates.io dependencies use exact `=` requirements and are additionally protected by the
checksums committed in `native/Cargo.lock`. `PRECSR_202210` predates structured lazer mods and
lazer slider-accuracy inputs; those options return `UnsupportedOptionException` instead of being
ignored. The other four versions support structured mods through the rosu-mods release matched to
their own rosu-pp dependency.

The algorithm version is part of every result and must also be included in application cache keys. Do not treat values produced by different algorithm versions as having identical semantics.

## Requirements

- Java 21 for the `java21-preview` JAR, or Java 22+ for the stable JAR
- Both JDK 21 and JDK 22+ when building both variants locally
- Rust 1.97 or newer when building the native bridge locally
- Windows x86_64 or Linux x86_64
- Docker Desktop in Linux-container mode when building both native platforms from Windows

Java 21 compilation and execution require:

```text
--enable-preview
--enable-native-access=ALL-UNNAMED
```

Java 22+ uses the final FFM API and must not use `--enable-preview`; it still needs native access:

```text
--enable-native-access=ALL-UNNAMED
```

The Maven profiles supply the correct flags for their own compilation and tests.

When importing this repository into IntelliJ IDEA, use JDK 22+ and the default Maven profile for
the stable build. To work on the Java 21 variant instead, switch the Project SDK to JDK 21, enable
the `java21-preview` Maven profile, and use Java 21 Preview as the language level. Applications
that consume the library only need to select the JAR matching their runtime; they must not include
both variants.

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

For example, Java 21 preview applications use:

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

Java 22+ applications use the stable JAR without the preview flag:

```powershell
java --enable-native-access=ALL-UNNAMED `
  -jar your-application.jar
```

```bash
java --enable-native-access=ALL-UNNAMED -jar your-application.jar
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

Build and test the Java 22+ stable variant with a JDK 22 or newer:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-22-or-newer'
.\mvnw.cmd -B test
.\mvnw.cmd -B package -DskipTests
```

Build and test the Java 21 preview variant with JDK 21:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
.\mvnw.cmd -B test -Pjava21-preview
.\mvnw.cmd -B package -Pjava21-preview -DskipTests
```

On Linux, use `./mvnw` instead of `mvnw.cmd`. Maven Wrapper gives `JAVA_HOME` precedence over the `java` executable found on `PATH`.

The platform-local release native library is written to `native/target/release/`. A normal Maven build produces the Java classes but only includes native libraries that were staged in `target/native-resources` before the Maven resource phase.

### Building both universal JARs locally on Windows

The PowerShell script builds the Windows DLL locally, builds the Linux SO inside the fixed `rust:1.97.0-bookworm` Docker image, stages both libraries, runs both Java test variants, and verifies their native resources and class-file versions. It requires an exact JDK 21 and a JDK 22 or newer:

```powershell
$env:JAVA21_HOME = 'C:\path\to\jdk-21'
$env:JAVA22_HOME = 'C:\path\to\jdk-22-or-newer'
.\scripts\build-universal.ps1
```

The homes can alternatively be passed as `-Java21Home` and `-Java22Home` parameters.

The Docker Linux build uses Debian Bookworm and therefore targets glibc 2.36, which is below the project's GLIBC 2.38 ceiling. If a compatible Linux SO has already been built, Docker can be skipped:

```powershell
.\scripts\build-universal.ps1 `
  -Java21Home 'C:\path\to\jdk-21' `
  -Java22Home 'C:\path\to\jdk-22-or-newer' `
  -LinuxLibrary 'C:\path\to\librosu_pp_ffi.so'
```

The resulting universal artifacts are:

```text
target/rosu-pp-java-0.0.1.jar                 # Java 22+
target/rosu-pp-java-0.0.1-java21-preview.jar  # Java 21 only
```

A Linux host cannot normally produce the MSVC Windows DLL with the standard Rust toolchain. Build it on Windows or download the `rosu-pp-native-windows-x86_64` artifact from GitHub Actions, then stage it together with the locally built Linux SO using the same resource paths shown above.

## GitHub Actions artifacts

The workflow builds and tests both native platforms, tests the preview build on Java 21, tests the stable build on Java 22 and Java 26, and then creates both universal JARs in a dependent aggregation job. Successful workflow runs expose these artifacts in the run's **Artifacts** section:

- `rosu-pp-native-linux-x86_64`
- `rosu-pp-native-windows-x86_64`
- `rosu-pp-java-universal`

`rosu-pp-java-universal` contains both Java JAR variants, the public C header, and standalone copies of both native libraries. Each JAR itself also contains both native libraries and normally requires no `rosu.pp.native.path` override.
