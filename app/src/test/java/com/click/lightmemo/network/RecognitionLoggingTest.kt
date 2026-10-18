package com.click.lightmemo.network

import com.click.lightmemo.recognition.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import org.junit.Assert.*
import org.junit.Test

class RecognitionLoggingTest {
    @Test fun httpMilestonesExposeTimingsAndSafeMetadataWithoutPayloads() = runBlocking {
        val events = mutableListOf<RecognitionLogEvent>()
        val metrics = RecognitionDiagnostics(log = { events += it })
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(null, """{"choices":[{"message":{"content":"PRIVATE_BODY","reasoning_content":"PRIVATE_REASONING"}}],"usage":{"prompt_tokens":100,"completion_tokens":20,"completion_tokens_details":{"reasoning_tokens":12}}}""")).build()
        }.build()
        withContext(metrics) {
            FoodRecognitionClient(httpClient = http).testConnection("https://api.deepseek.com/v1", "SECRET_API_KEY", "deepseek-flash")
        }
        assertEquals(listOf(RecognitionLogKind.PHASE_START, RecognitionLogKind.HTTP_START,
            RecognitionLogKind.HTTP_HEADERS, RecognitionLogKind.HTTP_BODY, RecognitionLogKind.PHASE_END), events.map { it.kind })
        val start = events.first { it.kind == RecognitionLogKind.HTTP_START }
        val body = events.first { it.kind == RecognitionLogKind.HTTP_BODY }
        assertEquals(RecognitionLogModel.DEEPSEEK_FLASH, start.model)
        assertEquals(RecognitionLogProvider.DEEPSEEK_OFFICIAL, start.provider)
        assertEquals(true, start.thinkingDisabled)
        assertEquals(200, body.httpStatus)
        assertEquals(100L, body.promptTokens)
        assertEquals(20L, body.completionTokens)
        assertEquals(12L, body.reasoningTokens)
        assertEquals(true, body.reasoningPresent)
        assertTrue(body.durationMs!! >= events.first { it.kind == RecognitionLogKind.HTTP_HEADERS }.durationMs!!)
        val exported = Json.encodeToString(events)
        listOf("SECRET_API_KEY", "PRIVATE_BODY", "PRIVATE_REASONING", "api.deepseek.com", "Authorization").forEach {
            assertFalse("Leaked $it", exported.contains(it))
        }
    }

    @Test fun timeoutAndCancellationKeepTheirCategoriesAndLoggerFailureCannotBreakWork() = runBlocking {
        val events = mutableListOf<RecognitionLogEvent>()
        val metrics = RecognitionDiagnostics(log = { events += it })
        val http = OkHttpClient.Builder().addInterceptor { throw java.net.SocketTimeoutException("SECRET_ERROR_TEXT") }.build()
        withContext(metrics) { runCatching {
            FoodRecognitionClient(httpClient = http).testConnection("https://example.test/v1", "SECRET", "custom-private-model")
        } }
        assertEquals(RecognitionLogError.TIMEOUT, events.first { it.kind == RecognitionLogKind.HTTP_ERROR }.error)
        assertEquals(RecognitionLogResult.FAILED, events.last().result)
        assertFalse(Json.encodeToString(events).contains("SECRET_ERROR_TEXT"))
        assertFalse(Json.encodeToString(events).contains("custom-private-model"))
        runCatching { metrics.measure(RecognitionPhase.MODEL_REVIEW) { throw kotlinx.coroutines.CancellationException() } }
        assertEquals(RecognitionLogResult.CANCELLED, events.last().result)
        assertEquals(RecognitionLogError.CANCELLED, events.last().error)
        assertEquals(42, RecognitionDiagnostics(log = { error("disk failure") }).measure(RecognitionPhase.CACHE_READ) { 42 })
    }
}
