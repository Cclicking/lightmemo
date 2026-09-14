package com.click.lightmemo.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.click.lightmemo.BuildConfig
import com.click.lightmemo.network.AppUpdate
import com.click.lightmemo.network.AppUpdateClient
import java.io.File
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
    val downloading: Boolean = false,
    val downloadProgress: Int? = null,
    val available: AppUpdate? = null,
    val downloadedApkPath: String? = null,
    val message: String? = null,
    val error: String? = null,
)

class AppUpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val client = AppUpdateClient()
    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdate() {
        if (_uiState.value.checking || _uiState.value.downloading) return
        _uiState.update {
            it.copy(
                checking = true,
                available = null,
                downloadedApkPath = null,
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

    fun downloadUpdate() {
        val update = _uiState.value.available ?: return
        if (_uiState.value.downloading) return

        val safeName = "lightmemo-${update.versionName}.apk"
        val target = File(getApplication<Application>().cacheDir, "updates/$safeName")
        _uiState.update {
            it.copy(
                downloading = true,
                downloadProgress = 0,
                downloadedApkPath = null,
                message = null,
                error = null,
            )
        }
        viewModelScope.launch {
            try {
                val downloaded = client.download(update, target) { downloadedBytes, totalBytes ->
                    val progress = if (totalBytes > 0) {
                        ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                    } else {
                        null
                    }
                    _uiState.update { it.copy(downloadProgress = progress) }
                }
                _uiState.update {
                    it.copy(
                        downloading = false,
                        downloadProgress = 100,
                        downloadedApkPath = downloaded.absolutePath,
                        message = "下载完成，请点击安装",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(downloading = false, downloadProgress = null, error = e.message ?: "下载更新失败，请重试")
                }
            }
        }
    }

    fun installDownloadedApk(context: Context) {
        val path = _uiState.value.downloadedApkPath ?: return
        val file = File(path)
        if (!file.isFile) {
            _uiState.update { it.copy(error = "更新文件已失效，请重新下载") }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _uiState.update { it.copy(error = "请允许本应用安装未知来源应用，返回后再次点击安装") }
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
            return
        }

        _uiState.update { it.copy(error = null) }
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        }.onFailure { error ->
            _uiState.update { it.copy(error = error.message ?: "无法打开系统安装器") }
        }
    }
}
