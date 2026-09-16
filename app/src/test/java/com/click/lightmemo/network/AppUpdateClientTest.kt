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

    @Test fun releaseBodyFiltersMarkdownHeadings() {
        assertEquals(
            "新增：功能一。\n\n修复：问题二。",
            extractReleaseBody("# 更新日志\n\n## 1.3\n\n新增：功能一。\n\n修复：问题二。"),
        )
    }

    @Test fun releaseNotesOnlyIncludeTargetVersion() {
        assertEquals(
            "新增：功能一。\n\n修复：问题二。",
            extractReleaseNotes(
                """
                # 更新日志

                ## 1.3.2

                新增：功能一。

                修复：问题二。

                ## 1.3

                新增：旧功能。
                """.trimIndent(),
                "v1.3.2",
            ),
        )
    }

    @Test fun releaseNotesDoNotIncludeOtherVersionsWhenTargetIsAbsent() {
        assertEquals(
            "",
            extractReleaseNotes(
                "## 1.3\n\n新增：旧功能。\n\n## 1.2\n\n修复：旧问题。",
                "1.3.2",
            ),
        )
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
                              "body": "## 1.3\n\n修复问题",
                              "assets": []
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
        assertEquals("LightMemo 1.3", update?.releaseName)
        assertEquals("修复问题", update?.releaseNotes)
        assertEquals("https://gitee.com/clicking/lightmemo/releases/v1.3", update?.releaseUrl)
    }

    @Test fun giteeHtmlUrlIsPreferredWhenPresent() = runBlocking {
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
                              "tag_name": "v1.4",
                              "name": "LightMemo 1.4",
                              "body": "修复",
                              "html_url": "https://gitee.com/clicking/lightmemo/releases/1.4"
                            }
                            """.trimIndent().toResponseBody()
                        )
                        .build()
                }
                .build(),
            latestReleaseUrl = "https://example.test/latest",
        )

        val update = client.checkLatest("1.3")
        assertEquals("https://gitee.com/clicking/lightmemo/releases/1.4", update?.releaseUrl)
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
