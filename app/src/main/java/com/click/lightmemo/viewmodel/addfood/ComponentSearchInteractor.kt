package com.click.lightmemo.viewmodel

import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.domain.NutritionReference
import com.click.lightmemo.network.FoodDataCentralClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Shared nutrition-database search for draft-edit and review-result overlays.
 * Only the state slot (`draftDatabaseSearch` vs `databaseSearch`) differs.
 */
internal class ComponentSearchInteractor(
    private val database: FoodDataCentralClient,
    private val uiState: MutableStateFlow<AddFoodUiState>,
    private val scope: CoroutineScope,
    private val apiKey: () -> String,
) {
    private var searchJob: Job? = null

    fun cancel() {
        searchJob?.cancel()
    }

    fun openDraft(component: FoodComponent) {
        val initialLookupQuery = component.databaseQuery.trim().ifBlank { component.name }
        searchJob?.cancel()
        uiState.value = uiState.value.copy(
            draftDatabaseSearch = DatabaseSearchState(
                componentId = component.id,
                query = component.name,
                loading = true,
                initialLookupQuery = initialLookupQuery,
            ),
        )
        search(
            componentId = component.id,
            displayQuery = component.name,
            lookupQuery = initialLookupQuery,
            keepInitialLookupQuery = true,
            isDraft = true,
        )
    }

    fun openDraftNew() {
        searchJob?.cancel()
        uiState.value = uiState.value.copy(
            draftDatabaseSearch = DatabaseSearchState(
                componentId = NewComponentId,
                query = "",
            ),
        )
    }

    fun searchDraft(componentId: String, query: String) {
        val current = uiState.value.draftDatabaseSearch
        val lookupQuery = resolveLookupQuery(current, componentId, query)
        search(
            componentId = componentId,
            displayQuery = query,
            lookupQuery = lookupQuery,
            keepInitialLookupQuery = false,
            isDraft = true,
        )
    }

    fun closeDraft() {
        searchJob?.cancel()
        uiState.value = uiState.value.copy(draftDatabaseSearch = null)
    }

    fun openReview(component: FoodComponent, replaceComponentName: Boolean = false) {
        // Replacing a component searches with the text shown in the input field.
        // The databaseQuery may be an English normalization used by recognition,
        // but it must not override the user's visible replacement term.
        val initialLookupQuery = if (replaceComponentName) {
            component.name
        } else {
            component.databaseQuery.trim().ifBlank { component.name }
        }
        val pinnedReference = component.nutritionReference.takeIf { replaceComponentName }
        uiState.value = uiState.value.copy(
            databaseSearch = DatabaseSearchState(
                componentId = component.id,
                query = component.name,
                loading = true,
                replaceComponentName = replaceComponentName,
                initialLookupQuery = initialLookupQuery,
                pinnedReference = pinnedReference,
            ),
        )
        search(
            componentId = component.id,
            displayQuery = component.name,
            lookupQuery = initialLookupQuery,
            keepInitialLookupQuery = true,
            pinnedReference = pinnedReference,
            isDraft = false,
        )
    }

    fun searchReview(componentId: String, query: String) {
        val current = uiState.value.databaseSearch
        val lookupQuery = resolveLookupQuery(current, componentId, query)
        search(
            componentId = componentId,
            displayQuery = query,
            lookupQuery = lookupQuery,
            keepInitialLookupQuery = false,
            pinnedReference = current?.pinnedReference,
            isDraft = false,
        )
    }

    fun closeReview() {
        uiState.value = uiState.value.copy(databaseSearch = null)
    }

    private fun resolveLookupQuery(
        current: DatabaseSearchState?,
        componentId: String,
        query: String,
    ): String {
        val useInitial = current?.componentId == componentId &&
            current.query.trim() == query.trim()
        return if (useInitial) current.initialLookupQuery ?: query else query
    }

    private fun search(
        componentId: String,
        displayQuery: String,
        lookupQuery: String,
        keepInitialLookupQuery: Boolean,
        pinnedReference: NutritionReference? = null,
        isDraft: Boolean,
    ) {
        searchJob?.cancel()
        val trimmed = lookupQuery.trim()
        if (trimmed.isBlank()) {
            updateSlot(componentId, isDraft) {
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
        updateSlot(componentId, isDraft) {
            it.copy(
                query = displayQuery,
                loading = true,
                error = null,
                initialLookupQuery = if (keepInitialLookupQuery) it.initialLookupQuery else null,
            )
        }
        searchJob = scope.launch {
            try {
                val results = database.searchCandidates(
                    query = trimmed,
                    apiKey = apiKey(),
                    limit = 20,
                )
                val orderedResults = pinnedReference?.let { currentReference ->
                    buildList {
                        add(currentReference)
                        addAll(results.filterNot { it.sameDatabaseEntryAs(currentReference) })
                    }
                } ?: results
                updateSlot(componentId, isDraft) {
                    it.copy(
                        loading = false,
                        results = orderedResults,
                        error = if (results.isEmpty()) "没有找到相近的食物" else null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateSlot(componentId, isDraft) {
                    it.copy(loading = false, results = emptyList(), error = e.message ?: "数据库查询失败")
                }
            }
        }
    }

    private fun NutritionReference.sameDatabaseEntryAs(other: NutritionReference): Boolean {
        return dataType == other.dataType && sourceId == other.sourceId
    }

    private inline fun updateSlot(
        componentId: String,
        isDraft: Boolean,
        transform: (DatabaseSearchState) -> DatabaseSearchState,
    ) {
        val state = uiState.value
        if (isDraft) {
            val current = state.draftDatabaseSearch ?: return
            if (current.componentId == componentId) {
                uiState.value = state.copy(draftDatabaseSearch = transform(current))
            }
        } else {
            val current = state.databaseSearch ?: return
            if (current.componentId == componentId) {
                uiState.value = state.copy(databaseSearch = transform(current))
            }
        }
    }
}
