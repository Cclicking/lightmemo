package com.click.lightmemo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.click.lightmemo.FoodApp
import com.click.lightmemo.data.ActivityLevel
import com.click.lightmemo.data.AppSettings
import com.click.lightmemo.data.DEFAULT_PROFILE_AGE_YEARS
import com.click.lightmemo.data.DEFAULT_PROFILE_HEIGHT_CM
import com.click.lightmemo.data.DEFAULT_PROFILE_WEIGHT_KG
import com.click.lightmemo.data.Gender
import com.click.lightmemo.data.ColorThemePreset
import com.click.lightmemo.data.GallerySaveLocation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import com.click.lightmemo.network.RecognitionPrompt
import com.click.lightmemo.network.userFacingRecognitionError
import com.click.lightmemo.data.FoodImages
import com.click.lightmemo.notification.MealReminderScheduler
import android.net.Uri
import android.os.SystemClock

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FoodApp).settingsRepository
    private val appContext = app.applicationContext

    val error = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val readError = repo.readError
    val settingsReady = MutableStateFlow(false)
    val addingPreset = MutableStateFlow(false)
    val savingPreset = MutableStateFlow(false)
    val testingPrompt = MutableStateFlow(false)
    val promptTestResult = MutableStateFlow<String?>(null)
    val promptTestResponse = MutableStateFlow<String?>(null)
    val photoCacheBytes = MutableStateFlow<Long?>(null)
    val clearingPhotoCache = MutableStateFlow(false)

    private fun saveSetting(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            error.value = null
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error.value = e.message ?: "设置保存失败，请重试"
        }
    }

    val settings: StateFlow<AppSettings> = repo.settings.onEach { settingsReady.value = true }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    fun setBaseUrl(value: String) = saveSetting { repo.updateActiveBaseUrl(value) }

    fun setApiKey(value: String) = saveSetting { repo.updateActiveApiKey(value) }

    fun setModel(value: String) = saveSetting { repo.updateActiveModel(value) }

    fun setSystemBackground(value: String) = saveSetting { repo.updateSystemBackground(value) }

    fun setFoodDataCentralApiKey(value: String) = saveSetting {
        repo.updateFoodDataCentralApiKey(value)
    }

    fun selectPreset(id: String) = saveSetting { repo.selectPreset(id) }

    fun addPreset(name: String = "新配置", onAdded: () -> Unit = {}) {
        if (addingPreset.value) return
        addingPreset.value = true
        saveSetting {
            try {
                repo.addPreset(name)
                onAdded()
            } finally {
                addingPreset.value = false
            }
        }
    }

    fun deleteActivePreset(onDeleted: () -> Unit = {}) = saveSetting {
        repo.deletePreset(settings.value.activePreset.id)
        onDeleted()
    }

    fun restorePrompt(kind: RecognitionPrompt) = saveSetting {
        if (testingPrompt.value) return@saveSetting
        repo.updatePromptOverride(kind.name, null)
        promptTestResult.value = "已恢复${kind.label}的默认 Prompt"
    }

    fun testAndApplyPrompt(kind: RecognitionPrompt, draft: String, image: Uri?) {
        if (testingPrompt.value) return
        testingPrompt.value = true
        promptTestResult.value = null
        promptTestResponse.value = null
        viewModelScope.launch {
            val started = SystemClock.elapsedRealtime()
            try {
                require(draft.isNotBlank()) { "Prompt 不能为空" }
                val current = repo.settings.first()
                require(current.isRecognitionConfigured) { "请先配置 Base URL 与 API Key" }
                require(!kind.requiresImage || image != null) { "请先选择一张清晰的食物照片" }
                val encoded = image?.takeIf { kind.requiresImage }?.let { FoodImages.encode(getApplication(), it) }
                val response = (getApplication<Application>() as FoodApp).recognitionClient.testPrompt(
                    current.baseUrl, current.apiKey, current.model, kind, draft, encoded,
                )
                repo.updatePromptOverride(kind.name, draft)
                promptTestResponse.value = response
                val seconds = (SystemClock.elapsedRealtime() - started) / 1000.0
                promptTestResult.value = "${kind.label}测试通过并已应用（%.1f 秒）。结构和基础约束有效，识别准确性仍需人工确认。".format(seconds)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                promptTestResult.value = "${kind.label}测试失败：${userFacingRecognitionError(e)}继续使用上次有效 Prompt（未设置时使用默认值），草稿已保留。"
            } finally {
                testingPrompt.value = false
            }
        }
    }

    fun renamePreset(id: String, name: String) = saveSetting { repo.renamePreset(id, name) }

    fun savePreset(id: String, name: String, baseUrl: String, apiKey: String, model: String) {
        if (savingPreset.value) return
        savingPreset.value = true
        saveSetting {
            try {
                repo.updatePreset(id, name, baseUrl, apiKey, model)
            } finally {
                savingPreset.value = false
            }
        }
    }

    fun setTarget(value: Float) = saveSetting { repo.updateDailyTarget(value) }

    fun setProteinTarget(value: Float) = saveSetting { repo.updateProteinTarget(value) }

    fun setFatTarget(value: Float) = saveSetting { repo.updateFatTarget(value) }

    fun setCarbsTarget(value: Float) = saveSetting { repo.updateCarbsTarget(value) }

    fun setHeight(value: Float) = saveSetting { repo.updateHeight(value) }

    fun setWeight(value: Float) = saveSetting { repo.updateWeight(value) }

    fun setAge(value: Int) = saveSetting { repo.updateAge(value) }

    fun setGender(value: Gender) = saveSetting { repo.updateGender(value) }

    fun setActivityLevel(value: ActivityLevel) = saveSetting { repo.updateActivityLevel(value) }

    fun setProteinRingColor(value: Long) = saveSetting { repo.updateProteinRingColor(value) }

    fun setCarbsRingColor(value: Long) = saveSetting { repo.updateCarbsRingColor(value) }

    fun setFatRingColor(value: Long) = saveSetting { repo.updateFatRingColor(value) }

    fun setColorTheme(theme: ColorThemePreset) = saveSetting { repo.updateColorTheme(theme) }

    fun setCustomColorTheme(seedColor: Long) = saveSetting { repo.updateCustomColorTheme(seedColor) }

    fun setGlassEffectsEnabled(value: Boolean) = saveSetting {
        repo.updateGlassEffectsEnabled(value)
    }

    fun setTopGradientBlurEnabled(value: Boolean) = saveSetting {
        repo.updateTopGradientBlurEnabled(value)
    }

    fun setTopGradientBlurRangeDp(value: Int) = saveSetting {
        repo.updateTopGradientBlurRangeDp(value)
    }

    fun setSavePhotosToGallery(value: Boolean) = saveSetting {
        repo.updateSavePhotosToGallery(value)
    }

    fun setGallerySaveLocation(value: GallerySaveLocation) = saveSetting {
        repo.updateGallerySaveLocation(value)
    }

    fun setMealRemindersEnabled(value: Boolean) = saveSetting {
        repo.updateMealRemindersEnabled(value)
        refreshMealReminderScheduleAfterUpdate()
    }

    fun setBreakfastReminderMinute(value: Int) = saveSetting {
        repo.updateBreakfastReminderMinute(value)
        refreshMealReminderScheduleAfterUpdate()
    }

    fun setLunchReminderMinute(value: Int) = saveSetting {
        repo.updateLunchReminderMinute(value)
        refreshMealReminderScheduleAfterUpdate()
    }

    fun setDinnerReminderMinute(value: Int) = saveSetting {
        repo.updateDinnerReminderMinute(value)
        refreshMealReminderScheduleAfterUpdate()
    }

    fun refreshMealReminderSchedule() {
        viewModelScope.launch {
            try {
                refreshMealReminderScheduleAfterUpdate()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: "提醒设置保存失败，请重试"
            }
        }
    }

    private suspend fun refreshMealReminderScheduleAfterUpdate() {
        MealReminderScheduler.schedule(appContext, repo.settings.first())
    }

    fun refreshPhotoCacheSize() {
        viewModelScope.launch {
            try {
                photoCacheBytes.value = FoodImages.photoCacheStats(appContext).totalBytes
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                photoCacheBytes.value = null
            }
        }
    }

    /**
     * Clears temporary camera captures and app-private meal photos.
     * Does not touch photos already copied into the system gallery.
     */
    fun clearPhotoCache(onDone: (Long) -> Unit = {}) {
        if (clearingPhotoCache.value) return
        clearingPhotoCache.value = true
        viewModelScope.launch {
            try {
                error.value = null
                val freed = FoodImages.clearPhotoCache(appContext).totalBytes
                photoCacheBytes.value = 0L
                onDone(freed)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: "清理失败，请重试"
            } finally {
                clearingPhotoCache.value = false
            }
        }
    }

    /** 将推荐热量与营养素写入目标。 */
    fun applyRecommendedTarget(onSuccess: () -> Unit = {}) = saveSetting {
        val current = settings.value
        val nutrients = current.recommendedNutrients ?: current.copy(
            heightCm = current.heightCm.takeIf { it > 0f } ?: DEFAULT_PROFILE_HEIGHT_CM,
            weightKg = current.weightKg.takeIf { it > 0f } ?: DEFAULT_PROFILE_WEIGHT_KG,
            ageYears = current.ageYears.takeIf { it > 0 } ?: DEFAULT_PROFILE_AGE_YEARS,
        ).recommendedNutrients ?: return@saveSetting
        repo.updateDailyTarget(nutrients.calories)
        repo.updateProteinTarget(nutrients.proteinG)
        repo.updateFatTarget(nutrients.fatG)
        repo.updateCarbsTarget(nutrients.carbsG)
        onSuccess()
    }
}
