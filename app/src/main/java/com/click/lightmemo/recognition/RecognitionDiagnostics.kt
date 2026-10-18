package com.click.lightmemo.recognition

import com.click.lightmemo.data.LocalDiagnosticsEntity
import com.click.lightmemo.domain.RecognitionPath
import java.time.LocalDate
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.currentCoroutineContext

@kotlinx.serialization.Serializable
enum class RecognitionPhase {
    IMAGE_PREPARE, CACHE_READ, MEMORY_READ, MODEL_RECOGNITION, MODEL_REVIEW,
    MODEL_NORMALIZE, MODEL_PORTION, MODEL_TEXT, NUTRITION_MATCH, NUTRITION_PREFETCH, IMAGE_SAVE, CACHE_WRITE,
}

internal suspend fun <T> measureRecognitionPhase(phase: RecognitionPhase, block: suspend () -> T): T {
    val metrics = currentCoroutineContext()[RecognitionDiagnostics]
    return if (metrics == null) block() else metrics.measure(phase, block)
}

/** Per-coroutine state: concurrent/replaced tasks cannot share counters. No food or request data. */
class RecognitionDiagnostics(
    private val log: ((RecognitionLogEvent) -> Unit)? = null,
    private val logEnabled: () -> Boolean = { true },
    private val nanoTime: () -> Long = System::nanoTime,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RecognitionDiagnostics>
    private val startedNanos = nanoTime()
    private val phaseNanos = java.util.concurrent.ConcurrentHashMap<RecognitionPhase, Long>()
    private val calls = AtomicInteger()
    private val networkCalls = AtomicInteger()
    private val logTask = java.util.UUID.randomUUID().toString()
    val modelCalls: Int get() = calls.get()
    val loggingEnabled: Boolean get() = log != null && logEnabled()
    var outcome: Boolean? = null
    var path: RecognitionPath = RecognitionPath.FULL
    var cacheReused: Boolean = false
    fun modelCalled(): Int = calls.incrementAndGet()
    fun nextNetworkRequest(): Int = networkCalls.incrementAndGet()
    fun logEvent(kind: RecognitionLogKind, phase: RecognitionPhase? = null, durationMs: Long? = null,
        result: RecognitionLogResult? = null, error: RecognitionLogError? = null,
        request: Int? = null, httpStatus: Int? = null, bytes: Long? = null,
        model: RecognitionLogModel? = null, provider: RecognitionLogProvider? = null,
        promptTokens: Long? = null, completionTokens: Long? = null, reasoningTokens: Long? = null,
        reasoningPresent: Boolean? = null, thinkingDisabled: Boolean? = null, candidateLimit: Int? = null) {
        if (!loggingEnabled) return
        runCatching { log?.invoke(RecognitionLogEvent(task = logTask, kind = kind, elapsedMs = elapsedMillis(),
            phase = phase, durationMs = durationMs, result = result, error = error, request = request,
            httpStatus = httpStatus, bytes = bytes, modelCalls = modelCalls, model = model, provider = provider,
            promptTokens = promptTokens, completionTokens = completionTokens,
            reasoningTokens = reasoningTokens, reasoningPresent = reasoningPresent,
            thinkingDisabled = thinkingDisabled, candidateLimit = candidateLimit)) }
    }
    fun elapsedMillis(): Long = ((nanoTime() - startedNanos) / 1_000_000).coerceAtLeast(0)
    fun phaseDurationsMillis(): Map<String, Long> = phaseNanos.mapKeys { it.key.name }.mapValues { it.value / 1_000_000 }
    suspend fun <T> measure(phase: RecognitionPhase, block: suspend () -> T): T {
        val start = nanoTime()
        logEvent(RecognitionLogKind.PHASE_START, phase)
        var failure: Throwable? = null
        return try { block() } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            val duration = (nanoTime() - start).coerceAtLeast(0)
            phaseNanos.merge(phase, duration, Long::plus)
            logEvent(RecognitionLogKind.PHASE_END, phase, duration / 1_000_000,
                result = when (failure) {
                    null -> RecognitionLogResult.SUCCESS
                    is kotlinx.coroutines.CancellationException -> RecognitionLogResult.CANCELLED
                    else -> RecognitionLogResult.FAILED
                }, error = failure?.logCategory())
        }
    }
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
