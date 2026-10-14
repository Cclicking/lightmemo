package com.click.lightmemo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.click.lightmemo.FoodApp
import com.click.lightmemo.data.AppSettings
import com.click.lightmemo.data.DailyTargets
import com.click.lightmemo.network.userFacingRecognitionError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class OnboardingState(
    val loading: Boolean = true,
    val step: Int = 0,
    val settings: AppSettings = AppSettings(),
    val targets: DailyTargets = DailyTargets(1800f, 90f, 60f, 225f),
    val targetsEdited: Boolean = false,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val connectionMessage: String? = null,
)

class OnboardingViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as FoodApp
    private val repo = app.settingsRepository
    private val mutableState = MutableStateFlow(
        if (repo.hasCompletedOnboarding()) OnboardingState(loading = false, step = 4)
        else OnboardingState(),
    )
    val state = mutableState.asStateFlow()

    init { initialize() }

    fun initialize() = action {
        val settings = repo.settings.first()
        val savedStep = repo.onboardingStep.first()
        val step = savedStep ?: run {
            app.foodLogRepository.ensureMigrated()
            repo.initializeOnboarding(app.foodLogRepository.allLogs().first().isNotEmpty())
        }
        repo.synchronizeOnboardingCache(step)
        val profile = settings.copy(
            heightCm = savedState["height"] ?: settings.heightCm,
            weightKg = savedState["weight"] ?: settings.weightKg,
            ageYears = savedState["age"] ?: settings.ageYears,
            gender = savedState.get<String>("gender")?.let { com.click.lightmemo.data.Gender.valueOf(it) } ?: settings.gender,
            activityLevel = savedState.get<String>("activity")?.let { com.click.lightmemo.data.ActivityLevel.valueOf(it) } ?: settings.activityLevel,
            fitnessGoal = savedState.get<String>("goal")?.let { com.click.lightmemo.data.FitnessGoal.valueOf(it) } ?: settings.fitnessGoal,
        )
        val recommendation = settings.recommendedNutrients
        val initialTargets = if (settings.proteinTargetG <= 0f && recommendation != null && step >= 2)
            DailyTargets(recommendation.calories, recommendation.proteinG, recommendation.fatG, recommendation.carbsG)
            else settings.currentTargets.takeIf { it.errors().isEmpty() } ?: OnboardingState().targets
        val targets = DailyTargets(savedState["calories"] ?: initialTargets.calories,
            savedState["protein"] ?: initialTargets.proteinG, savedState["fat"] ?: initialTargets.fatG,
            savedState["carbs"] ?: initialTargets.carbsG)
        mutableState.value = OnboardingState(loading = false, step = step, settings = profile,
            targets = targets, targetsEdited = savedState["targetsEdited"] ?: (settings.proteinTargetG > 0f),
            baseUrl = savedState["baseUrl"] ?: settings.baseUrl, apiKey = savedState["apiKey"] ?: settings.apiKey,
            model = savedState["model"] ?: settings.model)
    }

    fun editProfile(profile: AppSettings) {
        if (state.value.busy) return
        savedState["height"] = profile.heightCm
        savedState["weight"] = profile.weightKg
        savedState["age"] = profile.ageYears
        savedState["gender"] = profile.gender.name
        savedState["activity"] = profile.activityLevel.name
        savedState["goal"] = profile.fitnessGoal.name
        mutableState.update { it.copy(settings = profile, error = null) }
    }

    fun editTargets(targets: DailyTargets) {
        if (state.value.busy) return
        rememberTargets(targets, true)
        mutableState.update { it.copy(targets = targets, targetsEdited = true, error = null) }
    }

    fun useRecommendation() {
        val recommended = state.value.settings.recommendedNutrients ?: return
        val targets = DailyTargets(recommended.calories, recommended.proteinG, recommended.fatG, recommended.carbsG)
        rememberTargets(targets, false)
        mutableState.update { it.copy(targets = targets, targetsEdited = false, error = null) }
    }

    private fun rememberTargets(targets: DailyTargets, edited: Boolean) {
        savedState["calories"] = targets.calories
        savedState["protein"] = targets.proteinG
        savedState["fat"] = targets.fatG
        savedState["carbs"] = targets.carbsG
        savedState["targetsEdited"] = edited
    }

    fun editApi(baseUrl: String, apiKey: String, model: String) {
        if (state.value.busy) return
        savedState["baseUrl"] = baseUrl
        savedState["apiKey"] = apiKey
        savedState["model"] = model
        mutableState.update { it.copy(baseUrl = baseUrl, apiKey = apiKey, model = model,
            connectionMessage = null, error = null) }
    }

    fun next() = action {
        val current = state.value
        if (current.step >= 4) return@action
        when (current.step) {
            1 -> {
                val confirmed = current.settings.copy(
                    heightCm = current.settings.heightCm.takeIf { it > 0f } ?: com.click.lightmemo.data.DEFAULT_PROFILE_HEIGHT_CM,
                    weightKg = current.settings.weightKg.takeIf { it > 0f } ?: com.click.lightmemo.data.DEFAULT_PROFILE_WEIGHT_KG,
                    ageYears = current.settings.ageYears.takeIf { it > 0 } ?: com.click.lightmemo.data.DEFAULT_PROFILE_AGE_YEARS,
                )
                repo.savePersonalProfile(confirmed)
                mutableState.update { it.copy(settings = confirmed) }
                if (!current.targetsEdited) useRecommendation()
            }
            2 -> repo.updateTargets(current.targets)
            3 -> {
                require(current.baseUrl.isNotBlank() && current.apiKey.isNotBlank() && current.model.isNotBlank()) {
                    "请填写服务地址、API Key 和模型名，或选择稍后配置"
                }
                require(current.baseUrl.trim().startsWith("https://") || current.baseUrl.trim().startsWith("http://")) {
                    "服务地址需要以 https:// 或 http:// 开头"
                }
                val preset = current.settings.activePreset
                repo.updatePreset(preset.id, preset.name, current.baseUrl, current.apiKey, current.model)
            }
        }
        moveTo(current.step + 1)
    }

    fun skip() = action {
        val current = state.value
        if (current.step >= 4) return@action
        // Skipping a profile must not use unconfirmed draft values for recommendations.
        if (current.step == 1) {
            val saved = repo.settings.first()
            listOf("height", "weight", "age", "gender", "activity", "goal").forEach { savedState.remove<Any>(it) }
            mutableState.update { it.copy(settings = saved) }
        }
        moveTo(if (current.step == 0 || current.step == 3) 4 else current.step + 1)
    }

    fun back() = action {
        if (state.value.step < 4) moveTo((state.value.step - 1).coerceAtLeast(0))
    }

    fun goToStep(step: Int) = action {
        if (state.value.step < 4 && step in 0 until state.value.step) moveTo(step)
    }

    private suspend fun moveTo(step: Int) {
        repo.setOnboardingStep(step)
        mutableState.update { it.copy(step = step, error = null, connectionMessage = null) }
    }

    fun testConnection() = action {
        if (state.value.step >= 4) return@action
        val current = state.value
        try {
            withTimeout(20_000) {
                app.recognitionClient.testConnection(current.baseUrl, current.apiKey, current.model)
            }
        } catch (_: TimeoutCancellationException) {
            throw IllegalStateException("连接超时，请检查服务地址与网络后重试")
        }
        mutableState.update { it.copy(connectionMessage = "文字连接成功；图片识别能力需在实际使用时确认。") }
    }

    private fun action(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutableState.update { it.copy(error = userFacingRecognitionError(e)) }
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }
}
