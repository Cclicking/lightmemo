package com.click.lightmemo.recognition

import com.click.lightmemo.domain.RecognitionPath
import com.click.lightmemo.network.FoodRecognitionClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class RecognitionDiagnosticsTest {
    @Test fun waitingOrCancelledTasksDoNotCreateFailures() {
        val metrics = RecognitionDiagnostics()
        metrics.modelCalled()
        assertNull(metrics.completedDelta(1))
    }

    @Test fun cachedResultIsSuccessfulWithZeroCallsAndItsOwnPathCount() {
        val metrics = RecognitionDiagnostics().apply { outcome = true; cacheReused = true; path = RecognitionPath.FAST }
        val event = metrics.completedDelta(1)!!
        assertEquals(1L, event.cacheReuseCount)
        assertEquals(0L, event.fastPathCount)
        assertEquals(0L, event.recognitionLlmCallCount)
        assertEquals(1L, event.successCount)
    }

    @Test fun failureAndSuccessCountersAndPathsAreMutuallyExclusive() {
        RecognitionPath.entries.forEach { path ->
            val metrics = RecognitionDiagnostics().apply { outcome = false; this.path = path; modelCalled(); modelCalled() }
            val event = metrics.completedDelta(1)!!
            assertEquals(1L, event.failureCount)
            assertEquals(0L, event.successCount)
            assertEquals(1L, event.fullPathCount + event.assistedPathCount + event.fastPathCount)
            assertEquals(2L, event.recognitionLlmCallCount)
        }
    }

    @Test fun failedHttpCallIsCountedAndConcurrentContextsAreIndependent() = runBlocking {
        val calls = mutableListOf<Boolean>()
        val http = OkHttpClient.Builder().addInterceptor { throw java.io.IOException("offline") }.build()
        val client = FoodRecognitionClient(httpClient = http, onCall = { calls += it })
        val first = RecognitionDiagnostics()
        val second = RecognitionDiagnostics()
        withContext(first) { runCatching { client.normalizeFoodQuery("https://example.test/v1", "test", "test", "米饭") } }
        withContext(second) { runCatching { client.estimateNutrition("https://example.test/v1", "test", "test", "米饭", 200.0) } }
        assertEquals(1, first.modelCalls)
        assertEquals(1, second.modelCalls)
        assertEquals(listOf(false, true), calls)
    }

    @Test fun invalidConfigurationDoesNotCountAsModelCall() = runBlocking {
        var calls = 0
        val client = FoodRecognitionClient(onCall = { calls++ })
        runCatching { client.normalizeFoodQuery("", "", "", "米饭") }
        assertEquals(0, calls)
    }
}
