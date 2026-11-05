package com.click.lightmemo.recognition

import com.click.lightmemo.domain.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RecognitionNutritionFallbackTest {
    private fun component(id: String) = FoodComponent(
        id, id, id, source = ComponentSource.VISIBLE,
        estimatedWeightG = 200.0, weightMinG = 150.0, weightMaxG = 250.0, confidence = 0.9,
    )

    private fun meal(vararg components: FoodComponent) = MealRecognition(
        true, "餐食", listOf(RecognizedDish("dish", "菜品", DishType.MIXED_DISH, 0.9, components.toList())),
        0.9, emptyList(),
    )

    @Test fun fillsUnmatchedComponentsAndKeepsDatabaseReferences() = runTest {
        val database = NutritionReference("db", "米饭", "中国食物成分表", Nutrition(116.0))
        val matched = component("rice").copy(nutritionReference = database)
        val unmatched = component("sauce").copy(hasNutritionCandidates = false)
        val estimated = meal(matched, unmatched).withAiNutritionFallback {
            assertEquals(unmatched, it)
            Nutrition(150.0)
        }
        val components = estimated.dishes.single().components
        assertEquals(matched, components.first())
        assertEquals("AI估算（仅供参考）", components.last().nutritionReference!!.dataType)
        assertEquals(532.0, estimated.nutrition.caloriesKcal, 0.001)
        assertEquals(399.0, estimated.nutritionMin.caloriesKcal, 0.001)
        assertEquals(665.0, estimated.nutritionMax.caloriesKcal, 0.001)
        assertEquals(382.0, estimated.copy(dishes = listOf(estimated.dishes.single().copy(
            components = listOf(matched, components.last().copy(estimatedWeightG = 100.0)),
        ))).nutrition.caloriesKcal, 0.001)
    }

    @Test fun includesNestedComponentsAndIsolatesEstimateFailures() = runTest {
        val original = meal(component("failed"))
        val nested = original.copy(dishes = listOf(original.dishes.single().copy(
            children = listOf(meal(component("nested")).dishes.single()),
        )))
        val estimated = nested.withAiNutritionFallback {
            if (it.id == "failed") throw IOException("unavailable")
            Nutrition(100.0)
        }
        assertNull(estimated.dishes.single().components.single().nutritionReference)
        assertEquals(200.0, estimated.nutrition.caloriesKcal, 0.001)
        assertNotNull(estimated.dishes.single().children.single().components.single().nutritionReference)
    }

    @Test fun doesNotRequestEstimatesWhenAllReferencesExist() = runTest {
        val original = meal(component("known").copy(nutritionReference =
            NutritionReference("known", "known", "AI估算（仅供参考）", Nutrition(100.0))))
        assertEquals(original, original.withAiNutritionFallback { error("Already has nutrition") })
    }

    @Test fun propagatesCancellation() = runTest {
        try {
            meal(component("cancelled")).withAiNutritionFallback { throw CancellationException("cancelled") }
            fail("Cancellation must stop recognition")
        } catch (_: CancellationException) {
            // Expected: cancellation must not become an empty reference.
        }
    }
}
