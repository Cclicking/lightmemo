package com.click.lightmemo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.click.lightmemo.FoodApp
import com.click.lightmemo.data.AppSettings
import com.click.lightmemo.data.FoodLogRepository
import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.NutritionReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EditFoodUiState(
    val entry: FoodLog? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val databaseSearch: DatabaseSearchState? = null,
)

/** State holder for the full-screen food-log editor. */
class EditFoodViewModel(app: Application) : AndroidViewModel(app) {
    private val foodApp = app as FoodApp
    private val repo: FoodLogRepository = foodApp.foodLogRepository
    private val nutritionDatabase = foodApp.nutritionDatabase
    private val settingsRepo = foodApp.settingsRepository

    private val _uiState = MutableStateFlow(EditFoodUiState())
    val uiState: StateFlow<EditFoodUiState> = _uiState.asStateFlow()
    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppSettings(),
    )

    private var loadedEntryId: Long? = null
    private var databaseSearchJob: Job? = null

    fun load(entryId: Long) {
        if (loadedEntryId == entryId) return
        loadedEntryId = entryId
        viewModelScope.launch {
            try {
                val entry = repo.logs.first().firstOrNull { it.id == entryId }
                _uiState.value = if (entry == null) {
                    EditFoodUiState(loading = false, error = "找不到这条食物记录，可能已被删除")
                } else {
                    EditFoodUiState(entry = entry, loading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = EditFoodUiState(loading = false, error = e.message ?: "记录读取失败")
            }
        }
    }

    fun openComponentSearch(component: FoodComponent) {
        _uiState.value = _uiState.value.copy(
            databaseSearch = DatabaseSearchState(
                componentId = component.id,
                query = component.name,
                loading = true,
            ),
        )
        searchComponentDatabase(component.id, component.name)
    }

    fun openNewComponentSearch() {
        databaseSearchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            databaseSearch = DatabaseSearchState(
                componentId = NewComponentId,
                query = "",
            ),
        )
    }

    fun searchComponentDatabase(componentId: String, query: String) {
        databaseSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            updateDatabaseSearch(componentId) {
                it.copy(query = query, loading = false, results = emptyList(), error = "请输入食物名称")
            }
            return
        }
        updateDatabaseSearch(componentId) { it.copy(query = query, loading = true, error = null) }
        databaseSearchJob = viewModelScope.launch {
            try {
                val results = nutritionDatabase.searchCandidates(
                    query = trimmed,
                    apiKey = settings.value.foodDataCentralApiKey,
                    limit = 20,
                )
                updateDatabaseSearch(componentId) {
                    it.copy(
                        loading = false,
                        results = results,
                        error = if (results.isEmpty()) "没有找到相近的食物" else null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateDatabaseSearch(componentId) {
                    it.copy(loading = false, results = emptyList(), error = e.message ?: "数据库查询失败")
                }
            }
        }
    }

    fun closeComponentSearch() {
        databaseSearchJob?.cancel()
        _uiState.value = _uiState.value.copy(databaseSearch = null)
    }

    fun save(updated: FoodLog, onSaved: () -> Unit) {
        if (_uiState.value.saving) return
        _uiState.value = _uiState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                repo.update(updated)
                _uiState.value = _uiState.value.copy(entry = updated)
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "保存失败，请重试")
            } finally {
                _uiState.value = _uiState.value.copy(saving = false)
            }
        }
    }

    private fun updateDatabaseSearch(
        componentId: String,
        transform: (DatabaseSearchState) -> DatabaseSearchState,
    ) {
        val current = _uiState.value.databaseSearch ?: return
        if (current.componentId == componentId) {
            _uiState.value = _uiState.value.copy(databaseSearch = transform(current))
        }
    }

    companion object {
        const val NewComponentId = "__new_food_component__"
    }
}

/** Create a user-entered component from a selected database row. */
fun newFoodComponent(query: String, grams: Double, reference: NutritionReference): FoodComponent {
    return FoodComponent(
        id = java.util.UUID.randomUUID().toString(),
        name = query.trim(),
        databaseQuery = query.trim(),
        chinaDatabaseQuery = query.trim(),
        source = com.click.lightmemo.domain.ComponentSource.USER_PROVIDED,
        estimatedWeightG = grams,
        weightMinG = grams,
        weightMaxG = grams,
        confidence = 1.0,
        nutritionReference = reference,
    )
}
