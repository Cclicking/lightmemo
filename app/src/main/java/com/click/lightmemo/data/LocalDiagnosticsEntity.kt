package com.click.lightmemo.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import kotlinx.serialization.Serializable

/** A fixed numeric allowlist. Never add prompts, keys, images or food content here. */
@Serializable
@Entity(tableName = "local_diagnostics")
data class LocalDiagnosticsEntity(
    @PrimaryKey val dateEpochDay: Long,
    val recognitionCount: Long = 0,
    val successCount: Long = 0,
    val failureCount: Long = 0,
    val totalDurationMs: Long = 0,
    val fullPathCount: Long = 0,
    val assistedPathCount: Long = 0,
    val fastPathCount: Long = 0,
    val llmCallCount: Long = 0,
    val usdaOfflineHits: Long = 0,
    val usdaOnlineHits: Long = 0,
    val chinaDbHits: Long = 0,
    val aiNutritionFallbackCount: Long = 0,
    val missingNutritionCount: Long = 0,
    val nameCorrectionCount: Long = 0,
    val weightCorrectionCount: Long = 0,
    val componentCorrectionCount: Long = 0,
    val foodReferenceCorrectionCount: Long = 0,
    val directAcceptanceCount: Long = 0,
    val recommendationOpenCount: Long = 0,
    val recommendationDrawCount: Long = 0,
    val recommendationLikeCount: Long = 0,
    val recommendationSkipCount: Long = 0,
    val recommendationDislikeCount: Long = 0,
    val recommendationAcceptCount: Long = 0,
    @ColumnInfo(defaultValue = "0") val recognitionLlmCallCount: Long = 0,
    @ColumnInfo(defaultValue = "0") val reviewSaveCount: Long = 0,
    @ColumnInfo(defaultValue = "0") val modifiedReviewCount: Long = 0,
    @ColumnInfo(defaultValue = "0") val cacheReuseCount: Long = 0,
) {
    fun validateDelta() {
        require(listOf(recognitionCount, successCount, failureCount, totalDurationMs, fullPathCount, assistedPathCount, fastPathCount, llmCallCount, usdaOfflineHits, usdaOnlineHits, chinaDbHits, aiNutritionFallbackCount, missingNutritionCount, nameCorrectionCount, weightCorrectionCount, componentCorrectionCount, foodReferenceCorrectionCount, directAcceptanceCount, recommendationOpenCount, recommendationDrawCount, recommendationLikeCount, recommendationSkipCount, recommendationDislikeCount, recommendationAcceptCount).all { it >= 0 })
        require(dateEpochDay in java.time.LocalDate.MIN.toEpochDay()..java.time.LocalDate.MAX.toEpochDay())
        require(listOf(recognitionLlmCallCount, reviewSaveCount, modifiedReviewCount, cacheReuseCount).all { it >= 0 })
    }

    operator fun plus(other: LocalDiagnosticsEntity): LocalDiagnosticsEntity {
        require(dateEpochDay == other.dateEpochDay)
        return copy(
            recognitionCount = Math.addExact(recognitionCount, other.recognitionCount),
            successCount = Math.addExact(successCount, other.successCount),
            failureCount = Math.addExact(failureCount, other.failureCount),
            totalDurationMs = Math.addExact(totalDurationMs, other.totalDurationMs),
            fullPathCount = Math.addExact(fullPathCount, other.fullPathCount),
            assistedPathCount = Math.addExact(assistedPathCount, other.assistedPathCount),
            fastPathCount = Math.addExact(fastPathCount, other.fastPathCount),
            llmCallCount = Math.addExact(llmCallCount, other.llmCallCount),
            usdaOfflineHits = Math.addExact(usdaOfflineHits, other.usdaOfflineHits),
            usdaOnlineHits = Math.addExact(usdaOnlineHits, other.usdaOnlineHits),
            chinaDbHits = Math.addExact(chinaDbHits, other.chinaDbHits),
            aiNutritionFallbackCount = Math.addExact(aiNutritionFallbackCount, other.aiNutritionFallbackCount),
            missingNutritionCount = Math.addExact(missingNutritionCount, other.missingNutritionCount),
            nameCorrectionCount = Math.addExact(nameCorrectionCount, other.nameCorrectionCount),
            weightCorrectionCount = Math.addExact(weightCorrectionCount, other.weightCorrectionCount),
            componentCorrectionCount = Math.addExact(componentCorrectionCount, other.componentCorrectionCount),
            foodReferenceCorrectionCount = Math.addExact(foodReferenceCorrectionCount, other.foodReferenceCorrectionCount),
            directAcceptanceCount = Math.addExact(directAcceptanceCount, other.directAcceptanceCount),
            recommendationOpenCount = Math.addExact(recommendationOpenCount, other.recommendationOpenCount),
            recommendationDrawCount = Math.addExact(recommendationDrawCount, other.recommendationDrawCount),
            recommendationLikeCount = Math.addExact(recommendationLikeCount, other.recommendationLikeCount),
            recommendationSkipCount = Math.addExact(recommendationSkipCount, other.recommendationSkipCount),
            recommendationDislikeCount = Math.addExact(recommendationDislikeCount, other.recommendationDislikeCount),
            recommendationAcceptCount = Math.addExact(recommendationAcceptCount, other.recommendationAcceptCount),
            recognitionLlmCallCount = Math.addExact(recognitionLlmCallCount, other.recognitionLlmCallCount),
            reviewSaveCount = Math.addExact(reviewSaveCount, other.reviewSaveCount),
            modifiedReviewCount = Math.addExact(modifiedReviewCount, other.modifiedReviewCount),
            cacheReuseCount = Math.addExact(cacheReuseCount, other.cacheReuseCount),
        )
    }
}
