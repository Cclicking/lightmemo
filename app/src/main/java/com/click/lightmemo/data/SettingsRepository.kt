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
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.settingsStore by preferencesDataStore(name = "food_settings")
private val settingsJson = Json { ignoreUnknownKeys = true }

enum class Gender(val label: String) {
    MALE("男"),
    FEMALE("女"),
}

enum class ActivityLevel(val label: String, val factor: Float, val summary: String) {
    LOW("低强度", 1.2f, "久坐，每周几乎不运动"),
    MEDIUM("中强度", 1.55f, "每周 3–5 天中等强度运动"),
    HIGH("高强度", 1.725f, "每周 6–7 天高强度运动"),
}

@Serializable
data class ApiPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "默认配置",
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
)

data class AppSettings(
    val apiPresets: List<ApiPreset> = listOf(ApiPreset()),
    val activePresetId: String = apiPresets.firstOrNull()?.id.orEmpty(),
    val systemBackground: String = "",
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
    val proteinRingColor: Long = 0xFFF3A17C,
    val carbsRingColor: Long = 0xFF2F7D2B,
    val fatRingColor: Long = 0xFFFFB300,
    /** 总开关：默认关闭玻璃特效，避免弱 GPU/模拟器冷启动首帧 ANR */
    val glassEffectsEnabled: Boolean = false,
    val topGradientBlurEnabled: Boolean = false,
    /** 顶部渐变模糊覆盖范围，单位 dp。 */
    val topGradientBlurRangeDp: Int = 72,
) {
    val activePreset: ApiPreset
        get() = apiPresets.firstOrNull { it.id == activePresetId }
            ?: apiPresets.firstOrNull()
            ?: ApiPreset()

    val baseUrl: String get() = activePreset.baseUrl
    val apiKey: String get() = activePreset.apiKey
    val model: String get() = activePreset.model

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
        val FDC_API_KEY = stringPreferencesKey("food_data_central_api_key")
        val PROTEIN = floatPreferencesKey("protein_target_g")
        val FAT = floatPreferencesKey("fat_target_g")
        val CARBS = floatPreferencesKey("carbs_target_g")
        val PROTEIN_RING = longPreferencesKey("protein_ring_color")
        val CARBS_RING = longPreferencesKey("carbs_ring_color")
        val FAT_RING = longPreferencesKey("fat_ring_color")
        val GLASS_EFFECTS = booleanPreferencesKey("glass_effects_enabled")
        val TOP_GRADIENT_BLUR = booleanPreferencesKey("top_gradient_blur_enabled")
        val TOP_GRADIENT_BLUR_RANGE = intPreferencesKey("top_gradient_blur_range_dp")
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

        AppSettings(
            apiPresets = presets,
            activePresetId = activeId,
            systemBackground = prefs[Keys.SYSTEM_BG] ?: "",
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
            proteinRingColor = prefs[Keys.PROTEIN_RING] ?: 0xFFF3A17C,
            carbsRingColor = prefs[Keys.CARBS_RING] ?: 0xFF2F7D2B,
            fatRingColor = prefs[Keys.FAT_RING] ?: 0xFFFFB300,
            glassEffectsEnabled = prefs[Keys.GLASS_EFFECTS] ?: false,
            topGradientBlurEnabled = prefs[Keys.TOP_GRADIENT_BLUR] ?: false,
            topGradientBlurRangeDp = (prefs[Keys.TOP_GRADIENT_BLUR_RANGE] ?: 72).coerceIn(0, 240),
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
                baseUrl = legacyBaseUrl ?: "https://api.openai.com/v1",
                apiKey = legacyApiKey.orEmpty(),
                model = legacyModel ?: "gpt-4o-mini",
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

    suspend fun updateGlassEffectsEnabled(value: Boolean) {
        store.edit { it[Keys.GLASS_EFFECTS] = value }
    }

    suspend fun updateTopGradientBlurEnabled(value: Boolean) {
        store.edit { it[Keys.TOP_GRADIENT_BLUR] = value }
    }

    suspend fun updateTopGradientBlurRangeDp(value: Int) {
        store.edit { it[Keys.TOP_GRADIENT_BLUR_RANGE] = value.coerceIn(0, 240) }
    }
}
