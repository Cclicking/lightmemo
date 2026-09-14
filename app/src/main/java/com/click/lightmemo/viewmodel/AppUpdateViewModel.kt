package com.click.lightmemo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.click.lightmemo.BuildConfig
import com.click.lightmemo.network.AppUpdate
import com.click.lightmemo.network.AppUpdateClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUpdateUiState(
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val checking: Boolean = false,
    val available: AppUpdate? = null,
    val message: String? = null,
    val error: String? = null,
)

class AppUpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val client = AppUpdateClient()
    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    init {
        // Each activity gets its own ViewModel. Checking here keeps startup behavior
        // consistent for both the main screen and the About screen.
        checkForUpdate()
    }

    fun checkForUpdate() {
        if (_uiState.value.checking) return
        _uiState.update {
            it.copy(
                checking = true,
                available = null,
                message = null,
                error = null,
            )
        }
        viewModelScope.launch {
            try {
                val update = client.checkLatest(_uiState.value.currentVersionName)
                _uiState.update {
                    it.copy(
                        checking = false,
                        available = update,
                        message = if (update == null) "当前已是最新版本" else null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(checking = false, error = e.message ?: "检查更新失败，请稍后重试")
                }
            }
        }
    }
}
