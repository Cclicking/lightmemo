package com.click.lightmemo.recognition

import com.click.lightmemo.data.LocalDiagnosticsEntity
import com.click.lightmemo.domain.RecognitionPath
import java.time.LocalDate
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Per-coroutine state: concurrent/replaced tasks cannot share counters. No food or request data. */
class RecognitionDiagnostics : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RecognitionDiagnostics>
    private val startedNanos = System.nanoTime()
    var modelCalls: Int = 0
        private set
    var outcome: Boolean? = null
    var path: RecognitionPath = RecognitionPath.FULL
    var cacheReused: Boolean = false
    fun modelCalled() { modelCalls++ }
    fun elapsedMillis(): Long = ((System.nanoTime() - startedNanos) / 1_000_000).coerceAtLeast(0)
    fun completedDelta(day: Long = LocalDate.now().toEpochDay()): LocalDiagnosticsEntity? {
        val success = outcome ?: return null
        return LocalDiagnosticsEntity(day, recognitionCount = 1, successCount = if (success) 1 else 0,
            failureCount = if (success) 0 else 1, totalDurationMs = elapsedMillis(),
            fullPathCount = if (!cacheReused && path == RecognitionPath.FULL) 1 else 0,
            assistedPathCount = if (!cacheReused && path == RecognitionPath.ASSISTED) 1 else 0,
            fastPathCount = if (!cacheReused && path == RecognitionPath.FAST) 1 else 0,
            recognitionLlmCallCount = modelCalls.toLong(), cacheReuseCount = if (cacheReused) 1 else 0)
    }
}
