package com.click.lightmemo.domain

import com.click.lightmemo.data.PresetFood
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RecommendationEngineTest {
    private fun food(name: String, nutrition: Nutrition = Nutrition(500.0, 30.0, 50.0, 10.0)) =
        PresetFood(id = name, name = name, defaultGrams = 200.0, portionLabel = "一份", nutrition = nutrition)
    private fun context(vararg names: String) = RecommendationContext(presets = names.map(::food), nowMillis = 10 * 86_400_000L)

    @Test fun weightedDrawStaysInsideTopNAndDoesNotAlwaysPickTheBest() {
        val dishes = (1..20).map { DishRecommendation(food("菜$it"), Nutrition(), 100 - it, emptyList()) }
        val random = Random(42)
        val counts = (1..1000).map { RecommendationEngine.pick(dishes, random = random, topN = 5)!!.preset.name }.groupingBy { it }.eachCount()
        assertEquals(dishes.take(5).map { it.preset.name }.toSet(), counts.keys)
        assertTrue(counts.values.all { it > 100 })
    }

    @Test fun weightingChangesSelectionProbability() {
        val a = DishRecommendation(food("高权重"), Nutrition(), 90, emptyList(), weight = 90.0)
        val b = a.copy(preset = food("低权重"), weight = 10.0)
        val random = Random(17)
        val picked = (1..2000).count { RecommendationEngine.pick(listOf(a, b), random = random)!!.preset.name == "高权重" }
        assertTrue(picked in 1700..1900)
    }

    @Test fun previousResultIsExcludedBeforeTopNAndRecentResultsArePenalized() {
        val dishes = RecommendationEngine.rank(context("甲", "乙", "丙"))
        val random = Random(2)
        val counts = (1..1000).map { RecommendationEngine.pick(dishes, listOf("甲", "乙"), random)!!.preset.name }.groupingBy { it }.eachCount()
        assertFalse(counts.containsKey("乙"))
        assertTrue(counts.getValue("丙") > counts.getValue("甲") * 2)
        assertEquals("乙", RecommendationEngine.pick(dishes.take(2), listOf("甲"), Random(1), topN = 1)!!.preset.name)
    }

    @Test fun emptyAndSinglePoolsHaveSafeFallbacks() {
        assertNull(RecommendationEngine.pick(emptyList()))
        val one = RecommendationEngine.rank(context("甲"))
        assertEquals(one.single(), RecommendationEngine.pick(one, listOf("甲")))
    }

    @Test fun explicitPreferenceImprovesWeightAndRecentRejectionExpires() {
        val c = context("甲", "乙")
        val liked = c.copy(preferences = mapOf("甲" to FoodPreference(liked = 2)))
        val rank = RecommendationEngine.rank(liked)
        assertEquals("甲", rank.first().preset.name)
        assertTrue(rank.first().reasons.any { "喜欢" in it })
        val rejected = c.copy(preferences = mapOf("甲" to FoodPreference(skipped = 1, lastRejectedAtMillis = c.nowMillis)))
        val freshWeight = RecommendationEngine.rank(rejected).first { it.preset.name == "甲" }.weight
        val expiredWeight = RecommendationEngine.rank(rejected.copy(nowMillis = c.nowMillis + 4 * 86_400_000L)).first { it.preset.name == "甲" }.weight
        assertTrue(expiredWeight > freshWeight)
    }

    @Test fun changeModeAvoidsRecentMealsAndFallsBackWhenAllWereEaten() {
        val c = context("甲", "乙").copy(recentFoodNames = setOf("甲"))
        assertEquals(listOf("乙"), RecommendationEngine.forMode(RecommendationEngine.rank(c), c, RecommendationMode.CHANGE).map { it.preset.name })
        val allRecent = c.copy(recentFoodNames = setOf("甲", "乙"))
        assertEquals(2, RecommendationEngine.forMode(RecommendationEngine.rank(allRecent), allRecent, RecommendationMode.CHANGE).size)
    }

    @Test fun habitualModeUsesKnownMealsAndFallsBackWithoutHistory() {
        val c = context("甲", "乙").copy(foodFrequency = mapOf("乙" to 4), mealFrequency = mapOf("乙" to 3))
        val known = RecommendationEngine.forMode(RecommendationEngine.rank(c), c, RecommendationMode.HABITUAL)
        assertEquals("乙", known.single().preset.name)
        assertTrue(known.single().reasons.any { "餐次" in it })
        val empty = context("甲", "乙")
        assertEquals(2, RecommendationEngine.forMode(RecommendationEngine.rank(empty), empty, RecommendationMode.HABITUAL).size)
    }

    @Test fun healthyModeRespondsToNutritionGaps() {
        val protein = food("蛋白菜", Nutrition(500.0, 50.0, 10.0, 10.0))
        val carbs = food("主食", Nutrition(500.0, 5.0, 100.0, 10.0))
        val c = RecommendationContext(presets = listOf(protein, carbs), proteinTarget = 50.0, carbsTarget = 100.0)
        val proteinNeed = c.copy(todayNutrition = Nutrition(carbsG = 100.0))
        val carbsNeed = c.copy(todayNutrition = Nutrition(proteinG = 50.0))
        fun best(c: RecommendationContext) = RecommendationEngine.forMode(RecommendationEngine.rank(c), c, RecommendationMode.HEALTHY).first().preset.name
        assertEquals("蛋白菜", best(proteinNeed))
        assertEquals("主食", best(carbsNeed))
    }

    @Test fun customPresetsOverrideNormalizedDuplicatesAndUnknownNutritionIsExcluded() {
        val custom = food("番茄 炒蛋", Nutrition(250.0))
        val invalid = food("未知").copy(nutrition = null)
        val c = RecommendationContext(presets = listOf(food("番茄炒蛋"), custom, invalid, food("坏数据", Nutrition(Double.NaN))))
        assertEquals(custom, RecommendationEngine.rank(c).single().preset)
    }

    @Test fun historyShareIsBoundedAndHistoryOnlyPoolStillWorks() {
        val history = (1..20).map { food("历史$it") }
        val c = RecommendationContext(presets = (1..8).map { food("预设$it") }, recordedFoods = history)
        assertEquals(2, RecommendationEngine.rank(c).count { it.preset.name.startsWith("历史") })
        assertEquals(20, RecommendationEngine.rank(c.copy(presets = emptyList())).size)
    }

    @Test fun portionConfirmationScalesNutritionComponentsAndMetadata() {
        val component = FoodComponent("old", "鸡蛋", "egg", source = ComponentSource.USER_PROVIDED,
            estimatedWeightG = 200.0, weightMinG = 180.0, weightMaxG = 220.0, confidence = 1.0,
            nutritionReference = NutritionReference("local", "鸡蛋", "local", Nutrition(250.0)))
        val preset = food("鸡蛋").copy(components = listOf(component))
        val dish = DishRecommendation(preset, preset.nutrition!!, 50, emptyList())
        val log = dish.toFoodLog(100.0, MealType.DINNER, 20000, 1080, 123)
        assertEquals(Nutrition(250.0, 15.0, 25.0, 5.0), log.nutrition)
        assertEquals(100.0, log.components.single().estimatedWeightG, .001)
        assertEquals(90.0, log.components.single().weightMinG, .001)
        assertNotEquals("old", log.components.single().id)
        assertEquals(20000L, log.dateEpochDay)
        assertEquals(1080, log.mealMinuteOfDay)
        assertEquals(123L, log.createdAtMillis)
        assertEquals(MealType.DINNER, log.mealType)
    }

    @Test fun invalidPortionsAreRejected() {
        val dish = RecommendationEngine.rank(context("甲")).single()
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { grams ->
            assertThrows(IllegalArgumentException::class.java) { dish.toFoodLog(grams, MealType.LUNCH, 20000, 720, 123) }
        }
    }
}
