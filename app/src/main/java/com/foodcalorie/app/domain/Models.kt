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

    operator fun times(factor: Double): Nutrition = Nutrition(
        caloriesKcal = caloriesKcal * factor,
        proteinG = proteinG * factor,
        carbsG = carbsG * factor,
        fatG = fatG * factor,
    )
}

enum class DishType {
    SINGLE_FOOD,
    MIXED_DISH,
    STAPLE_WITH_TOPPINGS,
    SOUP_OR_NOODLE,
    SALAD,
    SANDWICH_OR_BURGER,
    COMBO_MEAL,
    BEVERAGE,
    DESSERT,
    OTHER,
}

enum class ComponentSource {
    VISIBLE,
    INFERRED,
    USER_PROVIDED,
}

data class NutritionReference(
    val sourceId: String,
    val description: String,
    val dataType: String,
    val per100g: Nutrition,
)

data class FoodComponent(
    val id: String,
    val name: String,
    val databaseQuery: String,
    val chinaDatabaseQuery: String = name,
    val source: ComponentSource,
    val estimatedWeightG: Double,
    val weightMinG: Double,
    val weightMaxG: Double,
    val confidence: Double,
    val needsConfirmation: Boolean = false,
    val nutritionReference: NutritionReference? = null,
) {
    val nutrition: Nutrition
        get() = nutritionReference?.per100g?.times(estimatedWeightG / 100.0) ?: Nutrition()

    val nutritionMin: Nutrition
        get() = nutritionReference?.per100g?.times(weightMinG / 100.0) ?: Nutrition()

    val nutritionMax: Nutrition
        get() = nutritionReference?.per100g?.times(weightMaxG / 100.0) ?: Nutrition()
}

data class RecognizedDish(
    val id: String,
    val name: String,
    val type: DishType,
    val confidence: Double,
    val components: List<FoodComponent>,
    val needsConfirmation: Boolean = false,
    val uncertaintyReason: String? = null,
    val children: List<RecognizedDish> = emptyList(),
) {
    val allComponents: List<FoodComponent>
        get() = components + children.flatMap { it.allComponents }

    val grams: Double get() = allComponents.sumOf { it.estimatedWeightG }
    val nutrition: Nutrition get() = allComponents.fold(Nutrition()) { total, item -> total + item.nutrition }
    val nutritionMin: Nutrition get() = allComponents.fold(Nutrition()) { total, item -> total + item.nutritionMin }
    val nutritionMax: Nutrition get() = allComponents.fold(Nutrition()) { total, item -> total + item.nutritionMax }
}

data class MealRecognition(
    val isFoodImage: Boolean,
    val mealName: String,
    val dishes: List<RecognizedDish>,
    val overallConfidence: Double,
    val confirmationQuestions: List<String>,
    val imageQualityIssues: List<String> = emptyList(),
) {
    val nutrition: Nutrition get() = dishes.fold(Nutrition()) { total, dish -> total + dish.nutrition }
    val nutritionMin: Nutrition get() = dishes.fold(Nutrition()) { total, dish -> total + dish.nutritionMin }
    val nutritionMax: Nutrition get() = dishes.fold(Nutrition()) { total, dish -> total + dish.nutritionMax }
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
    val components: List<FoodComponent> = emptyList(),
    val imageUri: String? = null,
    val dateEpochDay: Long = LocalDate.now().toEpochDay(),
    val createdAtMillis: Long = System.currentTimeMillis(),
    /** 当日分钟数（0–1439），表示用餐时间 */
    val mealMinuteOfDay: Int? = null,
    val note: String? = null,
    val mealTags: List<String> = emptyList(),
)

data class DayNutritionSummary(
    val dateEpochDay: Long,
    val total: Nutrition,
)
