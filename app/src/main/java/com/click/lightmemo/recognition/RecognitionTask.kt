package com.click.lightmemo.recognition

import android.content.Context
import com.click.lightmemo.domain.MealRecognition
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.domain.RecognitionStage
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class RecognitionRequestType {
    IMAGE,
    TEXT,
    MANUAL_GRAMS,
    MANUAL_PORTIONS,
    REPLACE_DISH,
}

/** All user input needed to resume an in-flight recognition after the UI is gone. */
@Serializable
data class RecognitionRequest(
    val id: String = UUID.randomUUID().toString(),
    val type: RecognitionRequestType,
    val imageUri: String? = null,
    val text: String? = null,
    val foodName: String? = null,
    val grams: Double? = null,
    val portions: Double? = null,
    val portionHint: String = "",
    val mealType: MealType,
    val targetDateEpochDay: Long,
    val mealMinuteOfDay: Int,
    val note: String = "",
    val selectedTags: List<String> = emptyList(),
    val plateSize: String = "",
    val baseResult: MealRecognition? = null,
    val baseOriginalResult: MealRecognition? = null,
    val replaceDishId: String? = null,
    /** null asks on a hit; true reuses it; false forces a new recognition. */
    val reuseImageCache: Boolean? = null,
)

@Serializable
enum class RecognitionTaskStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    AWAITING_CACHE_CONFIRMATION,
}

@Serializable
data class RecognitionTaskRecord(
    val request: RecognitionRequest,
    val status: RecognitionTaskStatus = RecognitionTaskStatus.RUNNING,
    val stage: RecognitionStage = RecognitionStage.PREPARING,
    val result: MealRecognition? = null,
    val imageUri: String? = null,
    val manualNutrition: Nutrition? = null,
    val estimatedPortionGrams: Double? = null,
    val error: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val path: com.click.lightmemo.domain.RecognitionPath = com.click.lightmemo.domain.RecognitionPath.FULL,
    val modelCallCount: Int = 0,
    val durationMillis: Long = 0,
    val cacheReused: Boolean = false,
)

/** Process-safe handoff between the foreground service and the screen ViewModel. */
class RecognitionTaskStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val lock = Any()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<RecognitionTaskRecord?> = _state.asStateFlow()

    fun begin(request: RecognitionRequest) {
        write(RecognitionTaskRecord(request = request))
    }

    fun updateStage(taskId: String, stage: RecognitionStage) {
        update(taskId) { it.copy(stage = stage, updatedAtMillis = System.currentTimeMillis()) }
    }

    fun complete(
        taskId: String,
        result: MealRecognition? = null,
        imageUri: String? = null,
        manualNutrition: Nutrition? = null,
        estimatedPortionGrams: Double? = null,
        path: com.click.lightmemo.domain.RecognitionPath = com.click.lightmemo.domain.RecognitionPath.FULL,
        modelCallCount: Int = 0,
        durationMillis: Long = 0,
        cacheReused: Boolean = false,
    ) {
        update(taskId) {
            it.copy(
                status = RecognitionTaskStatus.COMPLETED,
                stage = RecognitionStage.COMPLETED,
                result = result,
                imageUri = imageUri,
                manualNutrition = manualNutrition,
                estimatedPortionGrams = estimatedPortionGrams,
                error = null,
                path = path,
                modelCallCount = modelCallCount,
                durationMillis = durationMillis,
                cacheReused = cacheReused,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    fun fail(taskId: String, message: String) {
        update(taskId) {
            it.copy(
                status = RecognitionTaskStatus.FAILED,
                error = message,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    fun awaitCacheConfirmation(taskId: String) {
        update(taskId) { it.copy(status = RecognitionTaskStatus.AWAITING_CACHE_CONFIRMATION) }
    }

    fun clear(taskId: String? = null) {
        synchronized(lock) {
            if (taskId != null && _state.value?.request?.id != taskId) return
            preferences.edit().remove(KEY_RECORD).apply()
            _state.value = null
        }
    }

    private fun update(taskId: String, transform: (RecognitionTaskRecord) -> RecognitionTaskRecord) {
        synchronized(lock) {
            val current = _state.value ?: return
            if (current.request.id != taskId) return
            write(transform(current))
        }
    }

    private fun write(record: RecognitionTaskRecord?) {
        synchronized(lock) {
            if (record == null) {
                preferences.edit().remove(KEY_RECORD).apply()
            } else {
                preferences.edit().putString(KEY_RECORD, json.encodeToString(record)).apply()
            }
            _state.value = record
        }
    }

    private fun load(): RecognitionTaskRecord? = synchronized(lock) {
        preferences.getString(KEY_RECORD, null)?.let { raw ->
            runCatching { json.decodeFromString<RecognitionTaskRecord>(raw) }.getOrNull()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "recognition_task"
        const val KEY_RECORD = "record"
    }
}
