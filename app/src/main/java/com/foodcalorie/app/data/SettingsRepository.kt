package com.foodcalorie.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "food_settings")

data class AppSettings(
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val dailyCalorieTarget: Float = 1800f,
) {
    val isRecognitionConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank()
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val TARGET = floatPreferencesKey("daily_target")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(
            baseUrl = prefs[Keys.BASE_URL] ?: "https://api.openai.com/v1",
            apiKey = prefs[Keys.API_KEY] ?: "",
            model = prefs[Keys.MODEL] ?: "gpt-4o-mini",
            dailyCalorieTarget = prefs[Keys.TARGET] ?: 1800f,
        )
    }

    suspend fun updateBaseUrl(value: String) {
        context.settingsStore.edit { it[Keys.BASE_URL] = value.trim().trimEnd('/') }
    }

    suspend fun updateApiKey(value: String) {
        context.settingsStore.edit { it[Keys.API_KEY] = value.trim() }
    }

    suspend fun updateModel(value: String) {
        context.settingsStore.edit { it[Keys.MODEL] = value.trim() }
    }

    suspend fun updateDailyTarget(value: Float) {
        context.settingsStore.edit { it[Keys.TARGET] = value.coerceIn(500f, 10000f) }
    }
}
