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

    val settings: StateFlow<AppSettings> = repo.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    fun setBaseUrl(value: String) = viewModelScope.launch { repo.updateActiveBaseUrl(value) }

    fun setApiKey(value: String) = viewModelScope.launch { repo.updateActiveApiKey(value) }

    fun setModel(value: String) = viewModelScope.launch { repo.updateActiveModel(value) }

    fun setSystemBackground(value: String) = viewModelScope.launch { repo.updateSystemBackground(value) }

    fun selectPreset(id: String) = viewModelScope.launch { repo.selectPreset(id) }

    fun addPreset(name: String = "新配置") = viewModelScope.launch { repo.addPreset(name) }

    fun deleteActivePreset() = viewModelScope.launch {
        repo.deletePreset(settings.value.activePreset.id)
    }

    fun renamePreset(id: String, name: String) = viewModelScope.launch { repo.renamePreset(id, name) }

    fun setTarget(value: Float) = viewModelScope.launch { repo.updateDailyTarget(value) }

    fun setProteinTarget(value: Float) = viewModelScope.launch { repo.updateProteinTarget(value) }

    fun setFatTarget(value: Float) = viewModelScope.launch { repo.updateFatTarget(value) }

    fun setCarbsTarget(value: Float) = viewModelScope.launch { repo.updateCarbsTarget(value) }

    fun setHeight(value: Float) = viewModelScope.launch { repo.updateHeight(value) }

    fun setWeight(value: Float) = viewModelScope.launch { repo.updateWeight(value) }

    fun setAge(value: Int) = viewModelScope.launch { repo.updateAge(value) }

    fun setGender(value: Gender) = viewModelScope.launch { repo.updateGender(value) }

    fun setActivityLevel(value: ActivityLevel) = viewModelScope.launch { repo.updateActivityLevel(value) }

    /** 将推荐热量与营养素写入目标。 */
    fun applyRecommendedTarget() = viewModelScope.launch {
        val nutrients = settings.value.recommendedNutrients ?: return@launch
        repo.updateDailyTarget(nutrients.calories)
        repo.updateProteinTarget(nutrients.proteinG)
        repo.updateFatTarget(nutrients.fatG)
        repo.updateCarbsTarget(nutrients.carbsG)
    }
}
