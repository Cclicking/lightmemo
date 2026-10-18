package com.click.lightmemo.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.click.lightmemo.FoodApp
import com.click.lightmemo.domain.DiagnosticsSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class DiagnosticsUiState(
    val summary: DiagnosticsSummary = DiagnosticsSummary.from(emptyList()),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val endDate: LocalDate = LocalDate.now(),
)

class DiagnosticsViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as FoodApp).diagnosticsRepository
    private val logs = (app as FoodApp).recognitionLogRepository
    val logState = logs.state
    val uiState = MutableStateFlow(DiagnosticsUiState())
    private var readJob: kotlinx.coroutines.Job? = null
    init { refresh() }

    fun refresh() {
        readJob?.cancel()
        val today = LocalDate.now()
        uiState.update { it.copy(loading = true, error = null, endDate = today) }
        readJob = viewModelScope.launch {
            try {
                repository.recentDays(today = today).collect { rows ->
                    uiState.update { it.copy(summary = DiagnosticsSummary.from(rows), loading = false, error = null) }
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { uiState.update { it.copy(loading = false, error = "诊断数据暂时无法读取，请重试") } }
        }
    }

    fun export(uri: Uri) = perform {
        val text = repository.exportJson(today = uiState.value.endDate)
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(text)
        } ?: error("无法写入所选文件")
        "诊断信息已导出"
    }

    fun clear() = perform { repository.clear(); "诊断统计已清空" }
    fun setLogEnabled(enabled: Boolean) = perform {
        logs.setEnabled(enabled)
        if (enabled) "日志模式已开启，请重新执行一次识别" else "日志模式已关闭"
    }
    fun clearLogs() = perform { logs.clear(); "识别日志已清空" }
    fun exportLogs(uri: Uri) = perform {
        val text = logs.exportJson()
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(text)
        } ?: error("无法写入所选文件")
        "识别日志已导出"
    }
    fun consumeMessage() { uiState.update { it.copy(message = null) } }
    private fun perform(action: suspend () -> String) {
        if (uiState.value.busy) return
        uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val message = withContext(Dispatchers.IO) { action() }
                uiState.update { it.copy(message = message) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { uiState.update { it.copy(message = "操作失败，请检查文件或稍后重试") }
            } finally { uiState.update { it.copy(busy = false) } }
        }
    }
}
