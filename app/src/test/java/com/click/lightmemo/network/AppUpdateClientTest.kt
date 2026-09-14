package com.click.lightmemo.network

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateClientTest {
    @Test fun versionComparisonHandlesNumericSegments() {
        assertTrue(compareVersions("1.10", "1.2") > 0)
        assertTrue(compareVersions("2.0", "1.99") > 0)
        assertEquals(0, compareVersions("v1.2.0", "1.2"))
    }

    @Test fun latestReleaseIsParsedAndCompared() = runBlocking {
        val client = AppUpdateClient(
            httpClient = OkHttpClient.Builder()
                .addInterceptor {
                    Response.Builder()
                        .request(it.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            """
                            {
                              "tag_name": "v1.3",
                              "name": "LightMemo 1.3",
                              "body": "修复问题",
                              "html_url": "https://github.com/Cclicking/lightmemo/releases/tag/v1.3",
                              "assets": [{
                                "name": "LightMemo-v1.3-release.apk",
                                "browser_download_url": "https://example.com/LightMemo-v1.3-release.apk",
                                "size": 2048
                              }]
                            }
                            """.trimIndent().toResponseBody()
                        )
                        .build()
                }
                .build(),
            latestReleaseUrl = "https://example.test/latest",
        )

        val update = client.checkLatest("1.2")
        assertEquals("1.3", update?.versionName)
        assertEquals("LightMemo-v1.3-release.apk", update?.apkFileName)
        assertEquals(2048L, update?.apkSizeBytes)
    }

    @Test fun olderReleaseIsIgnored() = runBlocking {
        val client = AppUpdateClient(
            httpClient = OkHttpClient.Builder()
                .addInterceptor {
                    Response.Builder()
                        .request(it.request())
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("{\"tag_name\":\"v1.1\"}".toResponseBody())
                        .build()
                }
                .build(),
            latestReleaseUrl = "https://example.test/latest",
        )

        assertNull(client.checkLatest("1.2"))
    }
}
