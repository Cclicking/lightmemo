package com.click.lightmemo.network

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** Checks and downloads the APK published in the project's latest GitHub Release. */
class AppUpdateClient internal constructor(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val latestReleaseUrl: String = LatestReleaseUrl,
) {
    suspend fun checkLatest(currentVersionName: String): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "LightMemo/$currentVersionName")
            .build()

        httpClient.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) {
                throw IOException("检查更新失败：HTTP ${response.code}")
            }
            val release = json.decodeFromString<GithubRelease>(response.body?.string().orEmpty())
            val versionName = parseVersion(release.tagName)
                ?: throw IOException("最新版本号格式不支持：${release.tagName}")
            if (compareVersions(versionName, currentVersionName) <= 0) return@withContext null

            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: throw IOException("最新版本没有可安装的 APK")
            AppUpdate(
                versionName = versionName,
                releaseName = release.name.orEmpty(),
                releaseNotes = release.body.orEmpty().trim(),
                releaseUrl = release.htmlUrl,
                apkUrl = apk.browserDownloadUrl,
                apkFileName = apk.name,
                apkSizeBytes = apk.size,
            )
        }
    }

    suspend fun download(
        update: AppUpdate,
        target: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val parent = target.parentFile ?: throw IOException("无法创建更新目录")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("无法创建更新目录")
        }
        val partial = File(target.parentFile, "${target.name}.part")
        partial.delete()

        try {
            val request = Request.Builder()
                .url(update.apkUrl)
                .header("User-Agent", "LightMemo/${update.versionName}")
                .build()
            httpClient.newCall(request).awaitResponse().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("下载更新失败：HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("下载更新失败：响应为空")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                body.byteStream().use { input ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloadedBytes += count
                            onProgress(downloadedBytes, totalBytes)
                        }
                        output.fd.sync()
                    }
                }
            }
            if (target.exists() && !target.delete()) {
                throw IOException("无法替换旧的更新文件")
            }
            if (!partial.renameTo(target)) {
                throw IOException("无法保存更新文件")
            }
            target
        } catch (e: CancellationException) {
            partial.delete()
            throw e
        } catch (e: Exception) {
            partial.delete()
            throw e
        }
    }

    companion object {
        const val LatestReleaseUrl =
            "https://api.github.com/repos/Cclicking/lightmemo/releases/latest"
    }
}

data class AppUpdate(
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val apkUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
)

internal fun parseVersion(raw: String): String? {
    val cleaned = raw.trim().removePrefix("v").removePrefix("V")
    val version = cleaned.takeWhile { it.isDigit() || it == '.' }
    return version.takeIf { it.isNotBlank() && it.first().isDigit() && it.last() != '.' }
}

internal fun compareVersions(left: String, right: String): Int {
    val leftParts = parseVersion(left).orEmpty().split('.').map { it.toIntOrNull() ?: 0 }
    val rightParts = parseVersion(right).orEmpty().split('.').map { it.toIntOrNull() ?: 0 }
    val count = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until count) {
        val comparison = (leftParts.getOrElse(index) { 0 }).compareTo(rightParts.getOrElse(index) { 0 })
        if (comparison != 0) return comparison
    }
    return 0
}

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L,
)
