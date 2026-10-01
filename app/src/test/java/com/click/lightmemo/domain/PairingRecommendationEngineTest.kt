package com.click.lightmemo.domain

import com.click.lightmemo.data.PresetFood
import org.junit.Assert.*
import org.junit.Test

class PairingRecommendationEngineTest {
    private fun food(name: String, calories: Double = 120.0, protein: Double = 10.0, carbs: Double = 0.0, fat: Double = 0.0) =
        PresetFood(id = name, name = name, defaultGrams = 100.0, portionLabel = "1 份", nutrition = Nutrition(calories, protein, carbs, fat))
    private fun main(nutrition: Nutrition = Nutrition(200.0)) =
        DishRecommendation(food("主菜").copy(nutrition = nutrition), nutrition, 50, emptyList())
    private fun context(foods: List<PresetFood>, target: Nutrition = Nutrition(1000.0, 30.0, 60.0, 20.0)) =
        PairingContext(target, Nutrition(), defaultFoods = foods)

    @Test fun suitablePresetsPreventAnAllHistoryPairing() {
        val base = context(listOf(food("预设蛋白", protein = 9.0)))
            .copy(recordedFoods = listOf(food("历史甲", protein = 12.0), food("历史乙", protein = 12.0)),
                foodFrequency = mapOf("历史甲" to 100, "历史乙" to 100))
        val result = PairingRecommendationEngine.recommend(main(), base)
        assertTrue(result.foods.isNotEmpty())
        assertTrue(result.foods.any { it.source != PairingSource.HISTORY })
        assertTrue(result.foods.count { it.source == PairingSource.HISTORY } <= 1)
        assertTrue(result.foods.sumOf { it.nutrition.caloriesKcal } <= 350.0)
    }

    @Test fun unsuitableDefaultsDoNotForceUnsafeVariety() {
        val base = context(listOf(food("超脂肪预设", fat = 40.0)))
            .copy(recordedFoods = listOf(food("历史低脂", protein = 15.0)))
        val result = PairingRecommendationEngine.recommend(main(), base)
        assertEquals(listOf("历史低脂"), result.foods.map { it.food.name })
        assertEquals(PairingSource.HISTORY, result.foods.single().source)
    }

    @Test fun presetRemainsAvailableWhenManyHistoryCandidatesFillTheShortlist() {
        val base = context(listOf(food("合适预设", protein = 3.0)))
            .copy(recordedFoods = (1..40).map { food("历史$it", protein = 15.0) },
                foodFrequency = (1..40).associate { "历史$it" to 100 })
        val result = PairingRecommendationEngine.recommend(main(), base)
        assertTrue(result.foods.any { it.source != PairingSource.HISTORY })
        assertTrue(result.foods.sumOf { it.nutrition.caloriesKcal } <= 350.0)
    }

    @Test fun gapsSubtractTodayAndMainAndNeverBecomeNegative() {
        val gap = NutritionGap.calculate(Nutrition(1000.0, 50.0, 100.0, 20.0), Nutrition(500.0, 10.0, 120.0, 15.0), Nutrition(200.0, 20.0, 10.0, 10.0))
        assertEquals(NutritionGap(300.0, 20.0, 0.0, 0.0), gap)
        assertEquals(NutritionGap(200.0, 10.0, 0.0, 0.0), gap.after(Nutrition(100.0, 10.0, 20.0, 3.0)))
    }

    @Test fun invalidNutritionDoesNotCreateSilentGaps() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { NutritionGap.calculate(Nutrition(value), Nutrition()) }
        }
    }

    @Test fun differentMacroGapsProduceDifferentFoodsAndReasons() {
        val protein = food("瘦肉", protein = 20.0)
        val rice = food("主食", protein = 2.0, carbs = 30.0)
        val base = context(listOf(protein, rice))
        val proteinOnly = PairingRecommendationEngine.recommend(main(), base.copy(target = Nutrition(1000.0, 30.0, 0.0, 20.0)))
        val carbsOnly = PairingRecommendationEngine.recommend(main(), base.copy(target = Nutrition(1000.0, 0.0, 60.0, 20.0)))
        assertEquals(listOf("瘦肉"), proteinOnly.foods.map { it.food.name })
        assertEquals(listOf("主食"), carbsOnly.foods.map { it.food.name })
        assertTrue(proteinOnly.foods.single().reason.contains("20g 蛋白质"))
        assertTrue(carbsOnly.foods.single().reason.contains("30g 碳水"))
        assertTrue(proteinOnly.summary.contains("少加主食"))
    }

    @Test fun mainNutritionChangesTheRemainingNeed() {
        val base = context(listOf(food("蛋白搭配", protein = 20.0), food("碳水搭配", protein = 0.0, carbs = 30.0)))
        val result = PairingRecommendationEngine.recommend(main(Nutrition(200.0, 30.0)), base)
        assertEquals(listOf("碳水搭配"), result.foods.map { it.food.name })
    }

    @Test fun savedMainIsNotSubtractedTwice() {
        val m = main(Nutrition(200.0, 10.0, 20.0, 5.0))
        val before = PairingRecommendationEngine.recommend(m, context(emptyList()))
        val after = PairingRecommendationEngine.recommend(m, context(emptyList()).copy(todayNutrition = m.nutrition, mainAlreadyRecorded = true))
        assertEquals(before.gap, after.gap)
    }

    @Test fun userPresetOverridesNormalizedHistoryAndDefaultNames() {
        val custom = food(" 无糖 酸奶 ", protein = 15.0)
        val base = context(listOf(food("无糖酸奶", protein = 5.0)))
            .copy(userPresets = listOf(custom), recordedFoods = listOf(food("无糖酸奶", protein = 9.0)))
        val result = PairingRecommendationEngine.recommend(main(), base)
        assertEquals(custom, result.foods.single().food)
        assertEquals(PairingSource.USER_PRESET, result.foods.single().source)
        assertEquals(15.0, result.foods.single().nutrition.proteinG, .001)
    }

    @Test fun knownHistoryWinsAnOtherwiseEquivalentCandidate() {
        val base = context(listOf(food("默认食物")))
            .copy(recordedFoods = listOf(food("常吃食物")), foodFrequency = mapOf("常 吃食物" to 8))
        val result = PairingRecommendationEngine.recommend(main(), base)
        assertEquals("常吃食物", result.foods.first().food.name)
        assertEquals(PairingSource.HISTORY, result.foods.first().source)
        assertTrue(result.foods.first().reason.contains("历史份量"))
    }

    @Test fun highFrequencyCannotOverrideFatConstraint() {
        val base = context(listOf(food("低脂", fat = 1.0), food("高脂", fat = 15.0)))
            .copy(todayNutrition = Nutrition(fatG = 30.0), foodFrequency = mapOf("高脂" to 1000))
        val result = PairingRecommendationEngine.recommend(main(), base)
        assertEquals(listOf("低脂"), result.foods.map { it.food.name })
        assertTrue(result.summary.contains("低脂"))
    }

    @Test fun secondChoiceSharesCalorieFatAndCarbBudgets() {
        val foods = listOf(food("甲", calories = 180.0, fat = 1.5), food("乙", calories = 180.0, fat = 1.5), food("丙", calories = 100.0, fat = .5))
        val result = PairingRecommendationEngine.recommend(main(), context(foods).copy(target = Nutrition(1000.0, 60.0, 0.0, 0.0)))
        assertEquals(2, result.foods.size)
        assertTrue(result.foods.sumOf { it.nutrition.caloriesKcal } <= 350.0)
        assertTrue(result.foods.sumOf { it.nutrition.fatG } <= 2.0)
        assertEquals(2, result.foods.map { it.food.name }.distinct().size)
    }

    @Test fun smallCalorieRoomDoesNotRecommendOversizedPortions() {
        val base = context(listOf(food("太大", calories = 120.0), food("适量", calories = 60.0)))
            .copy(target = Nutrition(300.0, 30.0, 60.0, 20.0), todayNutrition = Nutrition(30.0))
        val result = PairingRecommendationEngine.recommend(main(), base)
        assertEquals(listOf("适量"), result.foods.map { it.food.name })
        assertTrue(result.foods.sumOf { it.nutrition.caloriesKcal } <= result.gap.caloriesKcal)
    }

    @Test fun stopsWhenFirstSupplementClosesTheMacroGap() {
        val base = context(listOf(food("甲", protein = 30.0), food("乙", protein = 30.0)), Nutrition(1000.0, 30.0, 0.0, 20.0))
        assertEquals(1, PairingRecommendationEngine.recommend(main(), base).foods.size)
    }

    @Test fun caloriesOrMacrosAlreadyMetAllowZeroSuggestions() {
        val base = context(listOf(food("搭配")))
        val full = PairingRecommendationEngine.recommend(main(), base.copy(todayNutrition = Nutrition(1000.0)))
        assertTrue(full.foods.isEmpty())
        assertTrue(full.summary.contains("热量将达到或超过目标"))
        val recorded = PairingRecommendationEngine.recommend(main(), base.copy(todayNutrition = Nutrition(1000.0), mainAlreadyRecorded = true))
        assertTrue(recorded.summary.contains("热量已达到或超过目标"))
        assertTrue(PairingRecommendationEngine.recommend(main(), base.copy(target = Nutrition(1000.0, 0.0, 0.0, 20.0))).foods.isEmpty())
    }

    @Test fun excludesTheMainDuplicatesUnknownAndInvalidNutrition() {
        val foods = listOf(food("主 菜"), food("未知").copy(nutrition = null), food("坏数据", protein = Double.NaN),
            food("坏重量").copy(defaultGrams = 0.0), food("有效"), food("有 效"))
        val result = PairingRecommendationEngine.recommend(main(), context(foods))
        assertEquals(listOf("有效"), result.foods.map { it.food.name })
    }

    @Test fun invalidCustomOverrideFallsBackToKnownNutrition() {
        val result = PairingRecommendationEngine.recommend(main(), context(listOf(food("酸奶")))
            .copy(userPresets = listOf(food("酸奶").copy(nutrition = null))))
        assertEquals(PairingSource.DEFAULT, result.foods.single().source)
    }

    @Test fun componentOnlyPresetUsesItsNutritionReference() {
        val reference = NutritionReference("local", "蛋白", "local", Nutrition(100.0, 10.0))
        val component = FoodComponent("id", "蛋白", "egg", source = ComponentSource.USER_PROVIDED,
            estimatedWeightG = 100.0, weightMinG = 100.0, weightMaxG = 100.0, confidence = 1.0, nutritionReference = reference)
        val preset = food("自定义").copy(nutrition = null, components = listOf(component))
        val result = PairingRecommendationEngine.recommend(main(), context(emptyList()).copy(userPresets = listOf(preset)))
        assertEquals(Nutrition(100.0, 10.0), result.foods.single().nutrition)
    }

    @Test fun fullSizeMainCanStillHaveAnAppropriateSupplement() {
        val result = PairingRecommendationEngine.recommend(main(Nutrition(500.0)), context(listOf(food("补充"))))
        assertEquals(1, result.foods.size)
    }

    @Test fun riceCannotBePairedWithItsHistoricalSynonym() {
        val rice = food("米饭", protein = 3.0, carbs = 30.0)
        val m = DishRecommendation(rice, rice.nutrition!!, 50, emptyList())
        val base = context(listOf(food("白饭", carbs = 30.0), food("鸡肉", protein = 20.0)))
            .copy(recordedFoods = listOf(food("白米饭", carbs = 30.0)))
        val result = PairingRecommendationEngine.recommend(m, base)
        assertEquals(listOf("鸡肉"), result.foods.map { it.food.name })
    }

    @Test fun comparesPairsInsteadOfGreedilyUsingTheBudgetOnTheFirstFood() {
        val foods = listOf(food("面包", 154.0, 7.2, 25.8, 2.4), food("鸡蛋", 72.0, 6.3, .4, 4.8),
            food("鸡胸肉", 198.0, 37.1, 0.0, 4.3), food("苹果", 104.0, .6, 27.6, .3))
        val base = context(foods, Nutrition(1000.0, 71.0, 31.0, 15.0))
        val result = PairingRecommendationEngine.recommend(main(), base)
        assertEquals(setOf("鸡胸肉", "苹果"), result.foods.map { it.food.name }.toSet())
        assertTrue(result.foods.sumOf { it.nutrition.caloriesKcal } <= 350.0)
    }
}
