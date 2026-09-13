package com.foodcalorie.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foodcalorie.app.FoodApp
import com.foodcalorie.app.data.ActivityLevel
import com.foodcalorie.app.data.AppSettings
import com.foodcalorie.app.data.Gender
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FoodApp).settingsRepository

    val error = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val readError = repo.readError

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

    val settings: StateFlow<AppSettings> = repo.settings.stateIn(
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

    fun addPreset(name: String = "新配置") = saveSetting { repo.addPreset(name) }

    fun deleteActivePreset() = saveSetting {
        repo.deletePreset(settings.value.activePreset.id)
    }

    fun renamePreset(id: String, name: String) = saveSetting { repo.renamePreset(id, name) }

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

    fun setGlassEffectsEnabled(value: Boolean) = saveSetting {
        repo.updateGlassEffectsEnabled(value)
    }

    fun setTopGradientBlurEnabled(value: Boolean) = saveSetting {
        repo.updateTopGradientBlurEnabled(value)
    }

    fun setTopGradientBlurRangeDp(value: Int) = saveSetting {
        repo.updateTopGradientBlurRangeDp(value)
    }

    /** 将推荐热量与营养素写入目标。 */
    fun applyRecommendedTarget() = saveSetting {
        val nutrients = settings.value.recommendedNutrients ?: return@saveSetting
        repo.updateDailyTarget(nutrients.calories)
        repo.updateProteinTarget(nutrients.proteinG)
        repo.updateFatTarget(nutrients.fatG)
        repo.updateCarbsTarget(nutrients.carbsG)
    }
}
