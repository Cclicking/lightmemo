package com.foodcalorie.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foodcalorie.app.FoodApp
import com.foodcalorie.app.data.AppSettings
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

    fun setBaseUrl(value: String) = viewModelScope.launch { repo.updateBaseUrl(value) }

    fun setApiKey(value: String) = viewModelScope.launch { repo.updateApiKey(value) }

    fun setModel(value: String) = viewModelScope.launch { repo.updateModel(value) }

    fun setTarget(value: Float) = viewModelScope.launch { repo.updateDailyTarget(value) }
}
