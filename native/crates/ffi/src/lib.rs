//! Stable C ABI for the multi-version rosu-pp bridge.

use std::{
    cell::RefCell,
    collections::HashMap,
    ffi::{c_char, c_void},
    fs,
    panic::{AssertUnwindSafe, catch_unwind},
    ptr, slice, str,
    sync::{
        Arc, Mutex, OnceLock,
        atomic::{AtomicUsize, Ordering},
    },
};

use backend_202510::Backend202510;
use backend_202607::Backend202607;
use backend_api::{
    Algorithm, Backend, BackendError, Beatmap, DifficultyRequest, ModInput, Mode,
    PerformanceRequest, ScoreMode, ScoreState, option, score_field,
};

pub const ABI_VERSION: u32 = 2;
const MAX_MODS_JSON_LEN: usize = 1024 * 1024;

pub mod status {
    pub const OK: i32 = 0;
    pub const INVALID_ARGUMENT: i32 = -1;
    pub const INVALID_ALGORITHM: i32 = -2;
    pub const INVALID_HANDLE: i32 = -3;
    pub const ABI_MISMATCH: i32 = -4;
    pub const PARSE_ERROR: i32 = -5;
    pub const UNSUPPORTED_OPTION: i32 = -6;
    pub const CALCULATION_ERROR: i32 = -7;
    pub const END_OF_STREAM: i32 = -8;
    pub const PANIC: i32 = -127;
}

#[repr(C)]
pub struct RosuCalculator {
    _private: [u8; 0],
}

#[repr(C)]
pub struct RosuBeatmap {
    _private: [u8; 0],
}

#[repr(C)]
pub struct RosuGradual {
    _private: [u8; 0],
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct RosuAlgorithmInfo {
    pub struct_size: u32,
    pub abi_version: u32,
    pub algorithm_id: u32,
    pub reserved: u32,
    pub capabilities: u64,
    pub name: *const c_char,
    pub version: *const c_char,
    pub details: *const c_char,
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct RosuDifficultyRequest {
    pub struct_size: u32,
    pub abi_version: u32,
    pub mode: i32,
    pub score_mode: u32,
    pub mods: u64,
    pub option_flags: u64,
    pub clock_rate: f64,
    pub ar: f64,
    pub od: f64,
    pub cs: f64,
    pub hp: f64,
    pub passed_objects: u32,
    pub reserved: u32,
    pub mods_json: *const u8,
    pub mods_json_len: usize,
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct RosuPerformanceRequest {
    pub difficulty: RosuDifficultyRequest,
    pub score_fields: u64,
    pub accuracy: f64,
    pub combo: u32,
    pub misses: u32,
    pub n300: u32,
    pub n100: u32,
    pub n50: u32,
    pub n_geki: u32,
    pub n_katu: u32,
    pub large_tick_hits: u32,
    pub small_tick_hits: u32,
    pub slider_end_hits: u32,
    pub legacy_total_score: u32,
    pub reserved: u32,
}

#[repr(C)]
#[derive(Clone, Copy)]
pub struct RosuScoreState {
    pub struct_size: u32,
    pub abi_version: u32,
    pub max_combo: u32,
    pub large_tick_hits: u32,
    pub small_tick_hits: u32,
    pub slider_end_hits: u32,
    pub n_geki: u32,
    pub n_katu: u32,
    pub n300: u32,
    pub n100: u32,
    pub n50: u32,
    pub misses: u32,
    pub legacy_total_score: u32,
    pub reserved: u32,
    pub fields: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
pub struct RosuDifficultyResult {
    pub struct_size: u32,
    pub abi_version: u32,
    pub algorithm_id: u32,
    pub mode: i32,
    pub capabilities: u64,
    pub present: u64,
    pub algorithm_name: *const c_char,
    pub algorithm_version: *const c_char,
    pub stars: f64,
    pub aim: f64,
    pub speed: f64,
    pub flashlight: f64,
    pub reading: f64,
    pub slider_factor: f64,
    pub aim_difficult_slider_count: f64,
    pub speed_note_count: f64,
    pub reading_difficult_note_count: f64,
    pub aim_top_weighted_slider_factor: f64,
    pub speed_top_weighted_slider_factor: f64,
    pub aim_difficult_strain_count: f64,
    pub speed_difficult_strain_count: f64,
    pub nested_score_per_object: f64,
    pub legacy_score_base_multiplier: f64,
    pub maximum_legacy_combo_score: f64,
    pub stamina: f64,
    pub rhythm: f64,
    pub color: f64,
    pub taiko_reading: f64,
    pub mono_stamina_factor: f64,
    pub mechanical_difficulty: f64,
    pub consistency_factor: f64,
    pub catch_preempt: f64,
    pub ar: f64,
    pub od: f64,
    pub hp: f64,
    pub great_hit_window: f64,
    pub ok_hit_window: f64,
    pub meh_hit_window: f64,
    pub max_combo: u32,
    pub n_objects: u32,
    pub n_circles: u32,
    pub n_sliders: u32,
    pub n_spinners: u32,
    pub n_fruits: u32,
    pub n_droplets: u32,
    pub n_tiny_droplets: u32,
    pub n_hold_notes: u32,
    pub is_convert: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
pub struct RosuPerformanceResult {
    pub struct_size: u32,
    pub abi_version: u32,
    pub algorithm_id: u32,
    pub mode: i32,
    pub capabilities: u64,
    pub present: u64,
    pub algorithm_name: *const c_char,
    pub algorithm_version: *const c_char,
    pub pp: f64,
    pub pp_aim: f64,
    pub pp_speed: f64,
    pub pp_accuracy: f64,
    pub pp_flashlight: f64,
    pub pp_reading: f64,
    pub pp_difficulty: f64,
    pub effective_miss_count: f64,
    pub speed_deviation: f64,
    pub estimated_unstable_rate: f64,
    pub combo_based_estimated_miss_count: f64,
    pub score_based_estimated_miss_count: f64,
    pub aim_estimated_slider_breaks: f64,
    pub speed_estimated_slider_breaks: f64,
    pub difficulty: RosuDifficultyResult,
}

struct CalculatorEntry {
    algorithm_id: u32,
}

enum GradualKind {
    Difficulty(DifficultyRequest),
    Performance(DifficultyRequest),
}

struct GradualEntry {
    map: Arc<dyn Beatmap>,
    kind: GradualKind,
    next_index: usize,
}

static NEXT_HANDLE: AtomicUsize = AtomicUsize::new(1);
static CALCULATORS: OnceLock<Mutex<HashMap<usize, CalculatorEntry>>> = OnceLock::new();
static BEATMAPS: OnceLock<Mutex<HashMap<usize, Arc<dyn Beatmap>>>> = OnceLock::new();
static GRADUALS: OnceLock<Mutex<HashMap<usize, GradualEntry>>> = OnceLock::new();

thread_local! {
    static LAST_ERROR: RefCell<Vec<u8>> = const { RefCell::new(Vec::new()) };
}

const NAME_202510: &[u8] = b"REWORK_202510\0";
const VERSION_202510: &[u8] = b"rework-202510-rosu-pp-4.0.1\0";
const DETAILS_202510: &[u8] =
    b"Unmodified crates.io rosu-pp 4.0.1; osu!lazer 2025-10 difficulty/performance\0";
const NAME_202607: &[u8] = b"REWORK_20260706\0";
const VERSION_202607: &[u8] = b"rework-20260706-9a073d2\0";
const DETAILS_202607: &[u8] =
    b"Apeuriox/rosu-pp 9a073d2; osu!standard precision/slider fixes plus 2026 taiko and catch updates\0";

fn calculators() -> &'static Mutex<HashMap<usize, CalculatorEntry>> {
    CALCULATORS.get_or_init(|| Mutex::new(HashMap::new()))
}
fn beatmaps() -> &'static Mutex<HashMap<usize, Arc<dyn Beatmap>>> {
    BEATMAPS.get_or_init(|| Mutex::new(HashMap::new()))
}
fn graduals() -> &'static Mutex<HashMap<usize, GradualEntry>> {
    GRADUALS.get_or_init(|| Mutex::new(HashMap::new()))
}
fn next_handle<T>() -> *mut T {
    NEXT_HANDLE.fetch_add(1, Ordering::Relaxed) as *mut T
}
fn handle_id<T>(handle: *const T) -> Option<usize> {
    let id = handle as usize;
    (id != 0).then_some(id)
}

fn set_error(message: impl AsRef<str>) {
    LAST_ERROR.with(|slot| {
        let mut bytes = slot.borrow_mut();
        bytes.clear();
        bytes.extend_from_slice(message.as_ref().as_bytes());
    });
}

fn clear_error() {
    LAST_ERROR.with(|slot| slot.borrow_mut().clear());
}

fn algorithm(index: u32) -> Option<&'static Algorithm> {
    match index {
        0 => Some(&backend_202510::ALGORITHM),
        1 => Some(&backend_202607::ALGORITHM),
        _ => None,
    }
}

fn backend(id: u32) -> Option<&'static dyn Backend> {
    static OLD: Backend202510 = Backend202510;
    static NEW: Backend202607 = Backend202607;
    match id {
        backend_api::ALGORITHM_202510 => Some(&OLD),
        backend_api::ALGORITHM_202607 => Some(&NEW),
        _ => None,
    }
}

fn algorithm_strings(id: u32) -> (*const c_char, *const c_char, *const c_char) {
    match id {
        backend_api::ALGORITHM_202510 => (
            NAME_202510.as_ptr().cast(),
            VERSION_202510.as_ptr().cast(),
            DETAILS_202510.as_ptr().cast(),
        ),
        backend_api::ALGORITHM_202607 => (
            NAME_202607.as_ptr().cast(),
            VERSION_202607.as_ptr().cast(),
            DETAILS_202607.as_ptr().cast(),
        ),
        _ => (ptr::null(), ptr::null(), ptr::null()),
    }
}

fn status_for(error: &BackendError) -> i32 {
    match error {
        BackendError::AbiMismatch(_) => status::ABI_MISMATCH,
        BackendError::InvalidRequest(_) => status::INVALID_ARGUMENT,
        BackendError::UnsupportedOption(_) => status::UNSUPPORTED_OPTION,
        BackendError::Parse(_) => status::PARSE_ERROR,
        BackendError::Calculation(_) => status::CALCULATION_ERROR,
        BackendError::EndOfStream => status::END_OF_STREAM,
    }
}

fn fail(error: BackendError) -> i32 {
    let code = status_for(&error);
    set_error(error.to_string());
    code
}

fn guard_i32(f: impl FnOnce() -> i32) -> i32 {
    clear_error();
    match catch_unwind(AssertUnwindSafe(f)) {
        Ok(value) => value,
        Err(_) => {
            set_error("native panic was caught at the C ABI boundary");
            status::PANIC
        }
    }
}

fn guard_ptr<T>(f: impl FnOnce() -> *mut T) -> *mut T {
    clear_error();
    match catch_unwind(AssertUnwindSafe(f)) {
        Ok(value) => value,
        Err(_) => {
            set_error("native panic was caught at the C ABI boundary");
            ptr::null_mut()
        }
    }
}

fn parse_difficulty(raw: &RosuDifficultyRequest) -> Result<DifficultyRequest, BackendError> {
    if raw.struct_size < size_of::<RosuDifficultyRequest>() as u32 || raw.abi_version != ABI_VERSION
    {
        return Err(BackendError::AbiMismatch(
            "difficulty request ABI/size mismatch".into(),
        ));
    }
    let score_mode = match raw.score_mode {
        0 => ScoreMode::Default,
        1 => ScoreMode::Stable,
        2 => ScoreMode::Lazer,
        value => {
            return Err(BackendError::InvalidRequest(format!(
                "invalid score mode {value}"
            )));
        }
    };
    let mods = if raw.option_flags & option::MODS_JSON != 0 {
        if raw.mods != 0 {
            return Err(BackendError::InvalidRequest(
                "legacy mods and mods JSON are mutually exclusive".into(),
            ));
        }
        if raw.mods_json.is_null() || raw.mods_json_len == 0 {
            return Err(BackendError::InvalidRequest(
                "mods JSON pointer must be non-null and length must be greater than zero".into(),
            ));
        }
        if raw.mods_json_len > MAX_MODS_JSON_LEN {
            return Err(BackendError::InvalidRequest(format!(
                "mods JSON exceeds the {MAX_MODS_JSON_LEN}-byte limit"
            )));
        }
        let bytes = unsafe { slice::from_raw_parts(raw.mods_json, raw.mods_json_len) };
        let json = str::from_utf8(bytes).map_err(|error| {
            BackendError::InvalidRequest(format!("mods JSON is not UTF-8: {error}"))
        })?;
        if json.trim().is_empty() {
            return Err(BackendError::InvalidRequest(
                "mods JSON must not be blank".into(),
            ));
        }
        ModInput::Json(json.to_owned())
    } else {
        if !raw.mods_json.is_null() || raw.mods_json_len != 0 {
            return Err(BackendError::InvalidRequest(
                "mods JSON pointer and length require ROSU_OPT_MODS_JSON".into(),
            ));
        }
        let bits = u32::try_from(raw.mods).map_err(|_| {
            BackendError::UnsupportedOption("mods bits above bit 31 are reserved".into())
        })?;
        ModInput::Legacy(bits)
    };
    Ok(DifficultyRequest {
        mode: Mode::from_i32(raw.mode)?,
        mods,
        option_flags: raw.option_flags,
        score_mode,
        clock_rate: raw.clock_rate,
        ar: raw.ar,
        od: raw.od,
        cs: raw.cs,
        hp: raw.hp,
        passed_objects: raw.passed_objects,
    })
}

fn parse_performance(raw: &RosuPerformanceRequest) -> Result<PerformanceRequest, BackendError> {
    Ok(PerformanceRequest {
        difficulty: parse_difficulty(&raw.difficulty)?,
        score_fields: raw.score_fields,
        accuracy: raw.accuracy,
        combo: raw.combo,
        misses: raw.misses,
        n300: raw.n300,
        n100: raw.n100,
        n50: raw.n50,
        n_geki: raw.n_geki,
        n_katu: raw.n_katu,
        large_tick_hits: raw.large_tick_hits,
        small_tick_hits: raw.small_tick_hits,
        slider_end_hits: raw.slider_end_hits,
        legacy_total_score: raw.legacy_total_score,
    })
}

fn parse_score_state(raw: &RosuScoreState) -> Result<ScoreState, BackendError> {
    if raw.struct_size < size_of::<RosuScoreState>() as u32 || raw.abi_version != ABI_VERSION {
        return Err(BackendError::AbiMismatch(
            "score state ABI/size mismatch".into(),
        ));
    }
    Ok(ScoreState {
        max_combo: raw.max_combo,
        large_tick_hits: raw.large_tick_hits,
        small_tick_hits: raw.small_tick_hits,
        slider_end_hits: raw.slider_end_hits,
        n_geki: raw.n_geki,
        n_katu: raw.n_katu,
        n300: raw.n300,
        n100: raw.n100,
        n50: raw.n50,
        misses: raw.misses,
        legacy_total_score: (raw.fields & score_field::LEGACY_TOTAL_SCORE != 0)
            .then_some(raw.legacy_total_score),
    })
}

fn get_calculator(handle: *const RosuCalculator) -> Result<u32, i32> {
    let Some(id) = handle_id(handle) else {
        set_error("calculator handle is null");
        return Err(status::INVALID_HANDLE);
    };
    calculators()
        .lock()
        .unwrap()
        .get(&id)
        .map(|e| e.algorithm_id)
        .ok_or_else(|| {
            set_error("calculator handle is invalid or closed");
            status::INVALID_HANDLE
        })
}

fn get_map(handle: *const RosuBeatmap, expected_algorithm: u32) -> Result<Arc<dyn Beatmap>, i32> {
    let Some(id) = handle_id(handle) else {
        set_error("beatmap handle is null");
        return Err(status::INVALID_HANDLE);
    };
    let map = beatmaps()
        .lock()
        .unwrap()
        .get(&id)
        .cloned()
        .ok_or_else(|| {
            set_error("beatmap handle is invalid or closed");
            status::INVALID_HANDLE
        })?;
    if map.algorithm().id != expected_algorithm {
        set_error("beatmap and calculator belong to different algorithm versions");
        return Err(status::INVALID_HANDLE);
    }
    Ok(map)
}

fn write_difficulty(
    result: backend_api::DifficultyResult,
    algorithm: &'static Algorithm,
    out: *mut RosuDifficultyResult,
) -> i32 {
    if out.is_null() {
        set_error("difficulty result pointer is null");
        return status::INVALID_ARGUMENT;
    }
    let (provided_size, provided_abi) = unsafe { ((*out).struct_size, (*out).abi_version) };
    if provided_size < size_of::<RosuDifficultyResult>() as u32 || provided_abi != ABI_VERSION {
        set_error("difficulty result ABI/size mismatch");
        return status::ABI_MISMATCH;
    }
    let (name, version, _) = algorithm_strings(algorithm.id);
    let value = RosuDifficultyResult {
        struct_size: size_of::<RosuDifficultyResult>() as u32,
        abi_version: ABI_VERSION,
        algorithm_id: algorithm.id,
        mode: result.mode,
        capabilities: algorithm.capabilities,
        present: result.present,
        algorithm_name: name,
        algorithm_version: version,
        stars: result.stars,
        aim: result.aim,
        speed: result.speed,
        flashlight: result.flashlight,
        reading: result.reading,
        slider_factor: result.slider_factor,
        aim_difficult_slider_count: result.aim_difficult_slider_count,
        speed_note_count: result.speed_note_count,
        reading_difficult_note_count: result.reading_difficult_note_count,
        aim_top_weighted_slider_factor: result.aim_top_weighted_slider_factor,
        speed_top_weighted_slider_factor: result.speed_top_weighted_slider_factor,
        aim_difficult_strain_count: result.aim_difficult_strain_count,
        speed_difficult_strain_count: result.speed_difficult_strain_count,
        nested_score_per_object: result.nested_score_per_object,
        legacy_score_base_multiplier: result.legacy_score_base_multiplier,
        maximum_legacy_combo_score: result.maximum_legacy_combo_score,
        stamina: result.stamina,
        rhythm: result.rhythm,
        color: result.color,
        taiko_reading: result.taiko_reading,
        mono_stamina_factor: result.mono_stamina_factor,
        mechanical_difficulty: result.mechanical_difficulty,
        consistency_factor: result.consistency_factor,
        catch_preempt: result.catch_preempt,
        ar: result.ar,
        od: result.od,
        hp: result.hp,
        great_hit_window: result.great_hit_window,
        ok_hit_window: result.ok_hit_window,
        meh_hit_window: result.meh_hit_window,
        max_combo: result.max_combo,
        n_objects: result.n_objects,
        n_circles: result.n_circles,
        n_sliders: result.n_sliders,
        n_spinners: result.n_spinners,
        n_fruits: result.n_fruits,
        n_droplets: result.n_droplets,
        n_tiny_droplets: result.n_tiny_droplets,
        n_hold_notes: result.n_hold_notes,
        is_convert: u32::from(result.is_convert),
    };
    unsafe { ptr::write(out, value) };
    status::OK
}

fn make_difficulty(
    result: backend_api::DifficultyResult,
    algorithm: &'static Algorithm,
) -> RosuDifficultyResult {
    let mut out = RosuDifficultyResult {
        struct_size: size_of::<RosuDifficultyResult>() as u32,
        abi_version: ABI_VERSION,
        ..RosuDifficultyResult::default()
    };
    let code = write_difficulty(result, algorithm, &mut out);
    debug_assert_eq!(code, status::OK);
    out
}

fn write_performance(
    result: backend_api::PerformanceResult,
    algorithm: &'static Algorithm,
    out: *mut RosuPerformanceResult,
) -> i32 {
    if out.is_null() {
        set_error("performance result pointer is null");
        return status::INVALID_ARGUMENT;
    }
    let (provided_size, provided_abi) = unsafe { ((*out).struct_size, (*out).abi_version) };
    if provided_size < size_of::<RosuPerformanceResult>() as u32 || provided_abi != ABI_VERSION {
        set_error("performance result ABI/size mismatch");
        return status::ABI_MISMATCH;
    }
    let (name, version, _) = algorithm_strings(algorithm.id);
    let value = RosuPerformanceResult {
        struct_size: size_of::<RosuPerformanceResult>() as u32,
        abi_version: ABI_VERSION,
        algorithm_id: algorithm.id,
        mode: result.difficulty.mode,
        capabilities: algorithm.capabilities,
        present: result.present,
        algorithm_name: name,
        algorithm_version: version,
        pp: result.pp,
        pp_aim: result.pp_aim,
        pp_speed: result.pp_speed,
        pp_accuracy: result.pp_accuracy,
        pp_flashlight: result.pp_flashlight,
        pp_reading: result.pp_reading,
        pp_difficulty: result.pp_difficulty,
        effective_miss_count: result.effective_miss_count,
        speed_deviation: result.speed_deviation.unwrap_or_default(),
        estimated_unstable_rate: result.estimated_unstable_rate.unwrap_or_default(),
        combo_based_estimated_miss_count: result.combo_based_estimated_miss_count,
        score_based_estimated_miss_count: result
            .score_based_estimated_miss_count
            .unwrap_or_default(),
        aim_estimated_slider_breaks: result.aim_estimated_slider_breaks,
        speed_estimated_slider_breaks: result.speed_estimated_slider_breaks,
        difficulty: make_difficulty(result.difficulty, algorithm),
    };
    unsafe { ptr::write(out, value) };
    status::OK
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_abi_version() -> u32 {
    ABI_VERSION
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_algorithm_count() -> u32 {
    2
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_algorithm_info(index: u32, out: *mut RosuAlgorithmInfo) -> i32 {
    guard_i32(|| {
        if out.is_null() {
            set_error("algorithm info result pointer is null");
            return status::INVALID_ARGUMENT;
        }
        let (provided_size, provided_abi) = unsafe { ((*out).struct_size, (*out).abi_version) };
        if provided_size < size_of::<RosuAlgorithmInfo>() as u32 || provided_abi != ABI_VERSION {
            set_error("algorithm info result ABI/size mismatch");
            return status::ABI_MISMATCH;
        }
        let Some(algorithm) = algorithm(index) else {
            set_error(format!("algorithm index {index} is out of range"));
            return status::INVALID_ALGORITHM;
        };
        let (name, version, details) = algorithm_strings(algorithm.id);
        unsafe {
            ptr::write(
                out,
                RosuAlgorithmInfo {
                    struct_size: size_of::<RosuAlgorithmInfo>() as u32,
                    abi_version: ABI_VERSION,
                    algorithm_id: algorithm.id,
                    reserved: 0,
                    capabilities: algorithm.capabilities,
                    name,
                    version,
                    details,
                },
            )
        };
        status::OK
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_calculator_create(algorithm_id: u32) -> *mut RosuCalculator {
    guard_ptr(|| {
        if backend(algorithm_id).is_none() {
            set_error(format!("unknown algorithm id {algorithm_id}"));
            return ptr::null_mut();
        }
        let handle = next_handle();
        calculators()
            .lock()
            .unwrap()
            .insert(handle as usize, CalculatorEntry { algorithm_id });
        handle
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_calculator_free(handle: *mut RosuCalculator) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        if let Some(id) = handle_id(handle) {
            calculators().lock().unwrap().remove(&id);
        }
    }));
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_beatmap_from_bytes(
    calculator: *const RosuCalculator,
    bytes: *const u8,
    len: usize,
) -> *mut RosuBeatmap {
    guard_ptr(|| {
        let algorithm_id = match get_calculator(calculator) {
            Ok(id) => id,
            Err(_) => return ptr::null_mut(),
        };
        if bytes.is_null() || len == 0 {
            set_error("beatmap bytes must be non-null and non-empty");
            return ptr::null_mut();
        }
        let data = unsafe { slice::from_raw_parts(bytes, len) };
        let parsed = match backend(algorithm_id).unwrap().parse(data) {
            Ok(map) => Arc::<dyn Beatmap>::from(map),
            Err(error) => {
                fail(error);
                return ptr::null_mut();
            }
        };
        let handle = next_handle();
        beatmaps().lock().unwrap().insert(handle as usize, parsed);
        handle
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_beatmap_from_path(
    calculator: *const RosuCalculator,
    path: *const u8,
    len: usize,
) -> *mut RosuBeatmap {
    guard_ptr(|| {
        if path.is_null() || len == 0 {
            set_error("path UTF-8 bytes must be non-null and non-empty");
            return ptr::null_mut();
        }
        let path = match str::from_utf8(unsafe { slice::from_raw_parts(path, len) }) {
            Ok(path) => path,
            Err(error) => {
                set_error(format!("path is not valid UTF-8: {error}"));
                return ptr::null_mut();
            }
        };
        let data = match fs::read(path) {
            Ok(data) => data,
            Err(error) => {
                set_error(format!("failed to read beatmap path: {error}"));
                return ptr::null_mut();
            }
        };
        rosu_beatmap_from_bytes(calculator, data.as_ptr(), data.len())
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_beatmap_free(handle: *mut RosuBeatmap) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        if let Some(id) = handle_id(handle) {
            beatmaps().lock().unwrap().remove(&id);
        }
    }));
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_calculate_difficulty(
    calculator: *const RosuCalculator,
    beatmap: *const RosuBeatmap,
    request: *const RosuDifficultyRequest,
    out: *mut RosuDifficultyResult,
) -> i32 {
    guard_i32(|| {
        let algorithm_id = match get_calculator(calculator) {
            Ok(id) => id,
            Err(code) => return code,
        };
        let map = match get_map(beatmap, algorithm_id) {
            Ok(map) => map,
            Err(code) => return code,
        };
        let Some(request) = (unsafe { request.as_ref() }) else {
            set_error("difficulty request is null");
            return status::INVALID_ARGUMENT;
        };
        let request = match parse_difficulty(request) {
            Ok(req) => req,
            Err(error) => return fail(error),
        };
        match map.difficulty(&request) {
            Ok(result) => write_difficulty(result, map.algorithm(), out),
            Err(error) => fail(error),
        }
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_calculate_performance(
    calculator: *const RosuCalculator,
    beatmap: *const RosuBeatmap,
    request: *const RosuPerformanceRequest,
    out: *mut RosuPerformanceResult,
) -> i32 {
    guard_i32(|| {
        let algorithm_id = match get_calculator(calculator) {
            Ok(id) => id,
            Err(code) => return code,
        };
        let map = match get_map(beatmap, algorithm_id) {
            Ok(map) => map,
            Err(code) => return code,
        };
        let Some(request) = (unsafe { request.as_ref() }) else {
            set_error("performance request is null");
            return status::INVALID_ARGUMENT;
        };
        let request = match parse_performance(request) {
            Ok(req) => req,
            Err(error) => return fail(error),
        };
        match map.performance(&request) {
            Ok(result) => write_performance(result, map.algorithm(), out),
            Err(error) => fail(error),
        }
    })
}

fn gradual_create(
    calculator: *const RosuCalculator,
    beatmap: *const RosuBeatmap,
    request: *const RosuDifficultyRequest,
    performance: bool,
) -> *mut RosuGradual {
    guard_ptr(|| {
        let algorithm_id = match get_calculator(calculator) {
            Ok(id) => id,
            Err(_) => return ptr::null_mut(),
        };
        let map = match get_map(beatmap, algorithm_id) {
            Ok(map) => map,
            Err(_) => return ptr::null_mut(),
        };
        let Some(request) = (unsafe { request.as_ref() }) else {
            set_error("difficulty request is null");
            return ptr::null_mut();
        };
        let mut request = match parse_difficulty(request) {
            Ok(req) => req,
            Err(error) => {
                fail(error);
                return ptr::null_mut();
            }
        };
        request.option_flags &= !option::PASSED_OBJECTS;
        let kind = if performance {
            GradualKind::Performance(request)
        } else {
            GradualKind::Difficulty(request)
        };
        let handle = next_handle();
        graduals().lock().unwrap().insert(
            handle as usize,
            GradualEntry {
                map,
                kind,
                next_index: 0,
            },
        );
        handle
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_gradual_difficulty_create(
    calculator: *const RosuCalculator,
    beatmap: *const RosuBeatmap,
    request: *const RosuDifficultyRequest,
) -> *mut RosuGradual {
    gradual_create(calculator, beatmap, request, false)
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_gradual_performance_create(
    calculator: *const RosuCalculator,
    beatmap: *const RosuBeatmap,
    request: *const RosuDifficultyRequest,
) -> *mut RosuGradual {
    gradual_create(calculator, beatmap, request, true)
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_gradual_difficulty_next(
    handle: *const RosuGradual,
    out: *mut RosuDifficultyResult,
) -> i32 {
    guard_i32(|| {
        let Some(id) = handle_id(handle) else {
            set_error("gradual handle is null");
            return status::INVALID_HANDLE;
        };
        let (map, request, index) = {
            let registry = graduals().lock().unwrap();
            let Some(entry) = registry.get(&id) else {
                set_error("gradual handle is invalid or closed");
                return status::INVALID_HANDLE;
            };
            let GradualKind::Difficulty(request) = &entry.kind else {
                set_error("handle is for gradual performance");
                return status::INVALID_HANDLE;
            };
            (Arc::clone(&entry.map), request.clone(), entry.next_index)
        };
        if index == usize::MAX {
            return fail(BackendError::EndOfStream);
        }
        match map.gradual_difficulty_at(&request, index) {
            Ok(result) => {
                if let Some(entry) = graduals().lock().unwrap().get_mut(&id) {
                    entry.next_index += 1;
                }
                write_difficulty(result, map.algorithm(), out)
            }
            Err(error) => fail(error),
        }
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_gradual_performance_next(
    handle: *const RosuGradual,
    state: *const RosuScoreState,
    out: *mut RosuPerformanceResult,
) -> i32 {
    guard_i32(|| {
        let Some(id) = handle_id(handle) else {
            set_error("gradual handle is null");
            return status::INVALID_HANDLE;
        };
        let Some(state) = (unsafe { state.as_ref() }) else {
            set_error("score state is null");
            return status::INVALID_ARGUMENT;
        };
        let state = match parse_score_state(state) {
            Ok(state) => state,
            Err(error) => return fail(error),
        };
        let (map, request, index) = {
            let registry = graduals().lock().unwrap();
            let Some(entry) = registry.get(&id) else {
                set_error("gradual handle is invalid or closed");
                return status::INVALID_HANDLE;
            };
            let GradualKind::Performance(request) = &entry.kind else {
                set_error("handle is for gradual difficulty");
                return status::INVALID_HANDLE;
            };
            (Arc::clone(&entry.map), request.clone(), entry.next_index)
        };
        if index == usize::MAX {
            return fail(BackendError::EndOfStream);
        }
        match map.gradual_performance_at(&request, index, &state) {
            Ok(result) => {
                if let Some(entry) = graduals().lock().unwrap().get_mut(&id) {
                    entry.next_index += 1;
                }
                write_performance(result, map.algorithm(), out)
            }
            Err(error) => fail(error),
        }
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_gradual_difficulty_last(
    handle: *const RosuGradual,
    out: *mut RosuDifficultyResult,
) -> i32 {
    guard_i32(|| {
        let Some(id) = handle_id(handle) else {
            set_error("gradual handle is null");
            return status::INVALID_HANDLE;
        };
        let (map, request) = {
            let registry = graduals().lock().unwrap();
            let Some(entry) = registry.get(&id) else {
                set_error("gradual handle is invalid or closed");
                return status::INVALID_HANDLE;
            };
            let GradualKind::Difficulty(request) = &entry.kind else {
                set_error("handle is for gradual performance");
                return status::INVALID_HANDLE;
            };
            (Arc::clone(&entry.map), request.clone())
        };
        match map.gradual_difficulty_last(&request) {
            Ok(result) => {
                if let Some(entry) = graduals().lock().unwrap().get_mut(&id) {
                    entry.next_index = usize::MAX;
                }
                write_difficulty(result, map.algorithm(), out)
            }
            Err(error) => fail(error),
        }
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_gradual_performance_last(
    handle: *const RosuGradual,
    state: *const RosuScoreState,
    out: *mut RosuPerformanceResult,
) -> i32 {
    guard_i32(|| {
        let Some(id) = handle_id(handle) else {
            set_error("gradual handle is null");
            return status::INVALID_HANDLE;
        };
        let Some(state) = (unsafe { state.as_ref() }) else {
            set_error("score state is null");
            return status::INVALID_ARGUMENT;
        };
        let state = match parse_score_state(state) {
            Ok(state) => state,
            Err(error) => return fail(error),
        };
        let (map, request) = {
            let registry = graduals().lock().unwrap();
            let Some(entry) = registry.get(&id) else {
                set_error("gradual handle is invalid or closed");
                return status::INVALID_HANDLE;
            };
            let GradualKind::Performance(request) = &entry.kind else {
                set_error("handle is for gradual difficulty");
                return status::INVALID_HANDLE;
            };
            (Arc::clone(&entry.map), request.clone())
        };
        match map.gradual_performance_last(&request, &state) {
            Ok(result) => {
                if let Some(entry) = graduals().lock().unwrap().get_mut(&id) {
                    entry.next_index = usize::MAX;
                }
                write_performance(result, map.algorithm(), out)
            }
            Err(error) => fail(error),
        }
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_gradual_free(handle: *mut RosuGradual) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        if let Some(id) = handle_id(handle) {
            graduals().lock().unwrap().remove(&id);
        }
    }));
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_last_error_length() -> usize {
    LAST_ERROR.with(|slot| slot.borrow().len())
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_last_error_copy(buffer: *mut u8, capacity: usize) -> usize {
    LAST_ERROR.with(|slot| {
        let bytes = slot.borrow();
        let count = bytes.len().min(capacity);
        if count > 0 && !buffer.is_null() {
            unsafe { ptr::copy_nonoverlapping(bytes.as_ptr(), buffer, count) };
        }
        bytes.len()
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_test_force_panic() -> i32 {
    guard_i32(|| panic!("intentional ABI containment test"))
}

#[unsafe(no_mangle)]
pub extern "C" fn rosu_handle_is_valid(handle: *const c_void, kind: u32) -> i32 {
    guard_i32(|| {
        let Some(id) = handle_id(handle) else {
            return 0;
        };
        match kind {
            1 => i32::from(calculators().lock().unwrap().contains_key(&id)),
            2 => i32::from(beatmaps().lock().unwrap().contains_key(&id)),
            3 => i32::from(graduals().lock().unwrap().contains_key(&id)),
            _ => 0,
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn discovers_both_algorithms() {
        assert_eq!(rosu_algorithm_count(), 2);
        let mut info = RosuAlgorithmInfo {
            struct_size: size_of::<RosuAlgorithmInfo>() as u32,
            abi_version: ABI_VERSION,
            algorithm_id: 0,
            reserved: 0,
            capabilities: 0,
            name: ptr::null(),
            version: ptr::null(),
            details: ptr::null(),
        };
        assert_eq!(rosu_algorithm_info(0, &mut info), status::OK);
        assert_eq!(info.algorithm_id, backend_api::ALGORITHM_202510);
        assert_eq!(rosu_algorithm_info(1, &mut info), status::OK);
        assert_eq!(info.algorithm_id, backend_api::ALGORITHM_202607);
    }

    #[test]
    fn invalid_and_double_free_are_safe() {
        assert!(rosu_calculator_create(123).is_null());
        let handle = rosu_calculator_create(backend_api::ALGORITHM_202510);
        assert!(!handle.is_null());
        rosu_calculator_free(handle);
        rosu_calculator_free(handle);
        assert_eq!(rosu_handle_is_valid(handle.cast(), 1), 0);
    }

    #[test]
    fn panic_is_contained() {
        assert_eq!(rosu_test_force_panic(), status::PANIC);
    }

    #[test]
    fn structured_mods_are_copied_and_validated() {
        let json = br#"[{"acronym":"DT","settings":{"speed_change":1.2}}]"#;
        let raw = RosuDifficultyRequest {
            struct_size: size_of::<RosuDifficultyRequest>() as u32,
            abi_version: ABI_VERSION,
            mode: 0,
            score_mode: 0,
            mods: 0,
            option_flags: option::MODS_JSON,
            clock_rate: 0.0,
            ar: 0.0,
            od: 0.0,
            cs: 0.0,
            hp: 0.0,
            passed_objects: 0,
            reserved: 0,
            mods_json: json.as_ptr(),
            mods_json_len: json.len(),
        };

        let parsed = parse_difficulty(&raw).unwrap();
        assert!(matches!(parsed.mods, ModInput::Json(value) if value.as_bytes() == json));

        let conflicting = RosuDifficultyRequest { mods: 8, ..raw };
        assert!(matches!(
            parse_difficulty(&conflicting),
            Err(BackendError::InvalidRequest(_))
        ));
    }

    #[test]
    fn null_and_forged_handles_return_errors() {
        let request = RosuDifficultyRequest {
            struct_size: size_of::<RosuDifficultyRequest>() as u32,
            abi_version: ABI_VERSION,
            mode: -1,
            score_mode: 0,
            mods: 0,
            option_flags: 0,
            clock_rate: 0.0,
            ar: 0.0,
            od: 0.0,
            cs: 0.0,
            hp: 0.0,
            passed_objects: 0,
            reserved: 0,
            mods_json: ptr::null(),
            mods_json_len: 0,
        };
        let mut result = RosuDifficultyResult::default();
        assert_eq!(
            rosu_calculate_difficulty(ptr::null(), ptr::null(), &request, &mut result),
            status::INVALID_HANDLE
        );
        let forged = 999_999usize as *const RosuCalculator;
        assert_eq!(
            rosu_calculate_difficulty(forged, ptr::null(), &request, &mut result),
            status::INVALID_HANDLE
        );
        rosu_beatmap_free(ptr::null_mut());
        rosu_gradual_free(ptr::null_mut());
    }
}
