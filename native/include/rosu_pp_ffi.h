#ifndef ROSU_PP_FFI_H
#define ROSU_PP_FFI_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define ROSU_ABI_VERSION 2u

typedef struct RosuCalculator RosuCalculator;
typedef struct RosuBeatmap RosuBeatmap;
typedef struct RosuGradual RosuGradual;

enum RosuStatus {
    ROSU_OK = 0,
    ROSU_INVALID_ARGUMENT = -1,
    ROSU_INVALID_ALGORITHM = -2,
    ROSU_INVALID_HANDLE = -3,
    ROSU_ABI_MISMATCH = -4,
    ROSU_PARSE_ERROR = -5,
    ROSU_UNSUPPORTED_OPTION = -6,
    ROSU_CALCULATION_ERROR = -7,
    ROSU_END_OF_STREAM = -8,
    ROSU_PANIC = -127
};

enum RosuCapability {
    ROSU_CAP_DIFFICULTY = UINT64_C(1) << 0,
    ROSU_CAP_PERFORMANCE = UINT64_C(1) << 1,
    ROSU_CAP_GRADUAL_DIFFICULTY = UINT64_C(1) << 2,
    ROSU_CAP_GRADUAL_PERFORMANCE = UINT64_C(1) << 3,
    ROSU_CAP_READING_SKILL = UINT64_C(1) << 4,
    ROSU_CAP_LAZER_SLIDER_ACCURACY = UINT64_C(1) << 5,
    ROSU_CAP_STRUCTURED_MODS = UINT64_C(1) << 6
};

enum RosuOptionFlag {
    ROSU_OPT_CLOCK_RATE = UINT64_C(1) << 0,
    ROSU_OPT_AR = UINT64_C(1) << 1,
    ROSU_OPT_OD = UINT64_C(1) << 2,
    ROSU_OPT_CS = UINT64_C(1) << 3,
    ROSU_OPT_HP = UINT64_C(1) << 4,
    ROSU_OPT_PASSED_OBJECTS = UINT64_C(1) << 5,
    ROSU_OPT_SCORE_MODE = UINT64_C(1) << 6,
    ROSU_OPT_MODS_JSON = UINT64_C(1) << 7
};

enum RosuScoreField {
    ROSU_SCORE_ACCURACY = UINT64_C(1) << 0,
    ROSU_SCORE_COMBO = UINT64_C(1) << 1,
    ROSU_SCORE_MISSES = UINT64_C(1) << 2,
    ROSU_SCORE_N300 = UINT64_C(1) << 3,
    ROSU_SCORE_N100 = UINT64_C(1) << 4,
    ROSU_SCORE_N50 = UINT64_C(1) << 5,
    ROSU_SCORE_N_GEKI = UINT64_C(1) << 6,
    ROSU_SCORE_N_KATU = UINT64_C(1) << 7,
    ROSU_SCORE_LARGE_TICK_HITS = UINT64_C(1) << 8,
    ROSU_SCORE_SMALL_TICK_HITS = UINT64_C(1) << 9,
    ROSU_SCORE_SLIDER_END_HITS = UINT64_C(1) << 10,
    ROSU_SCORE_LEGACY_TOTAL_SCORE = UINT64_C(1) << 11
};

typedef struct RosuAlgorithmInfo {
    uint32_t struct_size;
    uint32_t abi_version;
    uint32_t algorithm_id;
    uint32_t reserved;
    uint64_t capabilities;
    const char *name;
    const char *version;
    const char *details;
} RosuAlgorithmInfo;

typedef struct RosuDifficultyRequest {
    uint32_t struct_size;
    uint32_t abi_version;
    int32_t mode;              /* -1 keeps the map mode; 0 osu, 1 taiko, 2 catch, 3 mania */
    uint32_t score_mode;       /* 0 default, 1 stable, 2 lazer */
    uint64_t mods;             /* bridge-defined legacy osu! mod bitset; upper 32 bits reserved */
    uint64_t option_flags;
    double clock_rate;
    double ar;
    double od;
    double cs;
    double hp;
    uint32_t passed_objects;
    uint32_t reserved;
    const uint8_t *mods_json; /* borrowed UTF-8 JSON; valid for the duration of the call */
    size_t mods_json_len;     /* excludes any NUL terminator */
} RosuDifficultyRequest;

typedef struct RosuPerformanceRequest {
    RosuDifficultyRequest difficulty;
    uint64_t score_fields;
    double accuracy;
    uint32_t combo;
    uint32_t misses;
    uint32_t n300;
    uint32_t n100;
    uint32_t n50;
    uint32_t n_geki;
    uint32_t n_katu;
    uint32_t large_tick_hits;
    uint32_t small_tick_hits;
    uint32_t slider_end_hits;
    uint32_t legacy_total_score;
    uint32_t reserved;
} RosuPerformanceRequest;

typedef struct RosuScoreState {
    uint32_t struct_size;
    uint32_t abi_version;
    uint32_t max_combo;
    uint32_t large_tick_hits;
    uint32_t small_tick_hits;
    uint32_t slider_end_hits;
    uint32_t n_geki;
    uint32_t n_katu;
    uint32_t n300;
    uint32_t n100;
    uint32_t n50;
    uint32_t misses;
    uint32_t legacy_total_score;
    uint32_t reserved;
    uint64_t fields;
} RosuScoreState;

typedef struct RosuDifficultyResult {
    uint32_t struct_size;
    uint32_t abi_version;
    uint32_t algorithm_id;
    int32_t mode;
    uint64_t capabilities;
    uint64_t present;
    const char *algorithm_name;     /* static UTF-8 NUL-terminated; bridge lifetime */
    const char *algorithm_version;  /* static UTF-8 NUL-terminated; bridge lifetime */
    double stars, aim, speed, flashlight, reading, slider_factor;
    double aim_difficult_slider_count, speed_note_count, reading_difficult_note_count;
    double aim_top_weighted_slider_factor, speed_top_weighted_slider_factor;
    double aim_difficult_strain_count, speed_difficult_strain_count;
    double nested_score_per_object, legacy_score_base_multiplier, maximum_legacy_combo_score;
    double stamina, rhythm, color, taiko_reading, mono_stamina_factor;
    double mechanical_difficulty, consistency_factor, catch_preempt;
    double ar, od, hp, great_hit_window, ok_hit_window, meh_hit_window;
    uint32_t max_combo, n_objects, n_circles, n_sliders, n_spinners;
    uint32_t n_fruits, n_droplets, n_tiny_droplets, n_hold_notes, is_convert;
} RosuDifficultyResult;

typedef struct RosuPerformanceResult {
    uint32_t struct_size;
    uint32_t abi_version;
    uint32_t algorithm_id;
    int32_t mode;
    uint64_t capabilities;
    uint64_t present;
    const char *algorithm_name;
    const char *algorithm_version;
    double pp, pp_aim, pp_speed, pp_accuracy, pp_flashlight, pp_reading, pp_difficulty;
    double effective_miss_count, speed_deviation, estimated_unstable_rate;
    double combo_based_estimated_miss_count, score_based_estimated_miss_count;
    double aim_estimated_slider_breaks, speed_estimated_slider_breaks;
    RosuDifficultyResult difficulty;
} RosuPerformanceResult;

uint32_t rosu_abi_version(void);
uint32_t rosu_algorithm_count(void);
int32_t rosu_algorithm_info(uint32_t index, RosuAlgorithmInfo *out);
RosuCalculator *rosu_calculator_create(uint32_t algorithm_id);
void rosu_calculator_free(RosuCalculator *calculator);
RosuBeatmap *rosu_beatmap_from_bytes(const RosuCalculator *, const uint8_t *, size_t);
RosuBeatmap *rosu_beatmap_from_path(const RosuCalculator *, const uint8_t *utf8_path, size_t);
void rosu_beatmap_free(RosuBeatmap *beatmap);
int32_t rosu_calculate_difficulty(const RosuCalculator *, const RosuBeatmap *, const RosuDifficultyRequest *, RosuDifficultyResult *);
int32_t rosu_calculate_performance(const RosuCalculator *, const RosuBeatmap *, const RosuPerformanceRequest *, RosuPerformanceResult *);
RosuGradual *rosu_gradual_difficulty_create(const RosuCalculator *, const RosuBeatmap *, const RosuDifficultyRequest *);
RosuGradual *rosu_gradual_performance_create(const RosuCalculator *, const RosuBeatmap *, const RosuDifficultyRequest *);
int32_t rosu_gradual_difficulty_next(const RosuGradual *, RosuDifficultyResult *);
int32_t rosu_gradual_performance_next(const RosuGradual *, const RosuScoreState *, RosuPerformanceResult *);
int32_t rosu_gradual_difficulty_last(const RosuGradual *, RosuDifficultyResult *);
int32_t rosu_gradual_performance_last(const RosuGradual *, const RosuScoreState *, RosuPerformanceResult *);
void rosu_gradual_free(RosuGradual *gradual);
size_t rosu_last_error_length(void);
size_t rosu_last_error_copy(uint8_t *buffer, size_t capacity);

#ifdef __cplusplus
}
#endif
#endif
