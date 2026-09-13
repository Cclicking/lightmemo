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
import org.json.JSONObject

class BackupViewModel(app: Application) : AndroidViewModel(app) {
    private val foodApp = app as FoodApp
    private val foodLogRepository = foodApp.foodLogRepository
    private val settingsRepository = foodApp.settingsRepository
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun export(uri: Uri) = perform {
        val logs = JSONObject(foodLogRepository.exportJson())
        val settings = settingsRepository.exportBackupJson()
        val text = JSONObject().apply {
            put("format", "food-calorie-backup")
            put("version", 1)
            put("exportedAtMillis", System.currentTimeMillis())
            put("logs", logs.getJSONArray("logs"))
            put("foodPresets", settings.getJSONArray("foodPresets"))
            put("settings", settings.getJSONObject("settings"))
        }.toString(2)
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(text)
        } ?: error("无法写入所选文件")
        "全部数据已导出（包含 API 密钥，不含照片）"
    }

    fun import(uri: Uri) = perform {
        val limit = 20 * 1024 * 1024
        val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(limit + 1)
            var offset = 0
            while (offset < buffer.size) {
                val read = input.read(buffer, offset, buffer.size - offset)
                if (read < 0) break
                offset += read
            }
            buffer.copyOf(offset)
        } ?: error("无法读取备份")
        require(bytes.size <= limit) { "备份超过 20 MB，请拆分后导入" }
        val raw = bytes.toString(Charsets.UTF_8)
        val root = JSONObject(raw)
        when (root.optString("format")) {
            "food-calorie-logs" -> {
                val count = foodLogRepository.importJson(raw)
                "已导入 $count 条记录，重复记录已跳过"
            }
            "food-calorie-backup" -> {
                require(root.getInt("version") == 1) { "不支持的备份版本" }
                val logs = JSONObject()
                    .put("format", "food-calorie-logs")
                    .put("version", 1)
                    .put("logs", root.getJSONArray("logs"))
                val count = foodLogRepository.importJson(logs.toString())
                settingsRepository.importBackupJson(
                    settingsJsonObject = root.getJSONObject("settings"),
                    foodPresetsJson = root.getJSONArray("foodPresets"),
                )
                "已导入完整备份：新增 $count 条记录，设置与预设食物已恢复"
            }
            else -> error("不支持的备份格式")
        }
    }

    fun resetPresets() = perform {
        settingsRepository.resetFoodPresets()
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
