package com.foodcalorie.app.domain

import java.time.LocalDate

data class Nutrition(
    val caloriesKcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
) {
    operator fun plus(other: Nutrition): Nutrition = Nutrition(
        caloriesKcal = caloriesKcal + other.caloriesKcal,
        proteinG = proteinG + other.proteinG,
        carbsG = carbsG + other.carbsG,
        fatG = fatG + other.fatG,
    )
}

enum class MealType(val label: String) {
    BREAKFAST("早餐"),
    LUNCH("午餐"),
    DINNER("晚餐"),
    SNACK("加餐"),
}

data class FoodLog(
    val id: Long = 0L,
    val name: String,
    val mealType: MealType,
    val grams: Double,
    val nutrition: Nutrition,
    val imageUri: String? = null,
    val dateEpochDay: Long = LocalDate.now().toEpochDay(),
    val createdAtMillis: Long = System.currentTimeMillis(),
)

data class RecognizedFood(
    val name: String,
    val grams: Double,
    val nutrition: Nutrition,
)

data class DayNutritionSummary(
    val dateEpochDay: Long,
    val total: Nutrition,
)
