package com.click.lightmemo.viewmodel

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.click.lightmemo.FoodApp
import com.click.lightmemo.data.AppSettings
import com.click.lightmemo.data.FoodLogRepository
import com.click.lightmemo.data.PresetFood
import com.click.lightmemo.data.parseFoodLog
import com.click.lightmemo.data.toJsonLog
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
import org.json.JSONObject

data class EditFoodUiState(
    val entry: FoodLog? = null,
    val draft: FoodEditorDraft? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val databaseSearch: DatabaseSearchState? = null,
    val aiEstimatingComponentId: String? = null,
    val pinnedNames: Set<String> = emptySet(),
    val pinMessage: String? = null,
    val pinning: Boolean = false,
    val showLeaveConfirm: Boolean = false,
)

/** State holder for the full-screen food-log editor. */
class EditFoodViewModel(app: Application, private val savedState: SavedStateHandle = SavedStateHandle()) : AndroidViewModel(app) {
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

    /** The toolbar and save button commit the same validated draft. */
    fun toggleDraftPreset() {
        val state = _uiState.value
        if (state.loading || state.saving || state.pinning) return
        val entry = state.draft?.validatedFood()
        if (entry == null) {
            _uiState.value = state.copy(error = "请先填写有效的名称、重量、日期和营养数值")
            return
        }
        if (isPinned(entry.name)) unpinEntryPreset(entry.name) else pinEntryAsPreset(entry)
    }

    private fun pinEntryAsPreset(entry: FoodLog) {
        val name = entry.name.trim()
        if (name.isEmpty()) return
        if (isPinned(name)) return
        val preset = PresetFood(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            defaultGrams = entry.grams,
            portionLabel = "",
            nutrition = entry.nutrition,
            components = entry.components,
        )
        _uiState.value = _uiState.value.copy(pinning = true, error = null)
        viewModelScope.launch {
            try {
                settingsRepo.updateFoodPreset(preset)
                _uiState.value = _uiState.value.copy(pinMessage = "已加入预设「$name」")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "加入预设失败")
            } finally {
                _uiState.value = _uiState.value.copy(pinning = false)
            }
        }
    }

    /** 编辑记录页顶栏：按名称取消预设 */
    private fun unpinEntryPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _uiState.value = _uiState.value.copy(pinning = true, error = null)
        viewModelScope.launch {
            try {
                val target = settingsRepo.foodPresets.first().firstOrNull { it.name == trimmed } ?: return@launch
                settingsRepo.deleteFoodPreset(target.id)
                _uiState.value = _uiState.value.copy(pinMessage = "已从预设移除「$trimmed」")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "取消预设失败")
            } finally {
                _uiState.value = _uiState.value.copy(pinning = false)
            }
        }
    }

    fun consumePinMessage() {
        _uiState.value = _uiState.value.copy(pinMessage = null)
    }

    fun updateDraft(transform: (FoodEditorDraft) -> FoodEditorDraft) {
        val state = _uiState.value
        val current = state.draft ?: return
        if (state.saving || state.pinning) return
        val next = transform(current)
        savedState[DraftKey] = Bundle().apply {
            putString("food", next.food.toJsonLog().toString())
            putString("grams", next.grams)
            putString("date", next.date)
            putString("time", next.time)
        }
        _uiState.value = state.copy(draft = next, error = null)
    }

    fun updateFoodDraft(food: FoodLog) = updateDraft { it.copy(food = food) }

    fun requestBack(onExit: () -> Unit) {
        val state = _uiState.value
        if (state.saving || state.pinning) return
        if (state.databaseSearch != null) {
            closeComponentSearch()
        } else if (state.entry != null && state.draft != FoodEditorDraft.from(state.entry)) {
            _uiState.value = state.copy(showLeaveConfirm = true)
        } else {
            onExit()
        }
    }

    fun dismissLeaveConfirm() {
        _uiState.value = _uiState.value.copy(showLeaveConfirm = false)
    }

    fun discardDraft(onExit: () -> Unit) {
        if (_uiState.value.saving || _uiState.value.pinning) return
        savedState.remove<Bundle>(DraftKey)
        _uiState.value = _uiState.value.copy(showLeaveConfirm = false)
        onExit()
    }

    private fun restoredDraft(entryId: Long): FoodEditorDraft? = runCatching {
        val bundle = savedState.get<Bundle>(DraftKey) ?: return null
        FoodEditorDraft(
            food = parseFoodLog(JSONObject(requireNotNull(bundle.getString("food")))),
            grams = requireNotNull(bundle.getString("grams")),
            date = requireNotNull(bundle.getString("date")),
            time = requireNotNull(bundle.getString("time")),
        ).takeIf { it.food.id == entryId }
    }.getOrNull()

    fun load(entryId: Long) {
        if (loadedEntryId == entryId) return
        loadedEntryId = entryId
        viewModelScope.launch {
            try {
                val entry = repo.getById(entryId)
                _uiState.value = if (entry == null) {
                    _uiState.value.copy(entry = null, draft = null, loading = false, error = "找不到这条食物记录，可能已被删除")
                } else {
                    _uiState.value.copy(entry = entry, draft = restoredDraft(entryId) ?: FoodEditorDraft.from(entry), loading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = EditFoodUiState(loading = false, error = e.message ?: "记录读取失败")
            }
        }
    }

    fun openComponentSearch(component: FoodComponent) {
        // Tapping an existing component is a replacement action, so search
        // with the visible food name and let the selected result replace it.
        val initialLookupQuery = component.name
        _uiState.value = _uiState.value.copy(
            databaseSearch = DatabaseSearchState(
                componentId = component.id,
                query = component.name,
                loading = true,
                replaceComponentName = true,
                initialLookupQuery = initialLookupQuery,
                pinnedReference = component.nutritionReference,
                allowAiEstimate = component.nutritionReference == null,
                estimatedWeightG = component.estimatedWeightG,
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
                    results = withCurrentReference(componentId, emptyList()),
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
                        results = withCurrentReference(componentId, results),
                        error = if (results.isEmpty()) "没有找到相近的食物" else null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateDatabaseSearch(componentId) {
                    it.copy(
                        loading = false,
                        results = withCurrentReference(componentId, emptyList()),
                        error = e.message ?: "数据库查询失败",
                    )
                }
            }
        }
    }

    private fun withCurrentReference(
        componentId: String,
        results: List<NutritionReference>,
    ): List<NutritionReference> {
        val current = _uiState.value.databaseSearch
            ?.takeIf { it.componentId == componentId }
            ?.pinnedReference
            ?: return results
        return buildList {
            add(current)
            addAll(results.filterNot {
                it.sourceId == current.sourceId && it.dataType == current.dataType
            })
        }
    }

    fun closeComponentSearch() {
        databaseSearchJob?.cancel()
        _uiState.value = _uiState.value.copy(databaseSearch = null)
    }

    fun estimateComponentNutrition(
        componentId: String,
        foodName: String,
        weightG: Double,
        onResolved: (NutritionReference) -> Unit,
    ) {
        val state = _uiState.value
        if (state.saving || state.aiEstimatingComponentId != null) return
        _uiState.value = state.copy(
            aiEstimatingComponentId = componentId,
            error = null,
        )
        viewModelScope.launch {
            try {
                val appSettings = settings.value
                val nutrition = foodApp.recognitionClient.estimateNutrition(
                    baseUrl = appSettings.baseUrl,
                    apiKey = appSettings.apiKey,
                    model = appSettings.model,
                    foodName = foodName.trim().ifBlank { "食物" },
                    estimatedWeightG = weightG.takeIf { it.isFinite() && it > 0.0 } ?: 100.0,
                )
                onResolved(
                    NutritionReference(
                        sourceId = "ai-estimate-$componentId",
                        description = foodName.trim().ifBlank { "食物" },
                        dataType = "AI估算（仅供参考）",
                        per100g = nutrition,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "AI 营养估算失败，请稍后重试")
            } finally {
                _uiState.value = _uiState.value.copy(aiEstimatingComponentId = null)
            }
        }
    }

    fun saveDraft(onSaved: () -> Unit) {
        if (_uiState.value.saving || _uiState.value.pinning) return
        val updated = _uiState.value.draft?.validatedFood()
        if (updated == null) {
            _uiState.value = _uiState.value.copy(error = "请先填写有效的名称、重量、日期和营养数值")
            return
        }
        val original = _uiState.value.entry
        _uiState.value = _uiState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                repo.update(updated)
                original?.let { foodApp.diagnosticsRepository.recordSafely(com.click.lightmemo.domain.DiagnosticEvents.edited(
                    java.time.LocalDate.now().toEpochDay(), it, updated)) }
                if (original != null && com.click.lightmemo.domain.foodWasCorrected(updated, original)) {
                    foodApp.personalFoodMemoryRepository.learnAfterSave(updated, original)
                }
                savedState.remove<Bundle>(DraftKey)
                _uiState.value = _uiState.value.copy(entry = updated, draft = FoodEditorDraft.from(updated), showLeaveConfirm = false)
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
        private const val DraftKey = "food_editor_draft"
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
