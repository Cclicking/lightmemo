package com.click.lightmemo.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request

/** Checks the metadata published in the project's latest GitHub Release. */
class AppUpdateClient internal constructor(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        // Some networks/proxies advertise HTTP/2 but return an HTTP/1.1 response,
        // which makes OkHttp fail with "Required SETTINGS preface not received".
        .protocols(listOf(Protocol.HTTP_1_1))
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

            AppUpdate(
                versionName = versionName,
                releaseName = release.name.orEmpty(),
                // The update dialog intentionally shows only the Release body,
                // without Markdown heading lines such as "# 更新日志" or "## 1.3".
                releaseNotes = extractReleaseBody(release.body.orEmpty()),
                releaseUrl = release.htmlUrl,
            )
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

private val MarkdownHeading = Regex("^\\s{0,3}#{1,6}(?:\\s+.*)?\\s*$")

internal fun extractReleaseBody(markdown: String): String = markdown
    .lineSequence()
    .filterNot { MarkdownHeading.matches(it) }
    .joinToString("\n")
    .replace(Regex("\\n{3,}"), "\\n\\n")
    .trim()

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
)
