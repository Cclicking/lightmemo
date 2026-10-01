package com.click.lightmemo.domain

import com.click.lightmemo.data.PresetFood
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.random.Random

enum class RecommendationMode(val label: String) {
    CASUAL("随便吃"), HEALTHY("健康吃"), CHANGE("新口味"), HABITUAL("照旧吃"),
}

data class FoodPreference(
    val liked: Long = 0, val accepted: Long = 0,
    val disliked: Long = 0, val skipped: Long = 0, val lastRejectedAtMillis: Long = 0,
)

data class RecommendationContext(
    val presets: List<PresetFood> = emptyList(),
    val recordedFoods: List<PresetFood> = emptyList(),
    val todayNutrition: Nutrition = Nutrition(),
    val target: Double = 1800.0,
    val proteinTarget: Double = 120.0,
    val carbsTarget: Double = 250.0,
    val fatTarget: Double = 60.0,
    val foodFrequency: Map<String, Int> = emptyMap(),
    val recentFoodNames: Set<String> = emptySet(),
    val mealFrequency: Map<String, Int> = emptyMap(),
    val preferences: Map<String, FoodPreference> = emptyMap(),
    val nowMillis: Long = System.currentTimeMillis(),
)

data class DishRecommendation(
    val preset: PresetFood,
    val nutrition: Nutrition,
    val score: Int,
    val reasons: List<String>,
    val weight: Double = score.toDouble(),
)

/** Pure, local recommendation policy; the carousel only presents its selected result. */
object RecommendationEngine {
    private fun deduplicate(foods: List<PresetFood>) = foods.asReversed()
        .distinctBy { normalizeFoodName(it.name) }.asReversed()

    fun rank(context: RecommendationContext): List<DishRecommendation> {
        val base = deduplicate(context.presets).filter { usable(it) }
        val names = base.mapTo(mutableSetOf()) { normalizeFoodName(it.name) }
        val history = deduplicate(context.recordedFoods).filter { usable(it) && normalizeFoodName(it.name) !in names }
            .sortedByDescending { context.foodFrequency[normalizeFoodName(it.name)] ?: 0 }
            .take(if (base.isEmpty()) Int.MAX_VALUE else (base.size * 0.25).toInt().coerceAtLeast(1))
        return (base + history).map { preset ->
            val nutrition = presetNutrition(preset)
            val key = normalizeFoodName(preset.name)
            val preference = context.preferences[key]
            val novelty = if (key in context.recentFoodNames) 0.2 else 1.0
            val frequency = frequency(context.foodFrequency[key])
            val mealFit = frequency(context.mealFrequency[key])
            val fit = nutritionFit(nutrition, context)
            val score = (100 * (fit * .45 + preferenceFit(preference) * .20 + novelty * .15 + frequency * .10 + mealFit * .10))
                .roundToInt().coerceIn(1, 99)
            val reasons = buildList {
                if (preference != null && preference.liked + preference.accepted > 0) add("你喜欢或选择过这道菜")
                if (mealFit > 0) add("符合你这个餐次的记录习惯")
                addAll(recommendationReasons(preset, nutrition, context))
            }.take(3)
            DishRecommendation(preset, nutrition, score, reasons, score * rejectionMultiplier(preference, context.nowMillis))
        }.sortedByDescending { it.weight }
    }

    fun forMode(dishes: List<DishRecommendation>, context: RecommendationContext, mode: RecommendationMode): List<DishRecommendation> {
        val pool = when (mode) {
            RecommendationMode.CHANGE -> dishes.filterNot { normalizeFoodName(it.preset.name) in context.recentFoodNames }.ifEmpty { dishes }
            RecommendationMode.HABITUAL -> dishes.filter { (context.foodFrequency[normalizeFoodName(it.preset.name)] ?: 0) > 0 }.ifEmpty { dishes }
            else -> dishes
        }
        return pool.map { dish ->
            val preference = context.preferences[normalizeFoodName(dish.preset.name)]
            val weight = when (mode) {
                RecommendationMode.HEALTHY -> ((nutritionFit(dish.nutrition, context) * 80 + preferenceFit(preference) * 20)
                    * rejectionMultiplier(preference, context.nowMillis))
                RecommendationMode.HABITUAL -> dish.weight * (1 + frequency(context.foodFrequency[normalizeFoodName(dish.preset.name)]) * 2)
                else -> dish.weight
            }
            dish.copy(weight = weight.coerceAtLeast(.01))
        }.sortedByDescending { it.weight }
    }

    /** Remove the last result before forming Top N; mildly penalize the two earlier draws. */
    fun pick(dishes: List<DishRecommendation>, recentResults: List<String> = emptyList(), random: Random = Random.Default, topN: Int = 10): DishRecommendation? {
        if (dishes.isEmpty()) return null
        require(topN > 0)
        val recent = recentResults.takeLast(3).map(::normalizeFoodName)
        val pool = dishes.filterNot { normalizeFoodName(it.preset.name) == recent.lastOrNull() }
            .ifEmpty { dishes }.take(topN)
        val weights = pool.map { dish ->
            dish.weight.coerceAtLeast(.01) * if (normalizeFoodName(dish.preset.name) in recent) .3 else 1.0
        }
        var draw = random.nextDouble() * weights.sum()
        pool.forEachIndexed { index, dish ->
            draw -= weights[index]
            if (draw < 0) return dish
        }
        return pool.last()
    }

    private fun frequency(count: Int?) = (ln(1.0 + (count ?: 0).coerceAtLeast(0)) / ln(11.0)).coerceIn(0.0, 1.0)

    private fun preferenceFit(p: FoodPreference?): Double {
        if (p == null) return .5
        val positive = (p.liked.toDouble() + p.accepted).coerceAtLeast(0.0)
        val negative = (p.disliked.toDouble() + p.skipped * .25).coerceAtLeast(0.0)
        return ((positive + 1) / (positive + negative + 2)).coerceIn(0.0, 1.0)
    }

    private fun rejectionMultiplier(p: FoodPreference?, now: Long): Double {
        if (p == null || p.lastRejectedAtMillis <= 0 || now - p.lastRejectedAtMillis !in 0..(3 * 86_400_000L)) return 1.0
        return if (p.disliked > 0) .2 else .55
    }

    private fun nutritionFit(n: Nutrition, c: RecommendationContext): Double {
        val mealCalories = ((c.target - c.todayNutrition.caloriesKcal).coerceAtLeast(0.0) / 2).coerceIn(300.0, 750.0)
        val proteinGap = (c.proteinTarget - c.todayNutrition.proteinG).coerceAtLeast(0.0)
        val carbsGap = (c.carbsTarget - c.todayNutrition.carbsG).coerceAtLeast(0.0)
        val proteinCoverage = if (proteinGap > 0) (n.proteinG / proteinGap.coerceAtLeast(20.0)).coerceIn(0.0, 1.0) else 0.0
        val carbsCoverage = if (carbsGap > 0) (n.carbsG / carbsGap.coerceAtLeast(40.0)).coerceIn(0.0, 1.0) else 0.0
        val fatRoom = (c.fatTarget - c.todayNutrition.fatG).coerceAtLeast(1.0)
        return proteinCoverage * .40 + carbsCoverage * .20 +
            (1 - abs(n.caloriesKcal - mealCalories) / mealCalories).coerceIn(0.0, 1.0) * .25 +
            (1 - (n.fatG - fatRoom).coerceAtLeast(0.0) / fatRoom).coerceIn(0.0, 1.0) * .15
    }

    private fun usable(p: PresetFood): Boolean {
        val n = presetNutrition(p)
        return normalizeFoodName(p.name).isNotEmpty() && p.defaultGrams.isFinite() && p.defaultGrams > 0 &&
            (p.nutrition != null || p.components.isNotEmpty() && p.components.all { it.nutritionReference != null }) &&
            listOf(n.caloriesKcal, n.proteinG, n.carbsG, n.fatG).all { it.isFinite() && it >= 0 }
    }
}

fun presetNutrition(preset: PresetFood): Nutrition = preset.nutrition ?: preset.components
    .fold(Nutrition()) { total, component -> total + component.nutrition }

/** Scale the known portion without recognition or external nutrition lookup. */
fun DishRecommendation.toFoodLog(grams: Double, mealType: MealType, dateEpochDay: Long, minuteOfDay: Int, nowMillis: Long): FoodLog {
    require(grams.isFinite() && grams > 0) { "请输入大于 0 的有效克数" }
    require(preset.defaultGrams.isFinite() && preset.defaultGrams > 0)
    val factor = grams / preset.defaultGrams
    return FoodLog(
        name = preset.name, mealType = mealType, grams = grams, nutrition = nutrition * factor,
        components = preset.components.map { it.copy(
            id = java.util.UUID.randomUUID().toString(), estimatedWeightG = it.estimatedWeightG * factor,
            weightMinG = it.weightMinG * factor, weightMaxG = it.weightMaxG * factor,
        ) },
        dateEpochDay = dateEpochDay, createdAtMillis = nowMillis, mealMinuteOfDay = minuteOfDay,
    )
}

private fun recommendationReasons(
    preset: PresetFood,
    nutrition: Nutrition,
    state: RecommendationContext,
): List<String> {
    val remainingCalories = (state.target - state.todayNutrition.caloriesKcal).coerceAtLeast(0.0)
    val mealCalories = (remainingCalories / 2.0).coerceIn(300.0, 750.0)
    val proteinGap = (state.proteinTarget - state.todayNutrition.proteinG).coerceAtLeast(0.0)
    val carbsGap = (state.carbsTarget - state.todayNutrition.carbsG).coerceAtLeast(0.0)
    val fatAllowance = (state.fatTarget - state.todayNutrition.fatG).coerceAtLeast(0.0)
    val wordingVariant = (preset.name.hashCode() and Int.MAX_VALUE) % 3
    return buildList {
        if (proteinGap >= 8.0 && nutrition.proteinG >= 10.0) {
            add(
                when (wordingVariant) {
                    0 -> "这份大约有 ${nutrition.proteinG.toInt()}g 蛋白质，吃完更顶饱"
                    1 -> "含约 ${nutrition.proteinG.toInt()}g 蛋白质，适合当今天的一顿正餐"
                    else -> "蛋白质约 ${nutrition.proteinG.toInt()}g，和主食、蔬菜搭配就很完整"
                },
            )
        } else if (nutrition.proteinG >= 10.0) {
            add(
                when (wordingVariant) {
                    0 -> "蛋白质约 ${nutrition.proteinG.toInt()}g，比只吃主食更顶饱"
                    1 -> "含约 ${nutrition.proteinG.toInt()}g 蛋白质，作为一餐比较扎实"
                    else -> "这道菜有 ${nutrition.proteinG.toInt()}g 蛋白质，适合配饭一起吃"
                },
            )
        }
        if (nutrition.caloriesKcal <= mealCalories * 1.15) {
            add(
                when (wordingVariant) {
                    0 -> "热量约 ${nutrition.caloriesKcal.toInt()} kcal，作为今天这一餐分量不重"
                    1 -> "一份约 ${nutrition.caloriesKcal.toInt()} kcal，今天吃它比较轻松"
                    else -> "约 ${nutrition.caloriesKcal.toInt()} kcal，放在正餐里刚好"
                },
            )
        }
        if (nutrition.fatG <= state.fatTarget / 4.0) {
            add(
                when (wordingVariant) {
                    0 -> "脂肪约 ${nutrition.fatG.toInt()}g，想吃清淡一点可以选它"
                    1 -> "脂肪只有约 ${nutrition.fatG.toInt()}g，比油炸类更轻"
                    else -> "脂肪约 ${nutrition.fatG.toInt()}g，今天少油一点时很合适"
                },
            )
        } else if (nutrition.fatG <= fatAllowance) {
            add(
                when (wordingVariant) {
                    0 -> "脂肪约 ${nutrition.fatG.toInt()}g，今天控制油量时还能安排"
                    1 -> "脂肪约 ${nutrition.fatG.toInt()}g，配一份蔬菜会更合适"
                    else -> "这份脂肪约 ${nutrition.fatG.toInt()}g，别再搭配太多油炸小菜就好"
                },
            )
        }
        if (carbsGap >= 20.0 && nutrition.carbsG >= 20.0) {
            add(
                when (wordingVariant) {
                    0 -> "碳水约 ${nutrition.carbsG.toInt()}g，能补上米饭或面条的能量"
                    1 -> "有约 ${nutrition.carbsG.toInt()}g 碳水，适合今天想吃主食的一顿"
                    else -> "这份约含 ${nutrition.carbsG.toInt()}g 碳水，吃它时主食不用点太多"
                },
            )
        } else if (nutrition.carbsG <= state.carbsTarget / 5.0) {
            add(
                when (wordingVariant) {
                    0 -> "碳水约 ${nutrition.carbsG.toInt()}g，不想吃太多米饭时可以选它"
                    1 -> "主食量不多，适合今天少吃一点面或饭"
                    else -> "这份碳水约 ${nutrition.carbsG.toInt()}g，和其他菜一起点也好控制"
                },
            )
        }
        if (preset.portionLabel.isNotBlank()) {
            add(
                when (wordingVariant) {
                    0 -> "建议吃${preset.portionLabel}（约 ${preset.defaultGrams.toInt()}g），分量一眼就能看清"
                    1 -> "点${preset.portionLabel}就够一餐，约 ${preset.defaultGrams.toInt()}g"
                    else -> "按${preset.portionLabel}来吃，约 ${preset.defaultGrams.toInt()}g，不用特意估份量"
                },
            )
        }
        if (isEmpty()) {
            add(
                when (wordingVariant) {
                    0 -> "没有明显短板，按推荐分量吃就可以"
                    1 -> "营养信息齐全，照着一份的量吃即可"
                    else -> "分量清楚，今天直接点一份比较合适"
                },
            )
        }
    }.take(3)
}
