package com.click.lightmemo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.click.lightmemo.FoodApp
import com.click.lightmemo.data.AppSettings
import com.click.lightmemo.data.FoodLogRepository
import com.click.lightmemo.data.PresetFood
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
    val pinnedNames: Set<String> = emptySet(),
    val pinMessage: String? = null,
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

    init {
        viewModelScope.launch {
            settingsRepo.foodPresets.collect { presets ->
                _uiState.value = _uiState.value.copy(pinnedNames = presets.map { it.name }.toSet())
            }
        }
    }

    fun isPinned(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.isNotEmpty() && _uiState.value.pinnedNames.contains(trimmed)
    }

    /** 编辑记录页顶栏：把当前记录加入预设 */
    fun pinEntryAsPreset(entry: FoodLog) {
        val name = entry.name.trim()
        if (name.isEmpty()) return
        if (isPinned(name)) return
        val preset = PresetFood(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            defaultGrams = entry.grams.takeIf { it.isFinite() && it > 0.0 } ?: 100.0,
            portionLabel = "",
            nutrition = entry.nutrition.takeIf {
                it.caloriesKcal > 0.0 || it.proteinG > 0.0 || it.carbsG > 0.0 || it.fatG > 0.0
            },
            components = entry.components,
        )
        viewModelScope.launch {
            try {
                settingsRepo.updateFoodPreset(preset)
                _uiState.value = _uiState.value.copy(pinMessage = "已加入预设「$name」")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "加入预设失败")
            }
        }
    }

    /** 编辑记录页顶栏：按名称取消预设 */
    fun unpinEntryPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                val target = settingsRepo.foodPresets.first().firstOrNull { it.name == trimmed } ?: return@launch
                settingsRepo.deleteFoodPreset(target.id)
                _uiState.value = _uiState.value.copy(pinMessage = "已从预设移除「$trimmed」")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "取消预设失败")
            }
        }
    }

    fun consumePinMessage() {
        _uiState.value = _uiState.value.copy(pinMessage = null)
    }

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
        // The component name is user-facing (usually Chinese), while the
        // bundled USDA descriptions and the USDA API are searched in English.
        val initialLookupQuery = component.databaseQuery.trim().ifBlank { component.name }
        _uiState.value = _uiState.value.copy(
            databaseSearch = DatabaseSearchState(
                componentId = component.id,
                query = component.name,
                loading = true,
                initialLookupQuery = initialLookupQuery,
            ),
        )
        searchComponentDatabaseInternal(
            componentId = component.id,
            displayQuery = component.name,
            lookupQuery = initialLookupQuery,
            keepInitialLookupQuery = true,
        )
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
        val current = _uiState.value.databaseSearch
        val useInitialLookupQuery = current?.componentId == componentId &&
            current.query.trim() == query.trim()
        val lookupQuery = if (useInitialLookupQuery) {
            current.initialLookupQuery ?: query
        } else {
            query
        }
        searchComponentDatabaseInternal(
            componentId = componentId,
            displayQuery = query,
            lookupQuery = lookupQuery,
            keepInitialLookupQuery = false,
        )
    }

    private fun searchComponentDatabaseInternal(
        componentId: String,
        displayQuery: String,
        lookupQuery: String,
        keepInitialLookupQuery: Boolean,
    ) {
        databaseSearchJob?.cancel()
        val trimmed = lookupQuery.trim()
        if (trimmed.isBlank()) {
            updateDatabaseSearch(componentId) {
                it.copy(
                    query = displayQuery,
                    loading = false,
                    results = emptyList(),
                    error = "请输入食物名称",
                    initialLookupQuery = if (keepInitialLookupQuery) it.initialLookupQuery else null,
                )
            }
            return
        }
        updateDatabaseSearch(componentId) {
            it.copy(
                query = displayQuery,
                loading = true,
                error = null,
                initialLookupQuery = if (keepInitialLookupQuery) it.initialLookupQuery else null,
            )
        }
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
    val name = query.trim()
    val databaseQuery = if (reference.dataType.contains("中国")) name else reference.description
    return FoodComponent(
        id = java.util.UUID.randomUUID().toString(),
        name = name,
        databaseQuery = databaseQuery,
        chinaDatabaseQuery = name,
        source = com.click.lightmemo.domain.ComponentSource.USER_PROVIDED,
        estimatedWeightG = grams,
        weightMinG = grams,
        weightMaxG = grams,
        confidence = 1.0,
        nutritionReference = reference,
    )
}
