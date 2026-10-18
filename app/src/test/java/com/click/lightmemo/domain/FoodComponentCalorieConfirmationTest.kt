package com.click.lightmemo.domain

import org.junit.Assert.*
import org.junit.Test

class FoodComponentCalorieConfirmationTest {
    private val soup = FoodComponent(
        id = "soup", name = "面汤", databaseQuery = "soup", source = ComponentSource.VISIBLE,
        estimatedWeightG = 320.0, weightMinG = 280.0, weightMaxG = 360.0, confidence = 0.9,
        nutritionReference = NutritionReference("soup", "汤", "test", Nutrition(375.0)),
    )

    @Test fun checksCaloriesForTheCurrentPortionAndUsesAStrict800KcalThreshold() {
        assertEquals(1200.0, soup.nutrition.caloriesKcal, 0.001)
        assertTrue(soup.needsCalorieConfirmation)
        val boundary = soup.copy(estimatedWeightG = 100.0,
            nutritionReference = soup.nutritionReference!!.copy(per100g = Nutrition(800.0)))
        assertFalse(boundary.needsCalorieConfirmation)
        assertTrue(boundary.copy(estimatedWeightG = 100.01).needsCalorieConfirmation)
        // The limit applies to one portion, not the reference's per-100g value.
        assertFalse(boundary.copy(estimatedWeightG = 50.0).needsCalorieConfirmation)
        assertFalse(soup.copy(nutritionReference = null).needsCalorieConfirmation)
    }

    @Test fun changingWeightOrReferenceRecalculatesTheWarningWithoutChangingNutrition() {
        assertFalse(soup.copy(estimatedWeightG = 200.0).needsCalorieConfirmation)
        val corrected = soup.copy(nutritionReference = soup.nutritionReference!!.copy(per100g = Nutrition(5.0)))
        assertFalse(corrected.needsCalorieConfirmation)
        assertEquals(16.0, corrected.nutrition.caloriesKcal, 0.001)
        assertEquals(1200.0, soup.nutrition.caloriesKcal, 0.001)
    }
}
