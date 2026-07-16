# Native Bridge C ABI

公共声明位于 `include/rosu_pp_ffi.h`，当前 `rosu_abi_version()` 返回 `1`。所有结构都使用 Rust `#[repr(C)]` 和 C 固定宽度整数；Java 21 侧在初始化时校验关键结构尺寸。

## 字符串与结构扩展

- `RosuAlgorithmInfo` 和每份结果中的名称/版本指针都是只读、UTF-8、NUL 结尾的静态字符串，在动态库保持加载期间有效；调用方不得释放或修改。
- 路径输入是 `(uint8_t *, size_t)` UTF-8 字节，不要求 NUL 结尾。内存谱面输入在调用期间借用，函数返回前完成解析，调用方仍拥有原始字节。
- 请求、score state 和输出缓冲区都必须预先设置 `struct_size` 与 `abi_version`。当前版本在写入前拒绝尺寸过小或 ABI 不匹配的缓冲区，避免新 bridge 覆盖旧调用方的较小结构；成功后 bridge 会再次写回自身尺寸/版本。
- 新 ABI 会优先在结构尾部追加字段，并用 capability / present bits 表示语义可用性。

## Handle 所有权

`RosuCalculator *`、`RosuBeatmap *` 和 `RosuGradual *` 是不可解引用的 opaque token。它们不是暴露的 Rust 地址：bridge 使用单调 token 和加锁注册表保存真实对象。

- create/load 成功返回调用方独占 handle；失败返回 NULL，并设置线程局部错误。
- 对应的 `*_free` 可以接受 NULL、未知 token 或已释放 token，并且是幂等的。
- calculator 与 beatmap 都绑定算法 ID。计算时若二者版本不同，返回 `ROSU_INVALID_HANDLE`。
- gradual handle 同时绑定算法、谱面和请求；生命周期中没有修改 algorithm ID 的入口。
- `rosu_gradual_*_last` 直接调用对应 rosu-pp gradual calculator 的 `last`，高效处理所有剩余对象；成功后该 handle 到达流末尾。
- free 后继续调用返回 `ROSU_INVALID_HANDLE`，不会解引用悬空 Rust 指针。
- 注册表由互斥锁保护。单个 gradual handle 的位置只在成功计算后前进。

本实现选择“每个谱面 handle 绑定算法版本”，而不是让一个 handle 延迟保存原始字节。原因是所有权更明确：解析错误在 load 时发生，后端 Rust 类型永远只存在于其 adapter 内，且不会在一次渐进计算中临时切换解析实现。Java 如果要比较版本，应把相同路径或字节分别交给两个 calculator。

## 错误

状态码包括参数错误、未知算法、无效 handle、ABI 不匹配、解析错误、不支持选项、计算错误、渐进结束和 panic。错误文本是线程局部的：

1. 调用 `rosu_last_error_length()` 获取所需 UTF-8 字节数；
2. 分配缓冲区；
3. 调用 `rosu_last_error_copy()`；返回值始终是完整所需长度，复制量是 `min(capacity, length)`。

除 `free` 和只返回标量的查询外，所有导出入口都使用 `catch_unwind(AssertUnwindSafe(...))`。panic 被转换为 `ROSU_PANIC` 或 NULL，并留下 `native panic was caught at the C ABI boundary`，不会跨越 C ABI。

不支持的显式输入不会静默忽略。例如 Taiko 上的 AR/CS、Taiko 的 n50、非 osu!standard 的 slider tick/end 字段、非 Mania 的 n_geki 都返回 `ROSU_UNSUPPORTED_OPTION`。

## Mods

`RosuDifficultyRequest.mods` 是 bridge 自己定义的稳定 bitset，当前低 32 位与公开 osu! legacy mod bits 对应。adapter 再把该数值转换为每套 rosu-pp 的 `GameMods`；Rust enum 的判别值没有进入 ABI。高 32 位保留用于未来可扩展表示，当前非零时返回不支持。

## 结果

每份难度和表现结果都携带：algorithm ID、静态名称、详细版本字符串、capability bits、mode 和 present bits。结果结构是两个版本/四个模式属性的超集。调用方必须先检查 present bit；未标记字段的存储值为 0 只是初始化细节，不代表计算结果为 0。

202607 的公开 reading 属性会设置 `READING` / `READING_DIFFICULT_NOTE_COUNT` / `PP_READING`。202510 不设置这些位。HarmonicSkill 没有底层公开输出字段，因此不跨 ABI 暴露。
