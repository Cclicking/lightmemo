package com.foodcalorie.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
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

class SettingsRepository(private val context: Context) {
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
        val PROTEIN = floatPreferencesKey("protein_target_g")
        val FAT = floatPreferencesKey("fat_target_g")
        val CARBS = floatPreferencesKey("carbs_target_g")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
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
        )
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
                name = "默认配置",
                baseUrl = legacyBaseUrl ?: "https://api.openai.com/v1",
                apiKey = legacyApiKey.orEmpty(),
                model = legacyModel ?: "gpt-4o-mini",
            ),
        )
    }

    private suspend fun writePresets(presets: List<ApiPreset>, activeId: String) {
        val active = presets.firstOrNull { it.id == activeId } ?: presets.firstOrNull()
        context.settingsStore.edit { prefs ->
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
        context.settingsStore.edit { prefs ->
            prefs[Keys.ACTIVE_PRESET] = presetId
        }
    }

    suspend fun addPreset(name: String) {
        val preset = ApiPreset(name = name.ifBlank { "新配置" })
        context.settingsStore.edit { prefs ->
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
        context.settingsStore.edit { prefs ->
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
        context.settingsStore.edit { prefs ->
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
        context.settingsStore.edit { prefs ->
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
        context.settingsStore.edit { it[Keys.SYSTEM_BG] = value }
    }

    suspend fun updateDailyTarget(value: Float) {
        context.settingsStore.edit { it[Keys.TARGET] = value.coerceIn(500f, 10000f) }
    }

    suspend fun updateProteinTarget(value: Float) {
        context.settingsStore.edit { it[Keys.PROTEIN] = value.coerceIn(0f, 500f) }
    }

    suspend fun updateFatTarget(value: Float) {
        context.settingsStore.edit { it[Keys.FAT] = value.coerceIn(0f, 300f) }
    }

    suspend fun updateCarbsTarget(value: Float) {
        context.settingsStore.edit { it[Keys.CARBS] = value.coerceIn(0f, 800f) }
    }

    suspend fun updateHeight(value: Float) {
        context.settingsStore.edit { it[Keys.HEIGHT] = value.coerceIn(50f, 250f) }
    }

    suspend fun updateWeight(value: Float) {
        context.settingsStore.edit { it[Keys.WEIGHT] = value.coerceIn(20f, 300f) }
    }

    suspend fun updateAge(value: Int) {
        context.settingsStore.edit { it[Keys.AGE] = value.coerceIn(10, 100) }
    }

    suspend fun updateGender(value: Gender) {
        context.settingsStore.edit { it[Keys.GENDER] = value.name }
    }

    suspend fun updateActivityLevel(value: ActivityLevel) {
        context.settingsStore.edit { it[Keys.ACTIVITY] = value.name }
    }
}
