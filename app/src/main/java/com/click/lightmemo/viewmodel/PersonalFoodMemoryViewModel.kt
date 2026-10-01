package com.click.lightmemo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.click.lightmemo.FoodApp
import com.click.lightmemo.domain.PersonalFoodMemory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PersonalFoodMemoryUiState(
    val memories: List<Pair<Long, PersonalFoodMemory>> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
)

class PersonalFoodMemoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as FoodApp).personalFoodMemoryRepository
    private val state = MutableStateFlow(PersonalFoodMemoryUiState())
    val uiState = state.asStateFlow()
    private var readJob: Job? = null

    init { refresh() }

    fun refresh() {
        readJob?.cancel()
        state.value = state.value.copy(loading = true, error = null)
        readJob = viewModelScope.launch {
            try {
                repository.memories.collect { rows -> state.value = state.value.copy(memories = rows, loading = false, error = null) }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { state.value = state.value.copy(loading = false, error = "个人记忆读取失败，请重试") }
        }
    }

    fun delete(id: Long) = mutate { repository.delete(id) }
    fun clear() = mutate { repository.clear() }

    private fun mutate(action: suspend () -> Unit) {
        if (state.value.busy) return
        state.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { state.value = state.value.copy(error = "个人记忆删除失败，请重试") }
            finally { state.value = state.value.copy(busy = false) }
        }
    }
}
