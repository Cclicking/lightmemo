package com.click.lightmemo.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.click.lightmemo.FoodApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FoodApp).foodLogRepository
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun export(uri: Uri) = perform {
        val text = repo.exportJson()
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(text)
        } ?: error("无法写入所选文件")
        "记录已导出（不含照片和 API 密钥）"
    }

    fun import(uri: Uri) = perform {
        val limit = 20 * 1024 * 1024
        val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readNBytes(limit + 1) }
            ?: error("无法读取备份")
        require(bytes.size <= limit) { "备份超过 20 MB，请拆分后导入" }
        val count = repo.importJson(bytes.toString(Charsets.UTF_8))
        "已导入 $count 条记录，重复记录已跳过"
    }

    fun resetPresets() = perform {
        (getApplication<Application>() as FoodApp).settingsRepository.resetFoodPresets()
        "食物预设已恢复默认"
    }

    private fun perform(block: suspend () -> String) {
        if (busy.value) return
        busy.value = true
        message.value = null
        viewModelScope.launch {
            try {
                message.value = withContext(Dispatchers.IO) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = "操作失败：${e.message ?: "请检查文件后重试"}"
            } finally {
                busy.value = false
            }
        }
    }
}
