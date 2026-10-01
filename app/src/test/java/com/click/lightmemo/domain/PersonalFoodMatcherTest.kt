package com.click.lightmemo.domain

import org.junit.Assert.*
import org.junit.Test

class PersonalFoodMatcherTest {
    private fun memory(name: String = "番茄炒蛋") = PersonalFoodMemory(
        canonicalName = name, aliases = setOf("西红柿炒蛋"), typicalGrams = 230.0,
        minGrams = 210.0, maxGrams = 250.0, nutrition = Nutrition(230.0),
        correctionCount = 1, useCount = 4, confidence = 0.6,
    )

    @Test fun exactAndUniqueAliasAllowSuggestionsAfterCorrection() {
        assertEquals(MemoryMatchKind.EXACT, PersonalFoodMatcher.match(" 番茄 炒蛋 ", listOf(memory()))!!.kind)
        assertEquals(MemoryMatchKind.ALIAS, PersonalFoodMatcher.match("西红柿炒蛋", listOf(memory()))!!.kind)
        assertTrue(PersonalFoodMatcher.match("西红柿炒蛋", listOf(memory()))!!.canApply)
    }

    @Test fun differentIngredientsAndCookingMethodsDoNotMatch() {
        listOf("番茄炒肉", "番茄蛋汤", "水煮蛋", "番茄炒蛋面").forEach {
            assertNull(PersonalFoodMatcher.match(it, listOf(memory())))
        }
        assertNull(PersonalFoodMatcher.match("香煎黑椒鸡胸肉", listOf(memory("香煎黑椒鸡腿肉"))))
    }

    @Test fun ambiguousAliasIsIgnoredAndExactMatchWins() {
        val other = memory("番茄鸡蛋汤")
        assertNull(PersonalFoodMatcher.match("西红柿炒蛋", listOf(memory(), other)))
        assertEquals("番茄炒蛋", PersonalFoodMatcher.match("番茄炒蛋", listOf(other, memory()))!!.memory.canonicalName)
    }

    @Test fun similarLongNameProvidesHintOnly() {
        val match = PersonalFoodMatcher.match("香煎黑椒鸡胸肉餐", listOf(memory("香煎黑椒鸡胸肉")))!!
        assertEquals(MemoryMatchKind.SIMILAR, match.kind)
        assertFalse(match.canApply)
        assertNull(PersonalFoodMatcher.match("家庭特制超级大份香煎黑椒鸡胸肉餐", listOf(memory("香煎黑椒鸡胸肉"))))
    }

    @Test fun uneditedSingleObservationCannotReplaceRecognition() {
        assertFalse(PersonalFoodMatcher.match("番茄炒蛋", listOf(memory().copy(correctionCount = 0, useCount = 1)))!!.canApply)
    }

    @Test fun acceptedPortionScalesCompositionAndNutrition() {
        val component = FoodComponent("old", "鸡蛋", "egg", source = ComponentSource.VISIBLE,
            estimatedWeightG = 100.0, weightMinG = 90.0, weightMaxG = 110.0, confidence = 0.9,
            nutritionReference = NutritionReference("egg", "鸡蛋", "china", Nutrition(140.0, 12.0)))
        val dish = RecognizedDish("dish", "番茄炒蛋", DishType.MIXED_DISH, 0.9, listOf(component))
        val accepted = dish.withMemoryPortion(memory().copy(components = listOf(component)))
        assertEquals("dish", accepted.id)
        assertNotEquals("old", accepted.components.single().id)
        assertEquals(230.0, accepted.grams, 0.001)
        assertEquals(322.0, accepted.nutrition.caloriesKcal, 0.001)
        assertTrue(accepted.needsConfirmation)
    }

    @Test fun manualNutritionUsesSnapshotWeightInsteadOfSmoothedWeight() {
        val dish = RecognizedDish("dish", "米饭", DishType.SINGLE_FOOD, 1.0, emptyList())
        val accepted = dish.withMemoryPortion(memory("米饭").copy(nutrition = Nutrition(116.0), nutritionGrams = 100.0))
        assertEquals(266.8, accepted.nutrition.caloriesKcal, 0.001)
        assertEquals(230.0, accepted.grams, 0.001)
    }

    @Test fun unrelatedDatabaseReferencesAreNeverReplaced() {
        val preferred = NutritionReference("preferred", "米饭", "china", Nutrition(116.0))
        val component = FoodComponent("id", "鸡蛋", "egg", source = ComponentSource.VISIBLE,
            estimatedWeightG = 100.0, weightMinG = 100.0, weightMaxG = 100.0, confidence = 0.9)
        val dish = RecognizedDish("dish", "番茄炒蛋", DishType.MIXED_DISH, 0.9, listOf(component))
        assertNull(dish.withPreferredReferences(memory().copy(preferredComponentReferences = mapOf("米饭" to preferred))).components.single().nutritionReference)
        assertEquals(preferred, dish.withPreferredReferences(memory().copy(preferredComponentReferences = mapOf("鸡蛋" to preferred))).components.single().nutritionReference)
    }

    @Test fun notesAndMealTimeDoNotCountAsFoodCorrections() {
        val log = FoodLog(name = "米饭", mealType = MealType.LUNCH, grams = 100.0, nutrition = Nutrition(116.0))
        assertFalse(foodWasCorrected(log.copy(note = "补记", mealType = MealType.DINNER, mealMinuteOfDay = 1200), log))
        assertTrue(foodWasCorrected(log.copy(grams = 150.0), log))
        assertTrue(foodWasCorrected(log.copy(name = "糙米饭"), log))
    }

    @Test fun ignoringMemoryRestoresAiReferenceButKeepsUserWeightEdit() {
        val old = NutritionReference("ai", "鸡蛋", "USDA", Nutrition(150.0))
        val preferred = old.copy(sourceId = "preferred")
        val component = FoodComponent("id", "鸡蛋", "egg", source = ComponentSource.VISIBLE,
            estimatedWeightG = 100.0, weightMinG = 100.0, weightMaxG = 100.0, confidence = 0.9, nutritionReference = old)
        val raw = RecognizedDish("dish", "番茄炒蛋", DishType.MIXED_DISH, 0.9, listOf(component))
        val memory = memory().copy(preferredComponentReferences = mapOf("鸡蛋" to preferred))
        val assisted = raw.withPreferredReferences(memory)
        val edited = assisted.copy(components = assisted.components.map { it.copy(estimatedWeightG = 120.0, weightMinG = 120.0, weightMaxG = 120.0) })
        val ignored = edited.withoutAutomaticMemoryReferences(raw, memory)
        assertEquals(old, ignored.components.single().nutritionReference)
        assertEquals(120.0, ignored.grams, 0.001)
    }

    @Test fun ignoringMemoryPreservesExplicitUserReferenceAndComponentReplacement() {
        val old = NutritionReference("ai", "鸡蛋", "USDA", Nutrition(150.0))
        val preferred = old.copy(sourceId = "preferred")
        val user = old.copy(sourceId = "new-user-choice")
        val component = FoodComponent("id", "鸡蛋", "egg", source = ComponentSource.VISIBLE,
            estimatedWeightG = 100.0, weightMinG = 100.0, weightMaxG = 100.0, confidence = 0.9, nutritionReference = old)
        val raw = RecognizedDish("dish", "番茄炒蛋", DishType.MIXED_DISH, 0.9, listOf(component))
        val memory = memory().copy(preferredComponentReferences = mapOf("鸡蛋" to preferred))
        val edited = raw.copy(name = "新名称", components = listOf(component.copy(name = "鸭蛋", nutritionReference = user)))
        assertEquals(edited, edited.withoutAutomaticMemoryReferences(raw, memory))
    }
}
