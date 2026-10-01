package com.click.lightmemo

import android.app.Application
import com.click.lightmemo.data.FoodLogRepository
import com.click.lightmemo.data.SettingsRepository
import com.click.lightmemo.data.FoodDatabase
import com.click.lightmemo.data.PersonalFoodMemoryRepository
import com.click.lightmemo.data.RecommendationRepository
import com.click.lightmemo.data.DiagnosticsRepository
import com.click.lightmemo.network.FoodRecognitionClient
import com.click.lightmemo.network.FoodDataCentralClient
import com.click.lightmemo.notification.MealReminderScheduler
import com.click.lightmemo.recognition.RecognitionTaskStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import com.click.lightmemo.widget.FoodWidgets
import kotlinx.coroutines.launch

class FoodApp : Application() {
    internal val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var foodLogRepository: FoodLogRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    val recognitionClient = FoodRecognitionClient(
        promptOverrides = { settingsRepository.settings.first().promptOverrides },
        onCall = { nutritionEstimate -> recordDiagnostic(com.click.lightmemo.data.LocalDiagnosticsEntity(
            java.time.LocalDate.now().toEpochDay(), llmCallCount = 1,
            aiNutritionFallbackCount = if (nutritionEstimate) 1 else 0)) },
    )
    val nutritionDatabase by lazy { FoodDataCentralClient(this) { source ->
        recordDiagnostic(com.click.lightmemo.domain.DiagnosticEvents.match(java.time.LocalDate.now().toEpochDay(), source))
    } }
    val recognitionTaskStore by lazy { RecognitionTaskStore(this) }
    val personalFoodMemoryRepository by lazy { PersonalFoodMemoryRepository(FoodDatabase.get(this)) }
    val recommendationRepository by lazy { RecommendationRepository(FoodDatabase.get(this)) }
    val diagnosticsRepository by lazy { DiagnosticsRepository(FoodDatabase.get(this)) }

    fun recordDiagnostic(delta: com.click.lightmemo.data.LocalDiagnosticsEntity) {
        appScope.launch { diagnosticsRepository.recordSafely(delta) }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        appScope.launch {
            runCatching { FoodWidgets.updateAll(this@FoodApp) }
                .onFailure { android.util.Log.w("FoodWidgets", "Unable to refresh widget theme", it) }
        }
    }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        foodLogRepository = FoodLogRepository(this)
        settingsRepository = SettingsRepository(this)
        appScope.launch {
            foodLogRepository.ensureMigrated()
            combine(foodLogRepository.allLogs(), settingsRepository.settings, settingsRepository.foodPresets,
                recommendationRepository.feedback.catch { emit(emptyList()) }) { _, _, _, _ -> Unit }
                .debounce(300).collect {
                    runCatching { FoodWidgets.updateAll(this@FoodApp) }
                        .onFailure { android.util.Log.w("FoodWidgets", "Unable to refresh widgets", it) }
                }
        }
        // One-time DataStore → Room migration; completes before first user action in practice.
        appScope.launch { foodLogRepository.ensureMigrated() }
        appScope.launch {
            MealReminderScheduler.schedule(this@FoodApp, settingsRepository.settings.first())
        }
    }
}
