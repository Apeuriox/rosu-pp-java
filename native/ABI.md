# Native Bridge C ABI

The public declarations are defined in `include/rosu_pp_ffi.h`. The current `rosu_abi_version()` value is `2`. All structures use Rust `#[repr(C)]` and fixed-width C integers. The Java 21 binding verifies the sizes of critical structures during initialization.

## Strings and structure extension

- The name and version pointers in `RosuAlgorithmInfo` and result structures refer to read-only, UTF-8, NUL-terminated static strings. They remain valid while the dynamic library is loaded and must not be modified or freed by the caller.
- Path inputs are provided as `(uint8_t *, size_t)` UTF-8 byte sequences and do not require a NUL terminator. In-memory beatmap bytes are borrowed for the duration of the call. Parsing finishes before the function returns, and ownership of the original bytes remains with the caller.
- Requests, score states, and output buffers must initialize both `struct_size` and `abi_version`. The bridge rejects undersized structures and ABI mismatches before writing to them, preventing a newer bridge from overwriting a smaller buffer supplied by an older caller. On success, the bridge writes its own structure size and ABI version back to output structures.
- Future ABI versions should append fields to the end of existing structures whenever possible. Capability and presence bits indicate whether the associated semantics or result fields are available.

## Handle ownership

`RosuCalculator *`, `RosuBeatmap *`, and `RosuGradual *` are opaque, non-dereferenceable tokens. They are not exposed Rust addresses. The bridge stores the actual objects in synchronized registries and identifies them through monotonically increasing tokens.

- A successful create or load operation returns a handle owned by the caller. Failure returns `NULL` and stores a thread-local error.
- The corresponding `*_free` functions accept `NULL`, unknown tokens, and previously released tokens. Releasing a handle is idempotent.
- Calculator and beatmap handles are both bound to an algorithm ID. Attempting to calculate with handles from different algorithm versions returns `ROSU_INVALID_HANDLE`.
- A gradual handle is bound to its algorithm, beatmap, and request. Its algorithm ID cannot be changed during its lifetime.
- `rosu_gradual_*_last` delegates to the corresponding rosu-pp gradual calculator's `last` operation to process all remaining objects efficiently. After success, the handle is at the end of the stream.
- Using a handle after it has been released returns `ROSU_INVALID_HANDLE`; the bridge never dereferences a stale Rust pointer.
- Registries are protected by mutexes. A gradual handle advances only after a successful calculation.

Each beatmap handle is bound to one algorithm version. The bridge does not keep raw beatmap bytes in a shared handle for delayed parsing. This gives clearer ownership: parsing errors occur during load, backend-specific Rust types remain inside their adapters, and a gradual calculation cannot switch parsing implementations midway through its lifetime. To compare algorithm versions from Java, load the same path or byte sequence separately through each calculator.

## Errors

Status codes cover invalid arguments, unknown algorithms, invalid handles, ABI mismatches, parsing errors, unsupported options, calculation errors, the end of a gradual stream, and native panics. Error text is thread-local:

1. Call `rosu_last_error_length()` to obtain the required UTF-8 byte count.
2. Allocate a buffer.
3. Call `rosu_last_error_copy()`. Its return value is always the complete required length; the number of bytes copied is `min(capacity, length)`.

Except for release functions and scalar-only queries, exported entry points use `catch_unwind(AssertUnwindSafe(...))`. A Rust panic becomes `ROSU_PANIC` or `NULL` and stores `native panic was caught at the C ABI boundary`. A panic never crosses the C ABI boundary.

Explicit unsupported input is never silently ignored. Examples include AR or CS overrides for Taiko, `n50` for Taiko, slider tick or end fields outside osu!standard, and `n_geki` outside Mania. These cases return `ROSU_UNSUPPORTED_OPTION`.

## Mods

`RosuDifficultyRequest.mods` is a bridge-defined stable bitset. Its lower 32 bits currently correspond to the public osu! legacy mod bits. The upper 32 bits are reserved and currently produce an unsupported-option error when nonzero.

Structured lazer mods are supplied through the trailing `(mods_json, mods_json_len)` fields with `ROSU_OPT_MODS_JSON` set. The JSON is borrowed UTF-8 data and does not require a NUL terminator. For a nonzero length, the pointer must address at least `mods_json_len` readable bytes for the entire call. The bridge copies the JSON into owned storage before returning, so a gradual handle never retains the caller's pointer. JSON input is limited to 1 MiB.

Structured JSON and the legacy bitset are mutually exclusive. A null pointer, empty JSON, invalid UTF-8, unknown setting fields, or a mod unsupported by the backend's pinned rosu-mods version produces an explicit error.

The FFI layer does not parse JSON into the Rust types of either algorithm version. After determining the beatmap's effective mode, each backend adapter uses its own rosu-mods dependency and a strict mode-specific deserialization seed to construct its corresponding `GameMods`. No Rust enum discriminant crosses a backend boundary or the C ABI.

## Results

Every difficulty and performance result contains the algorithm ID, static algorithm name, detailed version string, capability bits, game mode, and presence bits. Result structures form a superset of the attributes exposed by both algorithm versions across all four game modes.

Callers must check the appropriate presence bit before reading an optional field. A stored value of zero without its presence bit is only an initialization detail and does not represent a calculated zero.

The 202607 backend sets `READING`, `READING_DIFFICULT_NOTE_COUNT`, and `PP_READING` when it exposes the corresponding reading attributes. The 202510 backend does not set these bits. HarmonicSkill has no public output field in the underlying library and is therefore not exposed through the ABI.
