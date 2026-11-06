package com.click.lightmemo.domain

import kotlin.math.abs

fun List<FoodComponent>.completeNutrition(): Nutrition? =
    if (isEmpty() || any { it.nutritionReference == null }) null
    else fold(Nutrition()) { total, component -> total + component.nutrition }

/** Totals and composition are stored independently, including in legacy backups. */
val FoodLog.hasManualNutrition: Boolean
    get() = components.isNotEmpty() && !nutrition.matches(
        components.fold(Nutrition()) { total, component -> total + component.nutrition },
    )

private fun Nutrition.matches(other: Nutrition): Boolean =
    listOf(caloriesKcal to other.caloriesKcal, proteinG to other.proteinG,
        carbsG to other.carbsG, fatG to other.fatG).all { (a, b) ->
        abs(a - b) <= 1e-6 * maxOf(1.0, abs(a), abs(b))
    }

/** Editing composition only recalculates totals that were already component-derived. */
fun FoodLog.withEditedComponents(next: List<FoodComponent>, updateGrams: Boolean = true): FoodLog = copy(
    components = next,
    grams = if (updateGrams && next.isNotEmpty()) next.sumOf { it.estimatedWeightG } else grams,
    nutrition = if (hasManualNutrition || (components.isEmpty() && nutrition != Nutrition())) nutrition
        else next.completeNutrition() ?: nutrition,
)

fun FoodLog.withComponentNutrition(): FoodLog =
    components.completeNutrition()?.let { copy(nutrition = it) } ?: this

/** The displayed ratio remains accurate even when a progress arc is full. */
internal fun nutritionTargetRatio(value: Double, target: Float): Double =
    if (!value.isFinite() || !target.isFinite() || target <= 0f) 0.0
    else (value / target).coerceAtLeast(0.0)
