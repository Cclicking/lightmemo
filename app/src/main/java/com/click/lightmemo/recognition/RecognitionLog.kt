package com.click.lightmemo.recognition

import kotlinx.serialization.Serializable

/** Whitelisted fields only: never store request/response text, URLs, credentials or food data. */
@Serializable
enum class RecognitionLogKind { TASK_START, TASK_END, PHASE_START, PHASE_END, HTTP_START, HTTP_HEADERS, HTTP_BODY, HTTP_ERROR }

@Serializable
enum class RecognitionLogResult { SUCCESS, FAILED, CANCELLED, WAITING }

@Serializable
enum class RecognitionLogError { TIMEOUT, NETWORK, CANCELLED, OTHER }

@Serializable
enum class RecognitionLogModel { DEEPSEEK_FLASH, DEEPSEEK_CHAT, DEEPSEEK_REASONER, OTHER }

@Serializable
enum class RecognitionLogProvider { DEEPSEEK_OFFICIAL, USDA, OTHER }

@Serializable
data class RecognitionLogEvent(
    val task: String,
    val kind: RecognitionLogKind,
    val timestampMs: Long = System.currentTimeMillis(),
    val elapsedMs: Long,
    val phase: RecognitionPhase? = null,
    val durationMs: Long? = null,
    val result: RecognitionLogResult? = null,
    val error: RecognitionLogError? = null,
    val request: Int? = null,
    val httpStatus: Int? = null,
    val bytes: Long? = null,
    val modelCalls: Int? = null,
    val model: RecognitionLogModel? = null,
    val provider: RecognitionLogProvider? = null,
    val promptTokens: Long? = null,
    val completionTokens: Long? = null,
    val reasoningTokens: Long? = null,
    val reasoningPresent: Boolean? = null,
    val thinkingDisabled: Boolean? = null,
    val candidateLimit: Int? = null,
)

internal fun Throwable.logCategory(): RecognitionLogError = when (this) {
    is kotlinx.coroutines.CancellationException -> RecognitionLogError.CANCELLED
    is java.io.InterruptedIOException -> RecognitionLogError.TIMEOUT
    is java.io.IOException -> RecognitionLogError.NETWORK
    else -> RecognitionLogError.OTHER
}
