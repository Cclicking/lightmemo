package com.click.lightmemo.mcp

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.click.lightmemo.FoodApp
import com.click.lightmemo.MainActivity
import com.click.lightmemo.network.FoodRecognitionClient
import com.click.lightmemo.recognition.RecognitionRequestType
import com.click.lightmemo.recognition.RecognitionService
import com.click.lightmemo.recognition.RecognitionTaskStatus
import com.click.lightmemo.viewmodel.AddFoodViewModel
import com.click.lightmemo.viewmodel.AddStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.Socket
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** The real MCP service hands text to the unchanged RecognitionService and AddFoodViewModel. */
@RunWith(AndroidJUnit4::class)
class McpTextRecordingFlowTest {
    @Test fun backgroundMcpCallUsesOriginalTextFlowAndProgressNotification(): Unit = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk") || Build.HARDWARE.contains("ranchu"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as FoodApp
        val originalClient = app.recognitionClient
        val originalSettings = app.settingsRepository.settings.first()
        val releaseResponse = CountDownLatch(1)
        val sawOriginalText = AtomicBoolean(false)
        val input = "午饭吃了 200 克米饭"
        val payload = """{"is_food_image":false,"overall_confidence":0.95,"meal_name":"午餐","confirmation_questions":[],"dishes":[{"dish_name":"米饭","dish_type":"single_food","confidence":0.95,"components":[{"name":"熟米饭","database_query":"rice cooked","china_database_query":"米饭","source":"user_provided","estimated_weight_g":200,"weight_min_g":200,"weight_max_g":200,"confidence":0.95}]}]}"""
        val response = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", payload)))).toString()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = Buffer()
            chain.request().body?.writeTo(buffer)
            sawOriginalText.set(buffer.readUtf8().contains(input))
            check(releaseResponse.await(30, TimeUnit.SECONDS))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(response.toResponseBody()).build()
        }.build()
        val clientField = FoodApp::class.java.getDeclaredField("recognitionClient").apply { isAccessible = true }
        val vm = AddFoodViewModel(app)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            clientField.set(app, FoodRecognitionClient(httpClient = http))
            app.settingsRepository.updateActiveBaseUrl("https://mcp-smoke.invalid/v1")
            app.settingsRepository.updateActiveApiKey("smoke-test-only")
            app.settingsRepository.updateActiveModel("smoke-test")
            instrumentation.uiAutomation.executeShellCommand("pm grant com.click.lightmemo android.permission.POST_NOTIFICATIONS").close()
            val before = app.foodLogRepository.readAll().size
            scenario.onActivity { McpRuntime.start(it) }
            val runtime = withTimeout(10_000) { McpRuntime.state.first { it.running } }
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            delay(12_000) // Exercise a request while another app is visible, beyond the transition grace period.
            val uri = URI(runtime.address)
            val json = JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", "tools/call")
                .put("params", JSONObject().put("name", ExistingTextRecordingTool.NAME)
                    .put("arguments", JSONObject().put("text", input).put("meal_type", "lunch"))).toString()
            val body = json.toByteArray()
            val raw = Socket("127.0.0.1", 8765).use { socket ->
                socket.soTimeout = 10_000
                val headers = "POST ${uri.rawPath}?${uri.rawQuery} HTTP/1.1\r\nHost: 127.0.0.1:8765\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                socket.getOutputStream().apply { write(headers.toByteArray()); write(body); flush() }
                socket.getInputStream().bufferedReader().readText()
            }
            assertTrue(raw.startsWith("HTTP/1.1 200 "))
            val result = JSONObject(raw.substringAfter("\r\n\r\n")).getJSONObject("result")
            assertFalse(result.toString(), result.getBoolean("isError"))
            val taskId = JSONObject(result.getJSONArray("content").getJSONObject(0).getString("text")).getString("task_id")
            val running = withTimeout(5_000) { app.recognitionTaskStore.state.first { it?.request?.id == taskId }!! }
            assertEquals(RecognitionRequestType.TEXT, running.request.type)
            assertEquals(input, running.request.text)
            val manager = app.getSystemService(NotificationManager::class.java)
            val notification = withTimeout(5_000) {
                while (true) {
                    manager.activeNotifications.firstOrNull { it.id == 4107 }?.let { return@withTimeout it.notification }
                    delay(100)
                }
                @Suppress("UNREACHABLE_CODE") error("unreachable")
            }
            assertEquals("food_recognition", notification.channelId)
            assertTrue(notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
            assertNotNull(notification.contentIntent)
            assertTrue(notification.actions.any { it.title.toString() == "查看" })
            android.util.Log.i("McpTextFlowTest", "Existing TEXT request and progress notification verified")
            releaseResponse.countDown()
            withTimeout(15_000) { app.recognitionTaskStore.state.first { it?.request?.id == taskId && it.status == RecognitionTaskStatus.COMPLETED } }
            val state = withTimeout(10_000) { vm.uiState.first { it.step is AddStep.Review } }
            val review = state.step as AddStep.Review
            assertEquals(200.0, review.result.dishes.single().grams, 0.0)
            assertNotNull(review.result.dishes.single().allComponents.single().nutritionReference)
            assertTrue(sawOriginalText.get())
            assertEquals(before, app.foodLogRepository.readAll().size)
            android.util.Log.i("McpTextFlowTest", "Existing result synchronization and no automatic save verified")
        } finally {
            releaseResponse.countDown()
            instrumentation.runOnMainSync {
                app.stopService(android.content.Intent(app, RecognitionService::class.java))
                McpRuntime.stop(app)
                vm.viewModelScope.cancel()
            }
            app.recognitionTaskStore.clear()
            clientField.set(app, originalClient)
            app.settingsRepository.updateActiveBaseUrl(originalSettings.baseUrl)
            app.settingsRepository.updateActiveApiKey(originalSettings.apiKey)
            app.settingsRepository.updateActiveModel(originalSettings.model)
            app.getSystemService(NotificationManager::class.java).cancel(4107)
            // The normal result Intent can recreate the initial Activity while returning from Home.
            // An obsolete ActivityScenario must not mask assertions from the actual flow.
            runCatching { scenario.close() }
        }
    }
}
