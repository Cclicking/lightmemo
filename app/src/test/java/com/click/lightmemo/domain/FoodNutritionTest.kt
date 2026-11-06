package com.click.lightmemo.domain

import org.junit.Assert.*
import org.junit.Test

class FoodNutritionTest {
    private val component = FoodComponent(
        "rice", "米饭", "rice", source = ComponentSource.USER_PROVIDED,
        estimatedWeightG = 100.0, weightMinG = 100.0, weightMaxG = 100.0, confidence = 1.0,
        nutritionReference = NutritionReference("rice", "米饭", "测试", Nutrition(116.0, 3.0, 26.0, 1.0)),
    )
    private val food = FoodLog(name = "米饭", mealType = MealType.LUNCH, grams = 100.0,
        nutrition = component.nutrition, components = listOf(component))

    @Test fun editingCompositionKeepsManualTotalsUntilExplicitlyRestored() {
        val manual = food.copy(nutrition = Nutrition(150.0, 5.0, 30.0, 2.0))
        val next = manual.withEditedComponents(listOf(component.copy(estimatedWeightG = 200.0)))
        assertTrue(next.hasManualNutrition)
        assertEquals(manual.nutrition, next.nutrition)
        assertEquals(200.0, next.grams, 0.0)
        val restored = next.withComponentNutrition()
        assertEquals(next.components, restored.components)
        assertEquals(Nutrition(232.0, 6.0, 52.0, 2.0), restored.nutrition)
        assertFalse(restored.hasManualNutrition)
    }

    @Test fun calculatedTotalsContinueFollowingComponents() {
        val next = food.withEditedComponents(listOf(component.copy(estimatedWeightG = 200.0)))
        assertEquals(food.nutrition * 2.0, next.nutrition)
        assertFalse(next.hasManualNutrition)
    }

    @Test fun completingUnmatchedComponentsAndAddingToManualEntriesKeepExplicitTotals() {
        val unmatched = food.copy(components = listOf(component.copy(nutritionReference = null)), nutrition = Nutrition(180.0))
        assertEquals(Nutrition(180.0), unmatched.withEditedComponents(listOf(component)).nutrition)
        val manual = food.copy(components = emptyList(), nutrition = Nutrition(200.0))
        assertEquals(Nutrition(200.0), manual.withEditedComponents(listOf(component)).nutrition)
    }

    @Test fun roundingNoiseDoesNotTurnCalculatedNutritionIntoAnOverride() {
        assertFalse(food.copy(nutrition = food.nutrition.copy(caloriesKcal = 116.00000001)).hasManualNutrition)
    }

    @Test fun removingAllComponentsKeepsValidPortionAndTotals() {
        val next = food.withEditedComponents(emptyList())
        assertTrue(next.components.isEmpty())
        assertEquals(food.grams, next.grams, 0.0)
        assertEquals(food.nutrition, next.nutrition)
    }

    @Test fun targetRatioPreservesOverTargetValuesAndRejectsInvalidTargets() {
        assertEquals(0.0, nutritionTargetRatio(0.0, 100f), 0.0)
        assertEquals(1.0, nutritionTargetRatio(100.0, 100f), 0.0)
        assertEquals(1.5, nutritionTargetRatio(150.0, 100f), 0.0)
        for (target in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(0.0, nutritionTargetRatio(150.0, target), 0.0)
        }
        assertEquals(0.0, nutritionTargetRatio(Double.NaN, 100f), 0.0)
    }
}
