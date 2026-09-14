package com.click.lightmemo.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsStore by preferencesDataStore(name = "food_settings")
private val settingsJson = Json { ignoreUnknownKeys = true }

const val DEFAULT_API_BASE_URL = "https://api.deepseek.com"
const val DEFAULT_API_MODEL = "deepseek-flash"
const val DEFAULT_PROFILE_HEIGHT_CM = 170f
const val DEFAULT_PROFILE_WEIGHT_KG = 60f
const val DEFAULT_PROFILE_AGE_YEARS = 25

enum class Gender(val label: String) {
    MALE("男"),
    FEMALE("女"),
}

enum class ActivityLevel(val label: String, val factor: Float, val summary: String) {
    LOW("低强度", 1.2f, "久坐，每周几乎不运动"),
    MEDIUM("中强度", 1.55f, "每周 3–5 天中等强度运动"),
    HIGH("高强度", 1.725f, "每周 6–7 天高强度运动"),
}

enum class GallerySaveLocation(val label: String, val relativePath: String) {
    PICTURES("Pictures/轻食记", "Pictures/轻食记"),
    DCIM("DCIM/轻食记", "DCIM/轻食记"),
}

@Serializable
data class ApiPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "默认配置",
    val baseUrl: String = DEFAULT_API_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_API_MODEL,
)

data class AppSettings(
    val apiPresets: List<ApiPreset> = listOf(ApiPreset()),
    val activePresetId: String = apiPresets.firstOrNull()?.id.orEmpty(),
    val systemBackground: String = "",
    val promptOverrides: Map<String, String> = emptyMap(),
    val foodDataCentralApiKey: String = "",
    val dailyCalorieTarget: Float = 1800f,
    /** 营养素目标；<=0 表示跟随推荐值 */
    val proteinTargetG: Float = 0f,
    val fatTargetG: Float = 0f,
    val carbsTargetG: Float = 0f,
    val heightCm: Float = 0f,
    val weightKg: Float = 0f,
    val ageYears: Int = 0,
    val gender: Gender = Gender.MALE,
    val activityLevel: ActivityLevel = ActivityLevel.MEDIUM,
    /** ARGB；默认与 TodayScreen 宏量环一致 */
    val proteinRingColor: Long = FoodColorPalette.Multicolor.protein,
    val carbsRingColor: Long = FoodColorPalette.Multicolor.carbs,
    val fatRingColor: Long = FoodColorPalette.Multicolor.fat,
    /** Product-owned colors used by calorie, nutrient, and meal-structure visualizations. */
    val calorieProgressColor: Long = FoodColorPalette.Multicolor.calorie,
    val overTargetColor: Long = FoodColorPalette.Multicolor.overTarget,
    val breakfastColor: Long = FoodColorPalette.Multicolor.breakfast,
    val lunchColor: Long = FoodColorPalette.Multicolor.lunch,
    val dinnerColor: Long = FoodColorPalette.Multicolor.dinner,
    val snackColor: Long = FoodColorPalette.Multicolor.snack,
    val colorTheme: ColorThemePreset = ColorThemePreset.MULTICOLOR,
    val colorSeed: Long = ColorThemePreset.MULTICOLOR.seedColor,
    /** 总开关：默认关闭玻璃特效，避免弱 GPU/模拟器冷启动首帧 ANR */
    val glassEffectsEnabled: Boolean = false,
    val topGradientBlurEnabled: Boolean = false,
    /** 顶部渐变模糊覆盖范围，单位 dp。 */
    val topGradientBlurRangeDp: Int = 72,
    /** 是否将相机拍摄的照片复制到系统相册。 */
    val savePhotosToGallery: Boolean = true,
    val gallerySaveLocation: GallerySaveLocation = GallerySaveLocation.PICTURES,
) {
    val activePreset: ApiPreset
        get() = apiPresets.firstOrNull { it.id == activePresetId }
            ?: apiPresets.firstOrNull()
            ?: ApiPreset()

    val baseUrl: String get() = activePreset.baseUrl
    val apiKey: String get() = activePreset.apiKey
    val model: String get() = activePreset.model

    val colorPalette: FoodColorPalette
        get() = FoodColorPalette(
            calorie = calorieProgressColor,
            overTarget = overTargetColor,
            protein = proteinRingColor,
            carbs = carbsRingColor,
            fat = fatRingColor,
            breakfast = breakfastColor,
            lunch = lunchColor,
            dinner = dinnerColor,
            snack = snackColor,
        )

    val isRecognitionConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    val hasPersonalProfile: Boolean
        get() = heightCm > 0f && weightKg > 0f && ageYears > 0

    /** Mifflin-St Jeor 基础代谢率；资料不全时返回 null。 */
    val basalMetabolicRate: Float?
        get() = if (!hasPersonalProfile) {
            null
        } else {
            val base = 10f * weightKg + 6.25f * heightCm - 5f * ageYears
            when (gender) {
                Gender.MALE -> base + 5f
                Gender.FEMALE -> base - 161f
            }.coerceAtLeast(800f)
        }

    /** 按活动强度估算的每日总消耗 (TDEE)。 */
    val recommendedCalories: Float?
        get() = basalMetabolicRate?.let { it * activityLevel.factor }

    data class NutrientTargets(
        val calories: Float,
        val proteinG: Float,
        val fatG: Float,
        val carbsG: Float,
    )

    /**
     * 推荐营养素：蛋白质约 1.0–1.2 g/kg，脂肪约 25% 热量，其余给碳水。
     */
    val recommendedNutrients: NutrientTargets?
        get() {
            val calories = recommendedCalories ?: return null
            val proteinPerKg = when (activityLevel) {
                ActivityLevel.LOW -> 1.0f
                ActivityLevel.MEDIUM, ActivityLevel.HIGH -> 1.2f
            }
            val proteinG = weightKg * proteinPerKg
            val fatG = calories * 0.25f / 9f
            val carbsG = ((calories - proteinG * 4f - fatG * 9f) / 4f).coerceAtLeast(0f)
            return NutrientTargets(
                calories = calories,
                proteinG = proteinG,
                fatG = fatG,
                carbsG = carbsG,
            )
        }

    /** 手动营养目标；未设置时回落到推荐值 */
    val effectiveProteinG: Float
        get() = if (proteinTargetG > 0f) proteinTargetG else (recommendedNutrients?.proteinG ?: 0f)

    val effectiveFatG: Float
        get() = if (fatTargetG > 0f) fatTargetG else (recommendedNutrients?.fatG ?: 0f)

    val effectiveCarbsG: Float
        get() = if (carbsTargetG > 0f) carbsTargetG else (recommendedNutrients?.carbsG ?: 0f)
}

class SettingsRepository(private val store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) {
    constructor(context: Context) : this(context.settingsStore)
    val readError = MutableStateFlow<String?>(null)
    private val foodPresetsKey = stringPreferencesKey("food_presets")

    /** 安全解码：损坏或空数据时回落到默认预设，避免读写整条链路失败。 */
    private fun decodeFoodPresets(raw: String?): List<PresetFood> {
        if (raw.isNullOrBlank()) return DefaultPresetFoods
        return runCatching {
            settingsJson.decodeFromString<List<PresetFood>>(raw)
        }.getOrDefault(DefaultPresetFoods).ifEmpty { DefaultPresetFoods }
    }

    val foodPresets: Flow<List<PresetFood>> = store.data.map { prefs ->
        decodeFoodPresets(prefs[foodPresetsKey])
    }.catch { error ->
        readError.value = "预设读取失败，已恢复默认：${error.message.orEmpty()}"
        emit(DefaultPresetFoods)
    }

    suspend fun updateFoodPreset(updated: PresetFood) {
        require(updated.name.isNotBlank() && updated.defaultGrams.isFinite() && updated.defaultGrams > 0.0) {
            "请填写食物名称和有效重量"
        }
        require(updated.nutrition?.isValid() != false) { "营养素必须是非负有效数字" }
        store.edit { prefs ->
            val current = decodeFoodPresets(prefs[foodPresetsKey])
            val exists = current.any { it.id == updated.id }
            val next = if (exists) {
                current.map { if (it.id == updated.id) updated else it }
            } else {
                current + updated
            }
            prefs[foodPresetsKey] = settingsJson.encodeToString(next)
        }
    }

    suspend fun resetFoodPresets() {
        store.edit { it.remove(foodPresetsKey) }
    }

    /**
     * Exports the user-owned settings and food presets as structured JSON.
     *
     * The API keys are intentionally included: this is a complete user backup rather than a
     * shareable diagnostics file. The UI must warn the user before saving or sharing it.
     */
    suspend fun exportBackupJson(): JSONObject {
        val current = settings.first()
        val presets = foodPresets.first()
        return JSONObject().apply {
            put("settings", JSONObject().apply {
                put("apiPresets", JSONArray(settingsJson.encodeToString(current.apiPresets)))
                put("activePresetId", current.activePresetId)
                put("systemBackground", current.systemBackground)
                put("promptOverrides", JSONObject(settingsJson.encodeToString(current.promptOverrides)))
                put("foodDataCentralApiKey", current.foodDataCentralApiKey)
                put("dailyCalorieTarget", current.dailyCalorieTarget.toDouble())
                put("proteinTargetG", current.proteinTargetG.toDouble())
                put("fatTargetG", current.fatTargetG.toDouble())
                put("carbsTargetG", current.carbsTargetG.toDouble())
                put("heightCm", current.heightCm.toDouble())
                put("weightKg", current.weightKg.toDouble())
                put("ageYears", current.ageYears)
                put("gender", current.gender.name)
                put("activityLevel", current.activityLevel.name)
                put("proteinRingColor", current.proteinRingColor)
                put("carbsRingColor", current.carbsRingColor)
                put("fatRingColor", current.fatRingColor)
                put("calorieProgressColor", current.calorieProgressColor)
                put("overTargetColor", current.overTargetColor)
                put("breakfastColor", current.breakfastColor)
                put("lunchColor", current.lunchColor)
                put("dinnerColor", current.dinnerColor)
                put("snackColor", current.snackColor)
                put("colorTheme", current.colorTheme.name)
                put("colorSeed", current.colorSeed)
                put("glassEffectsEnabled", current.glassEffectsEnabled)
                put("topGradientBlurEnabled", current.topGradientBlurEnabled)
                put("topGradientBlurRangeDp", current.topGradientBlurRangeDp)
                put("savePhotosToGallery", current.savePhotosToGallery)
                put("gallerySaveLocation", current.gallerySaveLocation.name)
            })
            put("foodPresets", JSONArray(settingsJson.encodeToString(presets)))
        }
    }

    /** Restores the settings portion of a complete app backup. */
    suspend fun importBackupJson(settingsJsonObject: JSONObject, foodPresetsJson: JSONArray) {
        val importedPresets = runCatching {
            settingsJson.decodeFromString<List<ApiPreset>>(
                settingsJsonObject.getJSONArray("apiPresets").toString(),
            )
        }.getOrElse { error -> throw IllegalArgumentException("模型配置无效", error) }
        require(importedPresets.isNotEmpty()) { "至少需要一个模型配置" }
        importedPresets.forEach { preset ->
            require(preset.name.isNotBlank() && preset.baseUrl.isNotBlank() && preset.model.isNotBlank()) {
                "模型配置不完整"
            }
        }
        val activePresetId = settingsJsonObject.optString("activePresetId")
            .takeIf { id -> importedPresets.any { it.id == id } }
            ?: importedPresets.first().id

        val importedFoodPresets = runCatching {
            settingsJson.decodeFromString<List<PresetFood>>(foodPresetsJson.toString())
        }.getOrElse { error -> throw IllegalArgumentException("预设食物无效", error) }
        importedFoodPresets.forEach { preset ->
            require(preset.name.isNotBlank() && preset.defaultGrams.isFinite() && preset.defaultGrams > 0.0) {
                "预设食物包含无效重量"
            }
            require(preset.nutrition?.isValid() != false) { "预设食物包含无效营养数据" }
        }

        fun floatValue(name: String, default: Float): Float = settingsJsonObject
            .optDouble(name, default.toDouble())
            .toFloat()
            .also { require(it.isFinite()) { "$name 不是有效数字" } }

        val defaults = AppSettings()
        val gender = settingsJsonObject.optString("gender")
            .let { value -> Gender.entries.firstOrNull { it.name == value } }
            ?: defaults.gender
        val activity = settingsJsonObject.optString("activityLevel")
            .let { value -> ActivityLevel.entries.firstOrNull { it.name == value } }
            ?: defaults.activityLevel
        val colorTheme = settingsJsonObject.optString("colorTheme")
            .let { value -> ColorThemePreset.entries.firstOrNull { it.name == value } }
            ?: defaults.colorTheme
        val gallerySaveLocation = settingsJsonObject.optString("gallerySaveLocation")
            .let { value -> GallerySaveLocation.entries.firstOrNull { it.name == value } }
            ?: defaults.gallerySaveLocation

        store.edit { prefs ->
            val active = importedPresets.first { it.id == activePresetId }
            prefs[foodPresetsKey] = settingsJson.encodeToString(importedFoodPresets)
            prefs[Keys.PRESETS] = settingsJson.encodeToString(importedPresets)
            prefs[Keys.ACTIVE_PRESET] = activePresetId
            prefs[Keys.BASE_URL] = active.baseUrl
            prefs[Keys.API_KEY] = active.apiKey
            prefs[Keys.MODEL] = active.model
            prefs[Keys.SYSTEM_BG] = settingsJsonObject.optString("systemBackground")
            prefs[Keys.PROMPT_OVERRIDES] = settingsJson.encodeToString(
                runCatching {
                    settingsJson.decodeFromString<Map<String, String>>(
                        settingsJsonObject.optJSONObject("promptOverrides")?.toString().orEmpty(),
                    )
                }.getOrDefault(emptyMap()),
            )
            prefs[Keys.FDC_API_KEY] = settingsJsonObject.optString("foodDataCentralApiKey")
            prefs[Keys.TARGET] = floatValue("dailyCalorieTarget", defaults.dailyCalorieTarget)
            prefs[Keys.PROTEIN] = floatValue("proteinTargetG", defaults.proteinTargetG)
            prefs[Keys.FAT] = floatValue("fatTargetG", defaults.fatTargetG)
            prefs[Keys.CARBS] = floatValue("carbsTargetG", defaults.carbsTargetG)
            prefs[Keys.HEIGHT] = floatValue("heightCm", defaults.heightCm)
            prefs[Keys.WEIGHT] = floatValue("weightKg", defaults.weightKg)
            prefs[Keys.AGE] = settingsJsonObject.optInt("ageYears", defaults.ageYears)
            prefs[Keys.GENDER] = gender.name
            prefs[Keys.ACTIVITY] = activity.name
            prefs[Keys.PROTEIN_RING] = settingsJsonObject.optLong("proteinRingColor", defaults.proteinRingColor)
            prefs[Keys.CARBS_RING] = settingsJsonObject.optLong("carbsRingColor", defaults.carbsRingColor)
            prefs[Keys.FAT_RING] = settingsJsonObject.optLong("fatRingColor", defaults.fatRingColor)
            prefs[Keys.CALORIE_PROGRESS] = settingsJsonObject.optLong("calorieProgressColor", defaults.calorieProgressColor)
            prefs[Keys.OVER_TARGET] = settingsJsonObject.optLong("overTargetColor", defaults.overTargetColor)
            prefs[Keys.BREAKFAST] = settingsJsonObject.optLong("breakfastColor", defaults.breakfastColor)
            prefs[Keys.LUNCH] = settingsJsonObject.optLong("lunchColor", defaults.lunchColor)
            prefs[Keys.DINNER] = settingsJsonObject.optLong("dinnerColor", defaults.dinnerColor)
            prefs[Keys.SNACK] = settingsJsonObject.optLong("snackColor", defaults.snackColor)
            prefs[Keys.COLOR_THEME] = colorTheme.name
            prefs[Keys.COLOR_SEED] = settingsJsonObject.optLong("colorSeed", defaults.colorSeed)
            prefs[Keys.GLASS_EFFECTS] = settingsJsonObject.optBoolean("glassEffectsEnabled", defaults.glassEffectsEnabled)
            prefs[Keys.TOP_GRADIENT_BLUR] = settingsJsonObject.optBoolean(
                "topGradientBlurEnabled",
                defaults.topGradientBlurEnabled,
            )
            prefs[Keys.TOP_GRADIENT_BLUR_RANGE] = settingsJsonObject
                .optInt("topGradientBlurRangeDp", defaults.topGradientBlurRangeDp)
                .coerceIn(0, 240)
            prefs[Keys.SAVE_PHOTOS_TO_GALLERY] = settingsJsonObject.optBoolean(
                "savePhotosToGallery",
                defaults.savePhotosToGallery,
            )
            prefs[Keys.GALLERY_SAVE_LOCATION] = gallerySaveLocation.name
        }
    }

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val TARGET = floatPreferencesKey("daily_target")
        val HEIGHT = floatPreferencesKey("height_cm")
        val WEIGHT = floatPreferencesKey("weight_kg")
        val AGE = intPreferencesKey("age_years")
        val GENDER = stringPreferencesKey("gender")
        val ACTIVITY = stringPreferencesKey("activity_level")
        val PRESETS = stringPreferencesKey("api_presets")
        val ACTIVE_PRESET = stringPreferencesKey("active_preset_id")
        val SYSTEM_BG = stringPreferencesKey("system_background")
        val PROMPT_OVERRIDES = stringPreferencesKey("prompt_overrides")
        val FDC_API_KEY = stringPreferencesKey("food_data_central_api_key")
        val PROTEIN = floatPreferencesKey("protein_target_g")
        val FAT = floatPreferencesKey("fat_target_g")
        val CARBS = floatPreferencesKey("carbs_target_g")
        val PROTEIN_RING = longPreferencesKey("protein_ring_color")
        val CARBS_RING = longPreferencesKey("carbs_ring_color")
        val FAT_RING = longPreferencesKey("fat_ring_color")
        val CALORIE_PROGRESS = longPreferencesKey("calorie_progress_color")
        val OVER_TARGET = longPreferencesKey("over_target_color")
        val BREAKFAST = longPreferencesKey("breakfast_color")
        val LUNCH = longPreferencesKey("lunch_color")
        val DINNER = longPreferencesKey("dinner_color")
        val SNACK = longPreferencesKey("snack_color")
        val COLOR_THEME = stringPreferencesKey("color_theme")
        val COLOR_SEED = longPreferencesKey("color_seed")
        val GLASS_EFFECTS = booleanPreferencesKey("glass_effects_enabled")
        val TOP_GRADIENT_BLUR = booleanPreferencesKey("top_gradient_blur_enabled")
        val TOP_GRADIENT_BLUR_RANGE = intPreferencesKey("top_gradient_blur_range_dp")
        val SAVE_PHOTOS_TO_GALLERY = booleanPreferencesKey("save_photos_to_gallery")
        val GALLERY_SAVE_LOCATION = stringPreferencesKey("gallery_save_location")
    }

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        val presets = parsePresets(
            raw = prefs[Keys.PRESETS],
            legacyBaseUrl = prefs[Keys.BASE_URL],
            legacyApiKey = prefs[Keys.API_KEY],
            legacyModel = prefs[Keys.MODEL],
        )
        val activeId = prefs[Keys.ACTIVE_PRESET]
            ?.takeIf { id -> presets.any { it.id == id } }
            ?: presets.firstOrNull()?.id.orEmpty()

        val storedTheme = prefs[Keys.COLOR_THEME]?.let { name ->
            ColorThemePreset.entries.firstOrNull { it.name == name }
        }
        val storedSeed = prefs[Keys.COLOR_SEED] ?: storedTheme?.seedColor ?: ColorThemePreset.MULTICOLOR.seedColor
        val hasNewPalette = prefs.contains(Keys.COLOR_THEME) || prefs.contains(Keys.CALORIE_PROGRESS)
        val legacyPalette = FoodColorPalette.Multicolor.copy(
            protein = prefs[Keys.PROTEIN_RING] ?: FoodColorPalette.Multicolor.protein,
            carbs = prefs[Keys.CARBS_RING] ?: FoodColorPalette.Multicolor.carbs,
            fat = prefs[Keys.FAT_RING] ?: FoodColorPalette.Multicolor.fat,
        )
        val storedPalette = when {
            !hasNewPalette && (prefs.contains(Keys.PROTEIN_RING) || prefs.contains(Keys.CARBS_RING) || prefs.contains(Keys.FAT_RING)) -> legacyPalette
            storedTheme == ColorThemePreset.CUSTOM -> FoodColorPalette.forPreset(ColorThemePreset.CUSTOM, storedSeed)
            hasNewPalette -> FoodColorPalette.Multicolor.copy(
                calorie = prefs[Keys.CALORIE_PROGRESS] ?: FoodColorPalette.Multicolor.calorie,
                overTarget = prefs[Keys.OVER_TARGET] ?: FoodColorPalette.Multicolor.overTarget,
                protein = prefs[Keys.PROTEIN_RING] ?: FoodColorPalette.Multicolor.protein,
                carbs = prefs[Keys.CARBS_RING] ?: FoodColorPalette.Multicolor.carbs,
                fat = prefs[Keys.FAT_RING] ?: FoodColorPalette.Multicolor.fat,
                breakfast = prefs[Keys.BREAKFAST] ?: FoodColorPalette.Multicolor.breakfast,
                lunch = prefs[Keys.LUNCH] ?: FoodColorPalette.Multicolor.lunch,
                dinner = prefs[Keys.DINNER] ?: FoodColorPalette.Multicolor.dinner,
                snack = prefs[Keys.SNACK] ?: FoodColorPalette.Multicolor.snack,
            )
            else -> FoodColorPalette.forPreset(ColorThemePreset.MULTICOLOR)
        }

        AppSettings(
            apiPresets = presets,
            activePresetId = activeId,
            systemBackground = prefs[Keys.SYSTEM_BG] ?: "",
            promptOverrides = decodePromptOverrides(prefs[Keys.PROMPT_OVERRIDES]),
            foodDataCentralApiKey = prefs[Keys.FDC_API_KEY] ?: "",
            dailyCalorieTarget = prefs[Keys.TARGET] ?: 1800f,
            proteinTargetG = prefs[Keys.PROTEIN] ?: 0f,
            fatTargetG = prefs[Keys.FAT] ?: 0f,
            carbsTargetG = prefs[Keys.CARBS] ?: 0f,
            heightCm = prefs[Keys.HEIGHT] ?: 0f,
            weightKg = prefs[Keys.WEIGHT] ?: 0f,
            ageYears = prefs[Keys.AGE] ?: 0,
            gender = prefs[Keys.GENDER]?.let { name -> Gender.entries.firstOrNull { it.name == name } }
                ?: Gender.MALE,
            activityLevel = prefs[Keys.ACTIVITY]?.let { name ->
                ActivityLevel.entries.firstOrNull { it.name == name }
            } ?: ActivityLevel.MEDIUM,
            proteinRingColor = storedPalette.protein,
            carbsRingColor = storedPalette.carbs,
            fatRingColor = storedPalette.fat,
            calorieProgressColor = storedPalette.calorie,
            overTargetColor = storedPalette.overTarget,
            breakfastColor = storedPalette.breakfast,
            lunchColor = storedPalette.lunch,
            dinnerColor = storedPalette.dinner,
            snackColor = storedPalette.snack,
            colorTheme = storedTheme ?: if (!hasNewPalette && legacyPalette != FoodColorPalette.Multicolor) {
                ColorThemePreset.CUSTOM
            } else {
                ColorThemePreset.MULTICOLOR
            },
            colorSeed = storedSeed,
            glassEffectsEnabled = prefs[Keys.GLASS_EFFECTS] ?: false,
            topGradientBlurEnabled = prefs[Keys.TOP_GRADIENT_BLUR] ?: false,
            topGradientBlurRangeDp = (prefs[Keys.TOP_GRADIENT_BLUR_RANGE] ?: 72).coerceIn(0, 240),
            savePhotosToGallery = prefs[Keys.SAVE_PHOTOS_TO_GALLERY] ?: true,
            gallerySaveLocation = prefs[Keys.GALLERY_SAVE_LOCATION]?.let { name ->
                GallerySaveLocation.entries.firstOrNull { it.name == name }
            } ?: GallerySaveLocation.PICTURES,
        )
    }.retryWhen { error, _ ->
        readError.value = "设置读取失败，正在重试：${error.message.orEmpty()}"
        delay(5_000)
        true
    }

    private fun parsePresets(
        raw: String?,
        legacyBaseUrl: String?,
        legacyApiKey: String?,
        legacyModel: String?,
    ): List<ApiPreset> {
        val fromJson = raw?.let {
            runCatching { settingsJson.decodeFromString<List<ApiPreset>>(it) }.getOrNull()
        }
        if (!fromJson.isNullOrEmpty()) return fromJson

        return listOf(
            ApiPreset(
                id = "default",
                name = "默认配置",
                baseUrl = legacyBaseUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_API_BASE_URL,
                apiKey = legacyApiKey.orEmpty(),
                model = legacyModel?.takeIf { it.isNotBlank() } ?: DEFAULT_API_MODEL,
            ),
        )
    }

    private suspend fun writePresets(presets: List<ApiPreset>, activeId: String) {
        val active = presets.firstOrNull { it.id == activeId } ?: presets.firstOrNull()
        store.edit { prefs ->
            prefs[Keys.PRESETS] = settingsJson.encodeToString(presets)
            prefs[Keys.ACTIVE_PRESET] = activeId
            if (active != null) {
                prefs[Keys.BASE_URL] = active.baseUrl
                prefs[Keys.API_KEY] = active.apiKey
                prefs[Keys.MODEL] = active.model
            }
        }
    }

    suspend fun selectPreset(presetId: String) {
        store.edit { prefs ->
            prefs[Keys.ACTIVE_PRESET] = presetId
        }
    }

    suspend fun addPreset(name: String) {
        val preset = ApiPreset(name = name.ifBlank { "新配置" })
        store.edit { prefs ->
            val current = parsePresets(prefs[Keys.PRESETS], prefs[Keys.BASE_URL], prefs[Keys.API_KEY], prefs[Keys.MODEL])
            val next = current + preset
            prefs[Keys.PRESETS] = settingsJson.encodeToString(next)
            prefs[Keys.ACTIVE_PRESET] = preset.id
            prefs[Keys.BASE_URL] = preset.baseUrl
            prefs[Keys.API_KEY] = preset.apiKey
            prefs[Keys.MODEL] = preset.model
        }
    }

    suspend fun deletePreset(presetId: String) {
        store.edit { prefs ->
            val current = parsePresets(prefs[Keys.PRESETS], prefs[Keys.BASE_URL], prefs[Keys.API_KEY], prefs[Keys.MODEL])
            if (current.size <= 1) return@edit
            val next = current.filterNot { it.id == presetId }
            val activeId = if (prefs[Keys.ACTIVE_PRESET] == presetId) {
                next.first().id
            } else {
                prefs[Keys.ACTIVE_PRESET] ?: next.first().id
            }
            val active = next.firstOrNull { it.id == activeId } ?: next.first()
            prefs[Keys.PRESETS] = settingsJson.encodeToString(next)
            prefs[Keys.ACTIVE_PRESET] = activeId
            prefs[Keys.BASE_URL] = active.baseUrl
            prefs[Keys.API_KEY] = active.apiKey
            prefs[Keys.MODEL] = active.model
        }
    }

    suspend fun renamePreset(presetId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        store.edit { prefs ->
            val current = parsePresets(prefs[Keys.PRESETS], prefs[Keys.BASE_URL], prefs[Keys.API_KEY], prefs[Keys.MODEL])
            val next = current.map { if (it.id == presetId) it.copy(name = trimmed) else it }
            prefs[Keys.PRESETS] = settingsJson.encodeToString(next)
        }
    }

    /** Persist one edited model configuration atomically after the user taps Save. */
    suspend fun updatePreset(
        presetId: String,
        name: String,
        baseUrl: String,
        apiKey: String,
        model: String,
    ) {
        val updated = ApiPreset(
            id = presetId,
            name = name.trim(),
            baseUrl = baseUrl.trim().trimEnd('/'),
            apiKey = apiKey.trim(),
            model = model.trim(),
        )
        require(updated.name.isNotBlank()) { "配置名称不能为空" }
        store.edit { prefs ->
            val current = parsePresets(prefs[Keys.PRESETS], prefs[Keys.BASE_URL], prefs[Keys.API_KEY], prefs[Keys.MODEL])
            require(current.any { it.id == presetId }) { "配置不存在，请返回后重试" }
            val next = current.map { if (it.id == presetId) updated else it }
            prefs[Keys.PRESETS] = settingsJson.encodeToString(next)
            if (prefs[Keys.ACTIVE_PRESET] == presetId) {
                prefs[Keys.BASE_URL] = updated.baseUrl
                prefs[Keys.API_KEY] = updated.apiKey
                prefs[Keys.MODEL] = updated.model
            }
        }
    }

    suspend fun updateActiveBaseUrl(value: String) {
        updateActivePreset { it.copy(baseUrl = value.trim().trimEnd('/')) }
    }

    suspend fun updateActiveApiKey(value: String) {
        updateActivePreset { it.copy(apiKey = value.trim()) }
    }

    suspend fun updateActiveModel(value: String) {
        updateActivePreset { it.copy(model = value.trim()) }
    }

    private suspend fun updateActivePreset(transform: (ApiPreset) -> ApiPreset) {
        store.edit { prefs ->
            val current = parsePresets(prefs[Keys.PRESETS], prefs[Keys.BASE_URL], prefs[Keys.API_KEY], prefs[Keys.MODEL])
            val activeId = prefs[Keys.ACTIVE_PRESET]
                ?.takeIf { id -> current.any { it.id == id } }
                ?: current.firstOrNull()?.id.orEmpty()
            val next = current.map { if (it.id == activeId) transform(it) else it }
            val active = next.firstOrNull { it.id == activeId } ?: next.firstOrNull()
            prefs[Keys.PRESETS] = settingsJson.encodeToString(next)
            if (active != null) {
                prefs[Keys.BASE_URL] = active.baseUrl
                prefs[Keys.API_KEY] = active.apiKey
                prefs[Keys.MODEL] = active.model
            }
        }
    }

    suspend fun updateSystemBackground(value: String) {
        store.edit { it[Keys.SYSTEM_BG] = value }
    }

    private fun decodePromptOverrides(raw: String?): Map<String, String> =
        raw?.let { runCatching { settingsJson.decodeFromString<Map<String, String>>(it) }.getOrNull() }
            ?: emptyMap()

    /** Only called after a successful prompt test, or to restore a built-in default. */
    suspend fun updatePromptOverride(key: String, value: String?) {
        store.edit { prefs ->
            val next = decodePromptOverrides(prefs[Keys.PROMPT_OVERRIDES]).toMutableMap()
            if (value.isNullOrBlank()) next.remove(key) else next[key] = value
            prefs[Keys.PROMPT_OVERRIDES] = settingsJson.encodeToString(next)
        }
    }

    suspend fun updateFoodDataCentralApiKey(value: String) {
        store.edit { it[Keys.FDC_API_KEY] = value.trim() }
    }

    suspend fun updateDailyTarget(value: Float) {
        require(value.isFinite()) { "请输入有效数字" }
        store.edit { it[Keys.TARGET] = value.coerceIn(500f, 10000f) }
    }

    suspend fun updateProteinTarget(value: Float) {
        require(value.isFinite()) { "请输入有效数字" }
        store.edit { it[Keys.PROTEIN] = value.coerceIn(0f, 500f) }
    }

    suspend fun updateFatTarget(value: Float) {
        require(value.isFinite()) { "请输入有效数字" }
        store.edit { it[Keys.FAT] = value.coerceIn(0f, 300f) }
    }

    suspend fun updateCarbsTarget(value: Float) {
        require(value.isFinite()) { "请输入有效数字" }
        store.edit { it[Keys.CARBS] = value.coerceIn(0f, 800f) }
    }

    suspend fun updateHeight(value: Float) {
        require(value.isFinite()) { "请输入有效数字" }
        store.edit { it[Keys.HEIGHT] = value.coerceIn(50f, 250f) }
    }

    suspend fun updateWeight(value: Float) {
        require(value.isFinite()) { "请输入有效数字" }
        store.edit { it[Keys.WEIGHT] = value.coerceIn(20f, 300f) }
    }

    suspend fun updateAge(value: Int) {
        store.edit { it[Keys.AGE] = value.coerceIn(10, 100) }
    }

    suspend fun updateGender(value: Gender) {
        store.edit { it[Keys.GENDER] = value.name }
    }

    suspend fun updateActivityLevel(value: ActivityLevel) {
        store.edit { it[Keys.ACTIVITY] = value.name }
    }

    suspend fun updateProteinRingColor(value: Long) {
        store.edit { it[Keys.PROTEIN_RING] = value }
    }

    suspend fun updateCarbsRingColor(value: Long) {
        store.edit { it[Keys.CARBS_RING] = value }
    }

    suspend fun updateFatRingColor(value: Long) {
        store.edit { it[Keys.FAT_RING] = value }
    }

    suspend fun updateColorTheme(theme: ColorThemePreset) {
        val palette = FoodColorPalette.forPreset(theme, theme.seedColor)
        updateColorPalette(theme, theme.seedColor, palette)
    }

    suspend fun updateCustomColorTheme(seedColor: Long) {
        updateColorPalette(
            theme = ColorThemePreset.CUSTOM,
            seedColor = seedColor,
            palette = FoodColorPalette.forPreset(ColorThemePreset.CUSTOM, seedColor),
        )
    }

    private suspend fun updateColorPalette(
        theme: ColorThemePreset,
        seedColor: Long,
        palette: FoodColorPalette,
    ) {
        store.edit { prefs ->
            prefs[Keys.COLOR_THEME] = theme.name
            prefs[Keys.COLOR_SEED] = seedColor
            prefs[Keys.CALORIE_PROGRESS] = palette.calorie
            prefs[Keys.OVER_TARGET] = palette.overTarget
            prefs[Keys.PROTEIN_RING] = palette.protein
            prefs[Keys.CARBS_RING] = palette.carbs
            prefs[Keys.FAT_RING] = palette.fat
            prefs[Keys.BREAKFAST] = palette.breakfast
            prefs[Keys.LUNCH] = palette.lunch
            prefs[Keys.DINNER] = palette.dinner
            prefs[Keys.SNACK] = palette.snack
        }
    }

    suspend fun updateGlassEffectsEnabled(value: Boolean) {
        store.edit { it[Keys.GLASS_EFFECTS] = value }
    }

    suspend fun updateTopGradientBlurEnabled(value: Boolean) {
        store.edit { it[Keys.TOP_GRADIENT_BLUR] = value }
    }

    suspend fun updateTopGradientBlurRangeDp(value: Int) {
        store.edit { it[Keys.TOP_GRADIENT_BLUR_RANGE] = value.coerceIn(0, 240) }
    }

    suspend fun updateSavePhotosToGallery(value: Boolean) {
        store.edit { it[Keys.SAVE_PHOTOS_TO_GALLERY] = value }
    }

    suspend fun updateGallerySaveLocation(value: GallerySaveLocation) {
        store.edit { it[Keys.GALLERY_SAVE_LOCATION] = value.name }
    }
}
