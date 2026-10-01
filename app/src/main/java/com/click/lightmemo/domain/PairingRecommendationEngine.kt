package com.click.lightmemo.domain

import com.click.lightmemo.data.DefaultPresetFoods
import com.click.lightmemo.data.PresetFood
import kotlin.math.ln
import kotlin.math.roundToInt

enum class PairingSource { USER_PRESET, HISTORY, DEFAULT }

data class PairingContext(
    val target: Nutrition,
    val todayNutrition: Nutrition,
    val userPresets: List<PresetFood> = emptyList(),
    val recordedFoods: List<PresetFood> = emptyList(),
    val defaultFoods: List<PresetFood> = DefaultPresetFoods,
    val foodFrequency: Map<String, Int> = emptyMap(),
    val mainAlreadyRecorded: Boolean = false,
)

data class PairingRecommendation(
    val food: PresetFood,
    val nutrition: Nutrition,
    val source: PairingSource,
    val reason: String,
)

data class PairingResult(
    val gap: NutritionGap,
    val foods: List<PairingRecommendation>,
    val summary: String,
)

/** Local portion candidates only; never scans an external nutrition database. */
object PairingRecommendationEngine {
    // Two additions are supplements, with a shared calorie budget rather than two full meals.
    private const val MaxSupplementCalories = 350.0
    private data class Candidate(val food: PresetFood, val nutrition: Nutrition, val source: PairingSource)

    // Explicit synonyms only, so 米饭 is never paired with a historical 白米饭 portion.
    private fun foodKey(name: String): String = when (val normalized = normalizeFoodName(name)) {
        "白米饭", "白饭" -> "米饭"
        else -> normalized
    }

    fun recommend(main: DishRecommendation, context: PairingContext): PairingResult {
        val gap = NutritionGap.calculate(context.target, context.todayNutrition,
            if (context.mainAlreadyRecorded) Nutrition() else main.nutrition)
        if (gap.caloriesKcal <= 0) return PairingResult(gap, emptyList(),
            if (context.mainAlreadyRecorded) "今天的热量已达到或超过目标，暂不建议额外搭配。"
            else "吃完这道菜后，今天的热量将达到或超过目标，暂不建议额外搭配。")

        val frequencies = context.foodFrequency.entries.groupBy { foodKey(it.key) }
            .mapValues { (_, entries) -> entries.sumOf { it.value.coerceAtLeast(0).toLong() } }
        val candidates = buildList {
            context.userPresets.asReversed().forEach { add(Candidate(it, presetNutrition(it), PairingSource.USER_PRESET)) }
            context.recordedFoods.sortedByDescending { frequencies[foodKey(it.name)] ?: 0L }
                .forEach { add(Candidate(it, presetNutrition(it), PairingSource.HISTORY)) }
            context.defaultFoods.forEach { add(Candidate(it, presetNutrition(it), PairingSource.DEFAULT)) }
        }.filter { candidate ->
            val p = candidate.food
            foodKey(p.name).isNotEmpty() && foodKey(p.name) != foodKey(main.preset.name) &&
                p.defaultGrams.isFinite() && p.defaultGrams > 0 &&
                (p.nutrition != null || p.components.isNotEmpty() && p.components.all { it.nutritionReference != null }) &&
                listOf(candidate.nutrition.caloriesKcal, candidate.nutrition.proteinG, candidate.nutrition.carbsG, candidate.nutrition.fatG)
                    .all { it.isFinite() && it >= 0 } && candidate.nutrition.caloriesKcal > 0
        }.distinctBy { foodKey(it.food.name) }

        val calorieBudget = minOf(gap.caloriesKcal, MaxSupplementCalories)
        val fatBudget = gap.fatG + 2.0 // Shared tolerance for genuinely low-fat foods at the limit.
        val carbBudget = gap.carbsG + 5.0
        // Bound the pair search: compare combinations rather than greedily spending the budget.
        val ranked = candidates.filter { fits(it.nutrition, gap, calorieBudget, fatBudget, carbBudget) }
            .map { it to (nutritionScore(it.nutrition, gap, calorieBudget) + familiarity(it, frequencies)) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Candidate, Double>> { it.second }
                .thenBy { it.first.source.ordinal }.thenBy { it.first.food.name })
        // Keep a suitable preset in the bounded search even when history occupies the top ranks.
        val nonHistorical = ranked.firstOrNull { it.first.source != PairingSource.HISTORY }
        val top = ranked.take(32)
        val shortlist = if (nonHistorical != null && nonHistorical !in top) top.take(31) + nonHistorical else top
        val requireVariety = nonHistorical != null
        val initial = shortlist.firstOrNull { !requireVariety || it.first.source != PairingSource.HISTORY }
        var bestScore = initial?.second ?: 0.0
        var best = initial?.let { listOf(it.first) }.orEmpty()
        shortlist.forEachIndexed { index, (a, _) ->
            shortlist.drop(index + 1).forEach { (b, _) ->
                if (requireVariety && a.source == PairingSource.HISTORY && b.source == PairingSource.HISTORY) return@forEach
                fun canFollow(first: Candidate, second: Candidate) = fits(second.nutrition, gap.after(first.nutrition),
                    calorieBudget - first.nutrition.caloriesKcal, fatBudget - first.nutrition.fatG, carbBudget - first.nutrition.carbsG)
                val ordered = when {
                    canFollow(a, b) -> listOf(a, b)
                    canFollow(b, a) -> listOf(b, a)
                    else -> return@forEach
                }
                val pairScore = nutritionScore(a.nutrition + b.nutrition, gap, calorieBudget) +
                    (familiarity(a, frequencies) + familiarity(b, frequencies)) / 2 - .02
                if (pairScore > bestScore) { bestScore = pairScore; best = ordered }
            }
        }
        var remaining = gap
        val chosen = best.map { candidate ->
            PairingRecommendation(candidate.food, candidate.nutrition, candidate.source, reason(candidate, remaining))
                .also { remaining = remaining.after(candidate.nutrition) }
        }
        val prefix = if (context.mainAlreadyRecorded) "记录这道菜后" else "吃完这道菜后"
        val summary = buildList {
            if (chosen.isEmpty()) add("$prefix，暂无合适的补充搭配。")
            else {
                if (gap.proteinG >= 5) add("$prefix，距蛋白质目标还差约 ${gap.proteinG.roundToInt()}g。")
                else if (gap.carbsG >= 10) add("$prefix，距碳水目标还差约 ${gap.carbsG.roundToInt()}g。")
            }
            if (gap.carbsG < 10) add("计入这道菜后，今天碳水已比较充足，优先少加主食。")
            if (gap.fatG < 5) add("计入这道菜后，脂肪已接近或达到目标，优先选择低脂食物。")
        }.joinToString(" ")
        return PairingResult(gap, chosen, summary)
    }

    private fun fits(n: Nutrition, gap: NutritionGap, calories: Double, fat: Double, carbs: Double): Boolean =
        n.caloriesKcal <= calories && n.fatG <= fat && n.carbsG <= carbs &&
            ((gap.proteinG >= 5 && n.proteinG >= 3) || (gap.carbsG >= 10 && n.carbsG >= 10))

    private fun nutritionScore(n: Nutrition, gap: NutritionGap, calories: Double): Double {
        val protein = if (gap.proteinG >= 5) minOf(n.proteinG, gap.proteinG) / gap.proteinG else 0.0
        val carbs = if (gap.carbsG >= 10) minOf(n.carbsG, gap.carbsG) / gap.carbsG else 0.0
        val calorieFit = (n.caloriesKcal / calories.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
        val fatPenalty = (n.fatG / gap.fatG.coerceAtLeast(5.0)).coerceIn(0.0, 1.0)
        val excessProtein = (n.proteinG - gap.proteinG).coerceAtLeast(0.0) / n.proteinG.coerceAtLeast(1.0)
        return protein * .5 + carbs * .25 + calorieFit * .15 - fatPenalty * .25 - excessProtein * .1
    }

    private fun familiarity(c: Candidate, frequencies: Map<String, Long>): Double {
        val familiar = (ln(1.0 + (frequencies[foodKey(c.food.name)] ?: 0L)) / ln(11.0)).coerceIn(0.0, 1.0)
        val sourceBonus = when (c.source) { PairingSource.USER_PRESET -> .04; PairingSource.HISTORY -> .02; PairingSource.DEFAULT -> 0.0 }
        return familiar * .1 + sourceBonus
    }

    private fun reason(candidate: Candidate, gap: NutritionGap): String {
        val n = candidate.nutrition
        val nutrients = buildList {
            if (gap.proteinG >= 5 && n.proteinG >= 3) add("约 ${n.proteinG.roundToInt()}g 蛋白质")
            if (gap.carbsG >= 10 && n.carbsG >= 10) add("约 ${n.carbsG.roundToInt()}g 碳水")
        }.joinToString("、")
        return if (candidate.source == PairingSource.HISTORY) "按历史份量可补$nutrients"
            else "按这份量可补$nutrients"
    }
}
