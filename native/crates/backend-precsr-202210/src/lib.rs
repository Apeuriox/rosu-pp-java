//! Adapter for the unmodified crates.io rosu-pp 1.0.0 release.

use backend_api::{
    Algorithm, Backend, BackendError, Beatmap as BackendBeatmap, DifficultyRequest,
    DifficultyResult, ModInput, Mode, PerformanceRequest, PerformanceResult, ScoreState,
    capability, option, present, score_field,
};
use rosu::{
    Beatmap, Difficulty, Performance,
    any::{DifficultyAttributes, PerformanceAttributes, ScoreState as RosuScoreState},
    model::mode::GameMode,
};
use rosu_pp_precsr_202210 as rosu;

pub static ALGORITHM: Algorithm = Algorithm {
    id: backend_api::ALGORITHM_PRECSR_202210,
    name: "PRECSR_202210",
    version: "precsr-202210-rosu-pp-1.0.0",
    details: "Unmodified crates.io rosu-pp 1.0.0",
    capabilities: capability::DIFFICULTY
        | capability::PERFORMANCE
        | capability::GRADUAL_DIFFICULTY
        | capability::GRADUAL_PERFORMANCE,
};

pub struct BackendPrecsr202210;

impl Backend for BackendPrecsr202210 {
    fn algorithm(&self) -> &'static Algorithm {
        &ALGORITHM
    }

    fn parse(&self, bytes: &[u8]) -> Result<Box<dyn BackendBeatmap>, BackendError> {
        let map = Beatmap::from_bytes(bytes).map_err(|e| BackendError::Parse(e.to_string()))?;
        Ok(Box::new(MapPrecsr202210 { map }))
    }
}

struct MapPrecsr202210 {
    map: Beatmap,
}

impl BackendBeatmap for MapPrecsr202210 {
    fn algorithm(&self) -> &'static Algorithm {
        &ALGORITHM
    }

    fn difficulty(&self, request: &DifficultyRequest) -> Result<DifficultyResult, BackendError> {
        let (map, mode, mods) = prepare_map(&self.map, request)?;
        let attrs = build_difficulty(request, &mods).calculate(&map);
        Ok(convert_difficulty(attrs, mode))
    }

    fn performance(&self, request: &PerformanceRequest) -> Result<PerformanceResult, BackendError> {
        let (map, mode, mods) = prepare_map(&self.map, &request.difficulty)?;
        validate_performance(request, mode)?;
        let attrs = build_performance(&map, request, &mods).calculate();
        Ok(convert_performance(attrs, mode))
    }

    fn gradual_difficulty_at(
        &self,
        request: &DifficultyRequest,
        object_index: usize,
    ) -> Result<DifficultyResult, BackendError> {
        let (map, mode, mods) = prepare_map(&self.map, request)?;
        let attrs = build_difficulty(request, &mods)
            .gradual_difficulty(&map)
            .nth(object_index)
            .ok_or(BackendError::EndOfStream)?;
        Ok(convert_difficulty(attrs, mode))
    }

    fn gradual_performance_at(
        &self,
        request: &DifficultyRequest,
        object_index: usize,
        state: &ScoreState,
    ) -> Result<PerformanceResult, BackendError> {
        let (map, mode, mods) = prepare_map(&self.map, request)?;
        validate_score_state(state)?;
        let mut gradual = build_difficulty(request, &mods).gradual_performance(&map);
        let attrs = gradual
            .nth(convert_score_state(state), object_index)
            .ok_or(BackendError::EndOfStream)?;
        Ok(convert_performance(attrs, mode))
    }

    fn gradual_difficulty_last(
        &self,
        request: &DifficultyRequest,
    ) -> Result<DifficultyResult, BackendError> {
        let (map, mode, mods) = prepare_map(&self.map, request)?;
        let attrs = build_difficulty(request, &mods)
            .gradual_difficulty(&map)
            .last()
            .ok_or(BackendError::EndOfStream)?;
        Ok(convert_difficulty(attrs, mode))
    }

    fn gradual_performance_last(
        &self,
        request: &DifficultyRequest,
        state: &ScoreState,
    ) -> Result<PerformanceResult, BackendError> {
        let (map, mode, mods) = prepare_map(&self.map, request)?;
        validate_score_state(state)?;
        let mut gradual = build_difficulty(request, &mods).gradual_performance(&map);
        let attrs = gradual
            .last(convert_score_state(state))
            .ok_or(BackendError::EndOfStream)?;
        Ok(convert_performance(attrs, mode))
    }
}

fn to_rosu_mode(mode: Mode) -> GameMode {
    match mode {
        Mode::Osu => GameMode::Osu,
        Mode::Taiko => GameMode::Taiko,
        Mode::Catch => GameMode::Catch,
        Mode::Mania => GameMode::Mania,
    }
}

fn from_rosu_mode(mode: GameMode) -> Mode {
    match mode {
        GameMode::Osu => Mode::Osu,
        GameMode::Taiko => Mode::Taiko,
        GameMode::Catch => Mode::Catch,
        GameMode::Mania => Mode::Mania,
    }
}

fn prepare_map(
    source: &Beatmap,
    request: &DifficultyRequest,
) -> Result<(Beatmap, Mode, u32), BackendError> {
    let mode = request.mode.unwrap_or_else(|| from_rosu_mode(source.mode));
    validate_difficulty(request, mode)?;
    let mods = parse_mods(&request.mods, mode)?;
    let mut map = source.clone();
    if !map.convert_in_place(to_rosu_mode(mode)).success() {
        return Err(BackendError::UnsupportedOption(
            "requested mode conversion is not supported by rosu-pp 1.0.0".into(),
        ));
    }
    Ok((map, mode, mods))
}

fn parse_mods(input: &ModInput, _mode: Mode) -> Result<u32, BackendError> {
    match input {
        ModInput::Legacy(bits) => Ok(*bits),
        ModInput::Json(_) => Err(BackendError::UnsupportedOption(
            "structured mods are not supported by rosu-pp 1.0.0".into(),
        )),
    }
}

fn validate_difficulty(request: &DifficultyRequest, mode: Mode) -> Result<(), BackendError> {
    let flags = request.option_flags;
    if flags & option::CLOCK_RATE != 0
        && (!request.clock_rate.is_finite() || request.clock_rate <= 0.0)
    {
        return Err(BackendError::InvalidRequest(
            "clock_rate must be finite and greater than zero".into(),
        ));
    }
    for (flag, value, name) in [
        (option::AR, request.ar, "ar"),
        (option::OD, request.od, "od"),
        (option::CS, request.cs, "cs"),
        (option::HP, request.hp, "hp"),
    ] {
        if flags & flag != 0 && !value.is_finite() {
            return Err(BackendError::InvalidRequest(format!(
                "{name} must be finite"
            )));
        }
    }
    if flags & (option::AR | option::CS) != 0 && !matches!(mode, Mode::Osu | Mode::Catch) {
        return Err(BackendError::UnsupportedOption(
            "AR and CS overrides are only supported for osu!standard and catch".into(),
        ));
    }
    if flags & option::SCORE_MODE != 0 {
        return Err(BackendError::UnsupportedOption(
            "stable/lazer score mode is not supported by rosu-pp 1.0.0".into(),
        ));
    }
    Ok(())
}

fn validate_performance(request: &PerformanceRequest, mode: Mode) -> Result<(), BackendError> {
    let fields = request.score_fields;
    if fields & score_field::ACCURACY != 0
        && (!request.accuracy.is_finite() || !(0.0..=100.0).contains(&request.accuracy))
    {
        return Err(BackendError::InvalidRequest(
            "accuracy must be between 0 and 100".into(),
        ));
    }
    if fields
        & (score_field::LARGE_TICK_HITS
            | score_field::SMALL_TICK_HITS
            | score_field::SLIDER_END_HITS
            | score_field::LEGACY_TOTAL_SCORE)
        != 0
    {
        return Err(BackendError::UnsupportedOption(
            "lazer slider fields and legacy total score are not supported by rosu-pp 1.0.0".into(),
        ));
    }
    if fields & score_field::N_GEKI != 0 && mode != Mode::Mania {
        return Err(BackendError::UnsupportedOption(
            "n_geki is only supported for mania".into(),
        ));
    }
    if fields & score_field::N_KATU != 0 && !matches!(mode, Mode::Catch | Mode::Mania) {
        return Err(BackendError::UnsupportedOption(
            "n_katu is only supported for catch and mania".into(),
        ));
    }
    if fields & score_field::N50 != 0 && mode == Mode::Taiko {
        return Err(BackendError::UnsupportedOption(
            "n50 is not supported for taiko".into(),
        ));
    }
    Ok(())
}

fn build_difficulty(request: &DifficultyRequest, mods: &u32) -> Difficulty {
    let mut difficulty = Difficulty::new().mods(*mods);
    let flags = request.option_flags;
    if flags & option::CLOCK_RATE != 0 {
        difficulty = difficulty.clock_rate(request.clock_rate);
    }
    if flags & option::AR != 0 {
        difficulty = difficulty.ar(request.ar as f32, true);
    }
    if flags & option::OD != 0 {
        difficulty = difficulty.od(request.od as f32, true);
    }
    if flags & option::CS != 0 {
        difficulty = difficulty.cs(request.cs as f32, true);
    }
    if flags & option::HP != 0 {
        difficulty = difficulty.hp(request.hp as f32, true);
    }
    if flags & option::PASSED_OBJECTS != 0 {
        difficulty = difficulty.passed_objects(request.passed_objects);
    }
    difficulty
}

fn build_performance<'a>(
    map: &'a Beatmap,
    request: &PerformanceRequest,
    mods: &u32,
) -> Performance<'a> {
    let mut performance =
        Performance::new(map).difficulty(build_difficulty(&request.difficulty, mods));
    let fields = request.score_fields;
    if fields & score_field::ACCURACY != 0 {
        performance = performance.accuracy(request.accuracy);
    }
    if fields & score_field::COMBO != 0 {
        performance = performance.combo(request.combo);
    }
    if fields & score_field::MISSES != 0 {
        performance = performance.misses(request.misses);
    }
    if fields & score_field::N300 != 0 {
        performance = performance.n300(request.n300);
    }
    if fields & score_field::N100 != 0 {
        performance = performance.n100(request.n100);
    }
    if fields & score_field::N50 != 0 {
        performance = performance.n50(request.n50);
    }
    if fields & score_field::N_GEKI != 0 {
        performance = performance.n_geki(request.n_geki);
    }
    if fields & score_field::N_KATU != 0 {
        performance = performance.n_katu(request.n_katu);
    }
    performance
}

fn convert_score_state(state: &ScoreState) -> RosuScoreState {
    RosuScoreState {
        max_combo: state.max_combo,
        n_geki: state.n_geki,
        n_katu: state.n_katu,
        n300: state.n300,
        n100: state.n100,
        n50: state.n50,
        misses: state.misses,
    }
}

fn validate_score_state(state: &ScoreState) -> Result<(), BackendError> {
    if state.large_tick_hits != 0
        || state.small_tick_hits != 0
        || state.slider_end_hits != 0
        || state.legacy_total_score.is_some()
    {
        return Err(BackendError::UnsupportedOption(
            "lazer slider state and legacy total score are not supported by rosu-pp 1.0.0".into(),
        ));
    }

    Ok(())
}

fn convert_difficulty(attrs: DifficultyAttributes, mode: Mode) -> DifficultyResult {
    let mut out = DifficultyResult {
        mode: mode as i32,
        ..DifficultyResult::default()
    };
    match attrs {
        DifficultyAttributes::Osu(a) => {
            out.present = present::AIM
                | present::SPEED
                | present::FLASHLIGHT
                | present::SLIDER_FACTOR
                | present::SPEED_NOTE_COUNT
                | present::AR
                | present::OD
                | present::HP
                | present::OBJECT_COUNTS;
            out.stars = a.stars;
            out.aim = a.aim;
            out.speed = a.speed;
            out.flashlight = a.flashlight;
            out.slider_factor = a.slider_factor;
            out.speed_note_count = a.speed_note_count;
            out.ar = a.ar;
            out.od = a.od;
            out.hp = a.hp;
            out.max_combo = a.max_combo;
            out.n_circles = a.n_circles;
            out.n_sliders = a.n_sliders;
            out.n_spinners = a.n_spinners;
            out.n_objects = a.n_objects();
        }
        DifficultyAttributes::Taiko(a) => {
            out.present = present::HIT_WINDOWS | present::IS_CONVERT;
            out.stars = a.stars;
            out.stamina = a.stamina;
            out.rhythm = a.rhythm;
            out.color = a.color;
            out.great_hit_window = a.hit_window;
            out.max_combo = a.max_combo;
            out.n_objects = a.max_combo;
            out.is_convert = a.is_convert;
        }
        DifficultyAttributes::Catch(a) => {
            out.present = present::OBJECT_COUNTS | present::IS_CONVERT | present::AR;
            out.stars = a.stars;
            out.ar = a.ar;
            out.n_fruits = a.n_fruits;
            out.n_droplets = a.n_droplets;
            out.n_tiny_droplets = a.n_tiny_droplets;
            out.max_combo = a.max_combo();
            out.n_objects = a.n_fruits + a.n_droplets;
            out.is_convert = a.is_convert;
        }
        DifficultyAttributes::Mania(a) => {
            out.present = present::OBJECT_COUNTS | present::IS_CONVERT;
            out.stars = a.stars;
            out.n_objects = a.n_objects;
            out.max_combo = a.max_combo;
            out.is_convert = a.is_convert;
        }
    }
    out
}

fn convert_performance(attrs: PerformanceAttributes, mode: Mode) -> PerformanceResult {
    match attrs {
        PerformanceAttributes::Osu(a) => PerformanceResult {
            present: present::PP_AIM
                | present::PP_SPEED
                | present::PP_ACCURACY
                | present::PP_FLASHLIGHT
                | present::EFFECTIVE_MISS_COUNT,
            pp: a.pp,
            pp_aim: a.pp_aim,
            pp_speed: a.pp_speed,
            pp_accuracy: a.pp_acc,
            pp_flashlight: a.pp_flashlight,
            effective_miss_count: a.effective_miss_count,
            difficulty: convert_difficulty(DifficultyAttributes::Osu(a.difficulty), mode),
            ..PerformanceResult::default()
        },
        PerformanceAttributes::Taiko(a) => PerformanceResult {
            present: present::PP_ACCURACY | present::PP_DIFFICULTY,
            pp: a.pp,
            pp_accuracy: a.pp_acc,
            pp_difficulty: a.pp_difficulty,
            difficulty: convert_difficulty(DifficultyAttributes::Taiko(a.difficulty), mode),
            ..PerformanceResult::default()
        },
        PerformanceAttributes::Catch(a) => PerformanceResult {
            pp: a.pp,
            difficulty: convert_difficulty(DifficultyAttributes::Catch(a.difficulty), mode),
            ..PerformanceResult::default()
        },
        PerformanceAttributes::Mania(a) => PerformanceResult {
            present: present::PP_DIFFICULTY,
            pp: a.pp,
            pp_difficulty: a.pp_difficulty,
            difficulty: convert_difficulty(DifficultyAttributes::Mania(a.difficulty), mode),
            ..PerformanceResult::default()
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn metadata_is_stable() {
        assert_eq!(ALGORITHM.id, 20_221_000);
        assert_eq!(ALGORITHM.version, "precsr-202210-rosu-pp-1.0.0");
        assert_eq!(ALGORITHM.capabilities & capability::READING_SKILL, 0);
        assert_eq!(ALGORITHM.capabilities & capability::STRUCTURED_MODS, 0);
        assert_eq!(
            ALGORITHM.capabilities & capability::LAZER_SLIDER_ACCURACY,
            0
        );
    }
}
