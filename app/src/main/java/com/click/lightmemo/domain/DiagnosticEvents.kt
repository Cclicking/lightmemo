package com.click.lightmemo.domain

import com.click.lightmemo.data.LocalDiagnosticsEntity
import kotlin.math.abs

enum class NutritionMatchSource { USDA_OFFLINE, USDA_ONLINE, CHINA, MISSING }

object DiagnosticEvents {
    fun source(reference: NutritionReference?): NutritionMatchSource = when {
        reference == null -> NutritionMatchSource.MISSING
        reference.dataType.contains("中国") || reference.dataType.contains("china", true) -> NutritionMatchSource.CHINA
        reference.dataType.contains("offline", true) -> NutritionMatchSource.USDA_OFFLINE
        else -> NutritionMatchSource.USDA_ONLINE
    }

    fun match(day: Long, source: NutritionMatchSource) = LocalDiagnosticsEntity(day,
        usdaOfflineHits = if (source == NutritionMatchSource.USDA_OFFLINE) 1 else 0,
        usdaOnlineHits = if (source == NutritionMatchSource.USDA_ONLINE) 1 else 0,
        chinaDbHits = if (source == NutritionMatchSource.CHINA) 1 else 0,
        missingNutritionCount = if (source == NutritionMatchSource.MISSING) 1 else 0)

    /** One event per saved review, even when several dishes or correction categories change. */
    fun reviewed(day: Long, original: MealRecognition, saved: MealRecognition, initialAcceptance: Boolean = true): LocalDiagnosticsEntity {
        val pairs = saved.dishes.mapNotNull { dish -> original.dishes.find { it.id == dish.id }?.let { it to dish } }
        val name = pairs.any { (a, b) -> normalizeFoodName(a.name) != normalizeFoodName(b.name) }
        val weight = pairs.any { (a, b) -> abs(a.grams - b.grams) > 0.1 }
        val composition = original.dishes.map { it.id }.toSet() != saved.dishes.map { it.id }.toSet() ||
            pairs.any { (a, b) -> structure(a.allComponents) != structure(b.allComponents) }
        val references = pairs.any { (a, b) ->
            val previous = a.allComponents.associateBy { it.id }
            b.allComponents.any { component ->
                val before = previous[component.id] ?: a.allComponents.singleOrNull {
                    normalizeFoodName(it.name) == normalizeFoodName(component.name)
                }
                before != null && before.nutritionReference != component.nutritionReference
            }
        }
        val modified = name || weight || composition || references || original.nutrition != saved.nutrition
        return LocalDiagnosticsEntity(day, reviewSaveCount = 1, modifiedReviewCount = if (modified) 1 else 0,
            nameCorrectionCount = if (name) 1 else 0, weightCorrectionCount = if (weight) 1 else 0,
            componentCorrectionCount = if (composition) 1 else 0, foodReferenceCorrectionCount = if (references) 1 else 0,
            directAcceptanceCount = if (initialAcceptance && !modified) 1 else 0)
    }

    private fun structure(components: List<FoodComponent>): List<Pair<String, Int>> {
        val total = components.sumOf { it.estimatedWeightG }
        return components.map {
            normalizeFoodName(it.name) to if (total > 0) kotlin.math.round(it.estimatedWeightG / total * 1000).toInt() else 0
        }.sortedWith(compareBy({ it.first }, { it.second }))
    }

    fun edited(day: Long, original: FoodLog, saved: FoodLog): LocalDiagnosticsEntity {
        fun meal(log: FoodLog) = MealRecognition(true, "", listOf(RecognizedDish("entry", log.name,
            DishType.OTHER, 1.0, log.components)), 1.0, emptyList())
        val event = reviewed(day, meal(original), meal(saved), initialAcceptance = false)
        val weight = abs(original.grams - saved.grams) > 0.1
        return event.copy(weightCorrectionCount = if (weight) 1 else 0,
            modifiedReviewCount = if (event.modifiedReviewCount > 0 || weight || original.nutrition != saved.nutrition) 1 else 0)
    }


}

data class DiagnosticsSummary(val totals: LocalDiagnosticsEntity) {
    val successRate: Double? get() = rate(totals.successCount, totals.recognitionCount)
    val modifiedRate: Double? get() = rate(totals.modifiedReviewCount, totals.reviewSaveCount)
    val averageDurationMs: Double? get() = average(totals.totalDurationMs, totals.recognitionCount)
    val averageRecognitionCalls: Double? get() = average(totals.recognitionLlmCallCount, totals.recognitionCount)
    fun reviewRate(count: Long): Double? = rate(count, totals.reviewSaveCount)
    companion object {
        fun from(rows: List<LocalDiagnosticsEntity>): DiagnosticsSummary = DiagnosticsSummary(
            rows.fold(LocalDiagnosticsEntity(0)) { sum, row -> sum + row.copy(dateEpochDay = 0) })
        private fun average(value: Long, count: Long): Double? = if (count == 0L) null else value.toDouble() / count
        private fun rate(value: Long, count: Long): Double? = average(value, count)?.times(100)
    }
}
