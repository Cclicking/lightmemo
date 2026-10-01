package com.click.lightmemo.domain

/** Remaining daily targets, clamped at zero after the planned meal. */
data class NutritionGap(
    val caloriesKcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
) {
    fun after(nutrition: Nutrition): NutritionGap = calculate(
        Nutrition(caloriesKcal, proteinG, carbsG, fatG), nutrition,
    )

    companion object {
        fun calculate(target: Nutrition, consumed: Nutrition, planned: Nutrition = Nutrition()): NutritionGap {
            listOf(target, consumed, planned).forEach { n ->
                require(listOf(n.caloriesKcal, n.proteinG, n.carbsG, n.fatG).all { it.isFinite() && it >= 0 })
            }
            return NutritionGap(
                (target.caloriesKcal - consumed.caloriesKcal - planned.caloriesKcal).coerceAtLeast(0.0),
                (target.proteinG - consumed.proteinG - planned.proteinG).coerceAtLeast(0.0),
                (target.carbsG - consumed.carbsG - planned.carbsG).coerceAtLeast(0.0),
                (target.fatG - consumed.fatG - planned.fatG).coerceAtLeast(0.0),
            )
        }
    }
}
