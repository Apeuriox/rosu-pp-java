# rosu-pp-java

一个不依赖 Spring、JNI、JNA 或第三方 Panama 封装的 Java 21 计算库。Java 通过 Foreign Function & Memory API（FFM preview）加载单个 Native Bridge，并在同一进程中按稳定算法 ID 选择两个固定的 rosu-pp 后端。

## 算法版本

| Java 枚举 | 稳定缓存键 | Rust 来源 | 特有能力 |
|---|---|---|---|
| `REWORK_202510` | `rework-202510` | crates.io `rosu-pp = "=4.0.1"` | 2025-10 难度与表现算法 |
| `REWORK_20260706` | `rework-20260706-9a073d2` | `Apeuriox/rosu-pp` commit `9a073d29611ef936b581a08d3c5b288ef5427301` | osu!standard 精度、slider 与 time-preempt 修复，包含 reading/HarmonicSkill，并更新 Taiko/Catch |

crates.io 4.0.1 的源码说明明确指向 2025-10 osu!lazer / osu!tools 算法更新；指定 Git commit 的直接基线也是该仓库的 `release: v4.0.1`。因此 `REWORK_202510` 没有替换为别的 rosu-pp 版本。两个包来源以及所有传递依赖都记录在 `native/Cargo.lock`。

## 使用

运行 Java 程序时必须启用 Java 21 preview 和 native access：

```text
java --enable-preview --enable-native-access=ALL-UNNAMED ...
```

Native loader 按以下顺序寻找动态库：系统属性 `rosu.pp.native.path`、环境变量 `ROSU_PP_NATIVE_PATH`、`native/target/release`、`target/native`、当前目录。

```java
import me.aloic.rosupp.*;

import java.nio.file.Path;

try(RosuPp legacy = RosuPp.forVersion(AlgorithmVersion.REWORK_202510);
RosuPp rework = RosuPp.forVersion(AlgorithmVersion.REWORK_20260706);
Beatmap oldMap = legacy.loadBeatmap(Path.of("map.osu"));
Beatmap newMap = rework.loadBeatmap(Path.of("map.osu"))){

DifficultyRequest difficultyRequest = DifficultyRequest.builder()
        .mods(Mods.of(Mods.HIDDEN, Mods.HARD_ROCK))
        .clockRate(1.0)
        .scoreMode(ScoreMode.LAZER)
        .build();

DifficultyResult oldDifficulty = legacy.calculateDifficulty(oldMap, difficultyRequest);
DifficultyResult newDifficulty = rework.calculateDifficulty(newMap, difficultyRequest);

PerformanceRequest score = PerformanceRequest.builder(difficultyRequest)
        .accuracy(98.75)
        .combo(800)
        .misses(2)
        .n300(900)
        .n100(20)
        .n50(3)
        .largeTickHits(420)
        .sliderEndHits(120)
        .build();

PerformanceResult oldPp = legacy.calculatePerformance(oldMap, score);
PerformanceResult newPp = rework.calculatePerformance(newMap, score);

// 不支持和值为 0 是两个不同状态。
    System.out.

println(oldDifficulty.readingOptional()); // OptionalDouble.empty
        System.out.

println(newDifficulty.readingOptional());
        System.out.

printf("old=%f, new=%f%n",oldPp.pp(),newPp.

pp());
        }
```

同一份 `.osu` 字节需要在不同 calculator 中分别加载。这样每个 `Beatmap` handle 永久绑定一个算法，两个不兼容的 Rust `Beatmap` 类型从不混用；与缓存相同，谱面解析身份也包含算法版本。

渐进计算同样绑定版本：

```java
try (GradualDifficulty gradual = rework.gradualDifficulty(newMap, difficultyRequest)) {
    DifficultyResult afterFirstObject = gradual.next();
    DifficultyResult finalResult = gradual.finish();
}
```

## 构建与测试

要求 Java 21 和固定 Rust 1.97.0。Maven Wrapper 固定为 3.3.2，并下载 Maven 3.9.9。

Windows PowerShell：

```powershell
cargo build --manifest-path native/Cargo.toml --release --locked -p rosu-pp-ffi
.\mvnw.cmd -B test
```

Linux：

```bash
cargo build --manifest-path native/Cargo.toml --release --locked -p rosu-pp-ffi
chmod +x mvnw
./mvnw -B test
```

其他检查：

```bash
cargo fmt --manifest-path native/Cargo.toml --all -- --check
cargo test --manifest-path native/Cargo.toml --workspace --locked
```

输出为 `native/target/release/rosu_pp_ffi.dll`（Windows）、`native/target/release/librosu_pp_ffi.so`（Linux）和 `target/rosu-pp-java-0.0.1.jar`。

## 测试范围

Rust 测试覆盖算法发现、无效 ID、空/伪造句柄、重复 free 和 panic containment。Java 集成测试覆盖同进程双版本、状态隔离、同谱面双解析、元数据/capability、错误选项、幂等 close、独立 golden 值，以及两个版本各自的完整/渐进最终值一致性。

golden 数据位于 `src/test/resources/golden/standard.osu`，两个版本分别保存 standard、Taiko、Catch 和 Mania 的预期值；更新其中一个后端不会重写另一个。测试还验证四个模式的完整计算与原生 gradual `finish()` 最终结果一致，并断言本次未更新的 Mania 在两个后端间保持一致。

## 已知边界

- 四个模式都支持完整与渐进难度/表现；从 osu!standard 转换到其他模式由各自 rosu-pp adapter 完成。非 standard 源谱面不能转换为另一模式，这是底层 rosu-pp 的限制。
- Mods ABI 是本项目定义的 32 位 osu! legacy mod bitset。它不会暴露 Rust enum 布局，但当前还不能表达带参数的 lazer structured mods；高 32 位保留，使用会返回 `UNSUPPORTED_OPTION`。
- `reading`、`readingDifficultNoteCount`、`ppReading` 只在 202607 后端标记为存在。HarmonicSkill 是该 commit 的内部计算机制，底层没有公开独立 harmonic 属性，因此没有伪造一个数值字段；其修复通过完整/渐进最终值一致测试验证。该固定版本还包含 Taiko rhythm 和 Catch movement 更新，Mania 保持 4.0.1 行为。
- `speedDeviation`、`estimatedUnstableRate` 和 `scoreBasedEstimatedMissCount` 使用 present bit 区分“不支持”和真实的 `0`。
- 当前目标是 Windows/Linux x86_64，opaque handle token 的实现也按 64 位进程验证。

完整 C ABI 所有权与错误规则见 [native/ABI.md](native/ABI.md)。
