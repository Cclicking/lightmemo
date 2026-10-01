package com.click.lightmemo.domain

import com.click.lightmemo.data.LocalDiagnosticsEntity
import org.junit.Assert.*
import org.junit.Test

class DiagnosticEventsTest {
    private val reference = NutritionReference("1", "Rice", "SR Legacy (offline 2018-04)", Nutrition(116.0))
    private val component = FoodComponent("c", "米饭", "rice", source = ComponentSource.VISIBLE,
        estimatedWeightG = 200.0, weightMinG = 180.0, weightMaxG = 220.0, confidence = 0.95, nutritionReference = reference)
    private val dish = RecognizedDish("d", "米饭", DishType.SINGLE_FOOD, 0.95, listOf(component))
    private val meal = MealRecognition(true, "午餐", listOf(dish), 0.95, emptyList())

    @Test fun directAcceptanceIsOneReviewWithoutAnyCorrection() {
        val event = DiagnosticEvents.reviewed(1, meal, meal)
        assertEquals(1L, event.reviewSaveCount)
        assertEquals(1L, event.directAcceptanceCount)
        assertEquals(0L, event.modifiedReviewCount)
    }

    @Test fun weightScalingDoesNotCountAsCompositionCorrection() {
        val saved = meal.copy(dishes = listOf(dish.copy(components = listOf(component.copy(
            estimatedWeightG = 100.0, weightMinG = 90.0, weightMaxG = 110.0)))))
        val event = DiagnosticEvents.reviewed(1, meal, saved)
        assertEquals(1L, event.weightCorrectionCount)
        assertEquals(0L, event.componentCorrectionCount)
        assertEquals(0L, event.foodReferenceCorrectionCount)
        assertEquals(1L, event.modifiedReviewCount)
    }

    @Test fun simultaneousCorrectionsAreOneModifiedReviewWithSeparateCategories() {
        val saved = meal.copy(dishes = listOf(dish.copy(name = "糙米饭", components = listOf(component.copy(
            name = "糙米", estimatedWeightG = 100.0, nutritionReference = reference.copy(sourceId = "2"))))))
        val event = DiagnosticEvents.reviewed(1, meal, saved)
        assertEquals(1L, event.nameCorrectionCount)
        assertEquals(1L, event.weightCorrectionCount)
        assertEquals(1L, event.componentCorrectionCount)
        assertEquals(1L, event.foodReferenceCorrectionCount)
        assertEquals(1L, event.modifiedReviewCount)
        assertEquals(1L, event.reviewSaveCount)
        assertEquals(0L, event.directAcceptanceCount)
    }

    @Test fun deletedOrAddedDishesAreCompositionChanges() {
        assertEquals(1L, DiagnosticEvents.reviewed(1, meal, meal.copy(dishes = emptyList())).componentCorrectionCount)
        assertEquals(1L, DiagnosticEvents.reviewed(1, meal, meal.copy(dishes = listOf(dish, dish.copy(id = "new")))).componentCorrectionCount)
    }

    @Test fun severalChangedDishesDoNotMultiplyReviewDenominator() {
        val original = meal.copy(dishes = listOf(dish, dish.copy(id = "second")))
        val saved = original.copy(dishes = original.dishes.map { it.copy(name = "糙米饭") })
        val event = DiagnosticEvents.reviewed(1, original, saved)
        assertEquals(1L, event.nameCorrectionCount)
        assertEquals(1L, event.reviewSaveCount)
    }

    @Test fun componentOrderAndFreshIdsDoNotCountAsCompositionChanges() {
        val saved = meal.copy(dishes = listOf(dish.copy(components = listOf(component.copy(id = "fresh")))))
        val event = DiagnosticEvents.reviewed(1, meal, saved)
        assertEquals(0L, event.modifiedReviewCount)
        assertEquals(1L, event.directAcceptanceCount)
    }

    @Test fun manuallyEnteredRecordsWithoutComponentsStillDetectWeightAndNutritionEdits() {
        val original = FoodLog(name = "米饭", mealType = MealType.LUNCH, grams = 200.0, nutrition = Nutrition(232.0))
        val event = DiagnosticEvents.edited(1, original, original.copy(grams = 100.0, nutrition = Nutrition(116.0)))
        assertEquals(1L, event.weightCorrectionCount)
        assertEquals(1L, event.modifiedReviewCount)
        assertEquals(0L, event.directAcceptanceCount)
    }

    @Test fun mealTimeAndNotesAreNotFoodCorrections() {
        val original = FoodLog(name = "米饭", mealType = MealType.LUNCH, grams = 200.0, nutrition = Nutrition(232.0))
        val event = DiagnosticEvents.edited(1, original, original.copy(note = "午饭", mealType = MealType.DINNER))
        assertEquals(0L, event.modifiedReviewCount)
        assertEquals(0L, event.directAcceptanceCount)
    }

    @Test fun sourcesAreClassifiedFromReferenceTypeIncludingMissing() {
        assertEquals(NutritionMatchSource.USDA_OFFLINE, DiagnosticEvents.source(reference))
        assertEquals(NutritionMatchSource.USDA_ONLINE, DiagnosticEvents.source(reference.copy(dataType = "Foundation")))
        assertEquals(NutritionMatchSource.CHINA, DiagnosticEvents.source(reference.copy(dataType = "中国食物成分表")))
        assertEquals(NutritionMatchSource.MISSING, DiagnosticEvents.source(null))
        assertEquals(1L, DiagnosticEvents.match(1, NutritionMatchSource.MISSING).missingNutritionCount)
    }

    @Test fun emptyWindowHasUndefinedRatesInsteadOfNaN() {
        val summary = DiagnosticsSummary.from(emptyList())
        assertNull(summary.successRate)
        assertNull(summary.averageDurationMs)
        assertNull(summary.averageRecognitionCalls)
        assertNull(summary.modifiedRate)
    }

    @Test fun thirtyDaySummaryUsesDistinctCorrectDenominators() {
        val summary = DiagnosticsSummary.from(listOf(
            LocalDiagnosticsEntity(1, recognitionCount = 2, successCount = 1, failureCount = 1, totalDurationMs = 4000,
                recognitionLlmCallCount = 3, llmCallCount = 9, reviewSaveCount = 1, modifiedReviewCount = 1, weightCorrectionCount = 1),
            LocalDiagnosticsEntity(2, recognitionCount = 2, successCount = 2, totalDurationMs = 8000,
                recognitionLlmCallCount = 4, llmCallCount = 4, reviewSaveCount = 3, modifiedReviewCount = 1, weightCorrectionCount = 1)))
        assertEquals(75.0, summary.successRate!!, 0.001)
        assertEquals(3000.0, summary.averageDurationMs!!, 0.001)
        assertEquals(1.75, summary.averageRecognitionCalls!!, 0.001)
        assertEquals(50.0, summary.modifiedRate!!, 0.001)
        assertEquals(50.0, summary.reviewRate(summary.totals.weightCorrectionCount)!!, 0.001)
        assertEquals(13L, summary.totals.llmCallCount)
    }
}
