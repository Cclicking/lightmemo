package com.click.lightmemo.domain

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class RecognitionPathSelectorTest {
    private val reference = NutritionReference("china:1", "米饭", "中国食物成分表", Nutrition(116.0, 2.6, 25.9, 0.3))
    private val component = FoodComponent("c", "米饭", "rice cooked", source = ComponentSource.VISIBLE,
        estimatedWeightG = 200.0, weightMinG = 180.0, weightMaxG = 220.0, confidence = 0.95)
    private val dish = RecognizedDish("d", "米饭", DishType.SINGLE_FOOD, 0.95, listOf(component))
    private val visual = MealRecognition(true, "午餐", listOf(dish), 0.95, emptyList())
    private val memory = PersonalFoodMemory("米饭", aliases = setOf("白饭"), typicalGrams = 200.0,
        minGrams = 180.0, maxGrams = 220.0, nutrition = Nutrition(232.0),
        components = listOf(component.copy(nutritionReference = reference)), preferredFoodReference = reference,
        useCount = 10, correctionCount = 1, confidence = 0.95, recentStableCount = 5)

    @Test fun stableKnownFoodReusesCompositionAndStillAsksForConfirmation() {
        val result = RecognitionStrategy(listOf(memory)).fastResult(visual)!!
        assertEquals(232.0, result.nutrition.caloriesKcal, 0.001)
        assertEquals(200.0, result.dishes.single().grams, 0.001)
        assertTrue(result.dishes.single().needsConfirmation)
        assertTrue(result.confirmationQuestions.isNotEmpty())
        assertNotEquals(component.id, result.dishes.single().components.single().id)
    }

    @Test fun oldMemoryDoesNotAcquireStabilityFromDefaults() {
        val raw = kotlinx.serialization.json.JsonObject(
            (Json.parseToJsonElement(Json.encodeToString(memory)) as kotlinx.serialization.json.JsonObject)
                .filterKeys { it != "recentStableCount" },
        ).toString()
        assertEquals(0, Json.decodeFromString<PersonalFoodMemory>(raw).recentStableCount)
        assertNull(RecognitionStrategy(listOf(memory.copy(recentStableCount = 0))).fastResult(visual))
    }

    @Test fun lowConfidenceOrHighCorrectionRateNeedsFullReview() {
        listOf(memory.copy(confidence = 0.8), memory.copy(useCount = 2),
            memory.copy(recentStableCount = 2), memory.copy(correctionCount = 8, recentStableCount = 3)).forEach {
            assertNull(RecognitionStrategy(listOf(it)).fastResult(visual))
        }
        assertNotNull(RecognitionStrategy(listOf(memory.copy(correctionCount = 8))).fastResult(visual))
    }

    @Test fun unconfirmedDatabaseAndAiEstimatesNeverAuthorizeFastPath() {
        listOf(memory.copy(preferredFoodReference = null),
            memory.copy(preferredFoodReference = reference.copy(sourceId = "ai-estimate-123")),
            memory.copy(preferredFoodReference = reference.copy(sourceId = "personal-memory")),
            memory.copy(preferredFoodReference = reference.copy(per100g = Nutrition(Double.NaN))),
            memory.copy(components = emptyList())).forEach {
            assertNull(RecognitionStrategy(listOf(it)).fastResult(visual))
        }
    }

    @Test fun uncertainPhotosAndMultipleDishesFallBack() {
        listOf(visual.copy(overallConfidence = 0.8), visual.copy(overallConfidence = Double.NaN),
            visual.copy(isFoodImage = false), visual.copy(imageQualityIssues = listOf("模糊")),
            visual.copy(confirmationQuestions = listOf("是什么食物？")), visual.copy(dishes = listOf(dish, dish)),
            visual.copy(dishes = listOf(dish.copy(needsConfirmation = true))),
            visual.copy(dishes = listOf(dish.copy(components = listOf(component.copy(confidence = 0.6)))))).forEach {
            assertNull(RecognitionStrategy(listOf(memory)).fastResult(it))
        }
    }

    @Test fun differentFoodCompositionOrPortionFallsBack() {
        listOf(dish.copy(name = "炒饭"), dish.copy(children = listOf(dish)),
            dish.copy(components = listOf(component.copy(estimatedWeightG = 300.0))),
            dish.copy(components = listOf(component.copy(name = "鸡蛋"))),
            dish.copy(components = listOf(component.copy(estimatedWeightG = Double.NaN)))).forEach {
            assertNull(RecognitionStrategy(listOf(memory)).fastResult(visual.copy(dishes = listOf(it))))
        }
    }

    @Test fun aliasesCanMatchButConflictingAliasesCannot() {
        val alias = visual.copy(dishes = listOf(dish.copy(name = "白饭")))
        assertNotNull(RecognitionStrategy(listOf(memory)).fastResult(alias))
        assertNull(RecognitionStrategy(listOf(memory, memory.copy(canonicalName = "另一种饭"))).fastResult(alias))
    }

    @Test fun explicitDescriptionsCustomPromptsAndMultipleServingsRetainFullReview() {
        listOf(RecognitionStrategy(listOf(memory), customPrompts = true),
            RecognitionStrategy(listOf(memory), explicitDescription = true),
            RecognitionStrategy(listOf(memory), multipleServings = true)).forEach { assertNull(it.fastResult(visual)) }
    }

    @Test fun assistedPathChangesOnlyConfirmedReferencesAndKeepsVisualWeight() {
        val unsteady = memory.copy(recentStableCount = 0)
        val large = visual.copy(dishes = listOf(dish.copy(components = listOf(component.copy(estimatedWeightG = 300.0)))))
        val result = RecognitionStrategy(listOf(unsteady)).assist(large)
        assertEquals(300.0, result.dishes.single().grams, 0.001)
        assertEquals(reference, result.dishes.single().components.single().nutritionReference)
        assertEquals(large, RecognitionStrategy(emptyList()).assist(large))
    }

    @Test fun compositionStabilityIgnoresIdsAndOrderButDetectsReferencesAndRatios() {
        val other = component.copy(id = "other", name = "鸡蛋", estimatedWeightG = 100.0)
        val components = listOf(component.copy(nutritionReference = reference), other)
        assertEquals(compositionKey(components), compositionKey(components.reversed().map { it.copy(id = "fresh") }))
        assertNotEquals(compositionKey(components), compositionKey(listOf(components.first(), other.copy(estimatedWeightG = 200.0))))
        assertNotEquals(compositionKey(components), compositionKey(listOf(components.first().copy(nutritionReference = reference.copy(per100g = Nutrition(200.0))), other)))
    }

    @Test fun changedComponentProportionsNeedFullReviewEvenWithSameNameAndTotalWeight() {
        val egg = component.copy(name = "鸡蛋", estimatedWeightG = 50.0)
        val rice = component.copy(estimatedWeightG = 150.0)
        val mixedMemory = memory.copy(components = listOf(rice, egg),
            preferredComponentReferences = mapOf("米饭" to reference, "鸡蛋" to reference), preferredFoodReference = null)
        val changed = dish.copy(components = listOf(rice.copy(estimatedWeightG = 100.0), egg.copy(estimatedWeightG = 100.0)))
        assertNull(RecognitionStrategy(listOf(mixedMemory)).fastResult(visual.copy(dishes = listOf(changed))))
    }
}
