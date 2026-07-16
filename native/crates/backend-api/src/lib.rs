//! Version-independent requests, results, errors, and backend contracts.

use std::{error::Error, fmt};

pub const ALGORITHM_202510: u32 = 20_251_000;
pub const ALGORITHM_202607: u32 = 20_260_706;

pub mod capability {
    pub const DIFFICULTY: u64 = 1 << 0;
    pub const PERFORMANCE: u64 = 1 << 1;
    pub const GRADUAL_DIFFICULTY: u64 = 1 << 2;
    pub const GRADUAL_PERFORMANCE: u64 = 1 << 3;
    pub const READING_SKILL: u64 = 1 << 4;
    pub const LAZER_SLIDER_ACCURACY: u64 = 1 << 5;
    pub const STRUCTURED_MODS: u64 = 1 << 6;
    pub const ALL_BASE: u64 = DIFFICULTY
        | PERFORMANCE
        | GRADUAL_DIFFICULTY
        | GRADUAL_PERFORMANCE
        | LAZER_SLIDER_ACCURACY
        | STRUCTURED_MODS;
}

pub mod option {
    pub const CLOCK_RATE: u64 = 1 << 0;
    pub const AR: u64 = 1 << 1;
    pub const OD: u64 = 1 << 2;
    pub const CS: u64 = 1 << 3;
    pub const HP: u64 = 1 << 4;
    pub const PASSED_OBJECTS: u64 = 1 << 5;
    pub const SCORE_MODE: u64 = 1 << 6;
    pub const MODS_JSON: u64 = 1 << 7;
}

pub mod score_field {
    pub const ACCURACY: u64 = 1 << 0;
    pub const COMBO: u64 = 1 << 1;
    pub const MISSES: u64 = 1 << 2;
    pub const N300: u64 = 1 << 3;
    pub const N100: u64 = 1 << 4;
    pub const N50: u64 = 1 << 5;
    pub const N_GEKI: u64 = 1 << 6;
    pub const N_KATU: u64 = 1 << 7;
    pub const LARGE_TICK_HITS: u64 = 1 << 8;
    pub const SMALL_TICK_HITS: u64 = 1 << 9;
    pub const SLIDER_END_HITS: u64 = 1 << 10;
    pub const LEGACY_TOTAL_SCORE: u64 = 1 << 11;
}

pub mod present {
    pub const AIM: u64 = 1 << 0;
    pub const SPEED: u64 = 1 << 1;
    pub const FLASHLIGHT: u64 = 1 << 2;
    pub const READING: u64 = 1 << 3;
    pub const SLIDER_FACTOR: u64 = 1 << 4;
    pub const AIM_DIFFICULT_SLIDER_COUNT: u64 = 1 << 5;
    pub const SPEED_NOTE_COUNT: u64 = 1 << 6;
    pub const READING_DIFFICULT_NOTE_COUNT: u64 = 1 << 7;
    pub const AR: u64 = 1 << 8;
    pub const OD: u64 = 1 << 9;
    pub const HP: u64 = 1 << 10;
    pub const HIT_WINDOWS: u64 = 1 << 11;
    pub const OBJECT_COUNTS: u64 = 1 << 12;
    pub const IS_CONVERT: u64 = 1 << 13;
    pub const PP_AIM: u64 = 1 << 16;
    pub const PP_SPEED: u64 = 1 << 17;
    pub const PP_ACCURACY: u64 = 1 << 18;
    pub const PP_FLASHLIGHT: u64 = 1 << 19;
    pub const PP_READING: u64 = 1 << 20;
    pub const PP_DIFFICULTY: u64 = 1 << 21;
    pub const EFFECTIVE_MISS_COUNT: u64 = 1 << 22;
    pub const SPEED_DEVIATION: u64 = 1 << 23;
    pub const ESTIMATED_UNSTABLE_RATE: u64 = 1 << 24;
    pub const TAIKO_SKILLS: u64 = 1 << 25;
    pub const CATCH_PREEMPT: u64 = 1 << 26;
    pub const LEGACY_SCORE: u64 = 1 << 27;
    pub const COMBO_BASED_ESTIMATED_MISS_COUNT: u64 = 1 << 28;
    pub const SCORE_BASED_ESTIMATED_MISS_COUNT: u64 = 1 << 29;
    pub const AIM_ESTIMATED_SLIDER_BREAKS: u64 = 1 << 30;
    pub const SPEED_ESTIMATED_SLIDER_BREAKS: u64 = 1 << 31;
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
#[repr(i32)]
pub enum Mode {
    Osu = 0,
    Taiko = 1,
    Catch = 2,
    Mania = 3,
}

impl Mode {
    pub fn from_i32(value: i32) -> Result<Option<Self>, BackendError> {
        match value {
            -1 => Ok(None),
            0 => Ok(Some(Self::Osu)),
            1 => Ok(Some(Self::Taiko)),
            2 => Ok(Some(Self::Catch)),
            3 => Ok(Some(Self::Mania)),
            _ => Err(BackendError::InvalidRequest(format!(
                "invalid mode {value}"
            ))),
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
#[repr(u32)]
pub enum ScoreMode {
    Default = 0,
    Stable = 1,
    Lazer = 2,
}

#[derive(Clone, Copy, Debug)]
pub struct Algorithm {
    pub id: u32,
    pub name: &'static str,
    pub version: &'static str,
    pub details: &'static str,
    pub capabilities: u64,
}

#[derive(Clone, Debug)]
pub enum ModInput {
    Legacy(u32),
    Json(String),
}

#[derive(Clone, Debug)]
pub struct DifficultyRequest {
    pub mode: Option<Mode>,
    pub mods: ModInput,
    pub option_flags: u64,
    pub score_mode: ScoreMode,
    pub clock_rate: f64,
    pub ar: f64,
    pub od: f64,
    pub cs: f64,
    pub hp: f64,
    pub passed_objects: u32,
}

#[derive(Clone, Debug)]
pub struct PerformanceRequest {
    pub difficulty: DifficultyRequest,
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
}

#[derive(Clone, Debug, Default)]
pub struct DifficultyResult {
    pub mode: i32,
    pub present: u64,
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
    pub is_convert: bool,
}

#[derive(Clone, Debug, Default)]
pub struct PerformanceResult {
    pub present: u64,
    pub pp: f64,
    pub pp_aim: f64,
    pub pp_speed: f64,
    pub pp_accuracy: f64,
    pub pp_flashlight: f64,
    pub pp_reading: f64,
    pub pp_difficulty: f64,
    pub effective_miss_count: f64,
    pub speed_deviation: Option<f64>,
    pub estimated_unstable_rate: Option<f64>,
    pub combo_based_estimated_miss_count: f64,
    pub score_based_estimated_miss_count: Option<f64>,
    pub aim_estimated_slider_breaks: f64,
    pub speed_estimated_slider_breaks: f64,
    pub difficulty: DifficultyResult,
}

#[derive(Clone, Debug, Default)]
pub struct ScoreState {
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
    pub legacy_total_score: Option<u32>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum BackendError {
    AbiMismatch(String),
    InvalidRequest(String),
    UnsupportedOption(String),
    Parse(String),
    Calculation(String),
    EndOfStream,
}

impl fmt::Display for BackendError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::AbiMismatch(s) => write!(f, "ABI mismatch: {s}"),
            Self::InvalidRequest(s) => write!(f, "invalid request: {s}"),
            Self::UnsupportedOption(s) => write!(f, "unsupported option: {s}"),
            Self::Parse(s) => write!(f, "beatmap parse error: {s}"),
            Self::Calculation(s) => write!(f, "calculation error: {s}"),
            Self::EndOfStream => f.write_str("gradual calculation has no remaining objects"),
        }
    }
}

impl Error for BackendError {}

pub trait Beatmap: Send + Sync {
    fn algorithm(&self) -> &'static Algorithm;
    fn difficulty(&self, request: &DifficultyRequest) -> Result<DifficultyResult, BackendError>;
    fn performance(&self, request: &PerformanceRequest) -> Result<PerformanceResult, BackendError>;
    fn gradual_difficulty_at(
        &self,
        request: &DifficultyRequest,
        object_index: usize,
    ) -> Result<DifficultyResult, BackendError>;
    fn gradual_difficulty_last(
        &self,
        request: &DifficultyRequest,
    ) -> Result<DifficultyResult, BackendError>;
    fn gradual_performance_at(
        &self,
        request: &DifficultyRequest,
        object_index: usize,
        state: &ScoreState,
    ) -> Result<PerformanceResult, BackendError>;
    fn gradual_performance_last(
        &self,
        request: &DifficultyRequest,
        state: &ScoreState,
    ) -> Result<PerformanceResult, BackendError>;
}

pub trait Backend: Send + Sync {
    fn algorithm(&self) -> &'static Algorithm;
    fn parse(&self, bytes: &[u8]) -> Result<Box<dyn Beatmap>, BackendError>;
}
