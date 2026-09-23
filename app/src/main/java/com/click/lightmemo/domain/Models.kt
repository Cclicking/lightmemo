package com.click.lightmemo.domain

import java.time.LocalDate

@kotlinx.serialization.Serializable
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

@kotlinx.serialization.Serializable
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

@kotlinx.serialization.Serializable
enum class ComponentSource {
    VISIBLE,
    INFERRED,
    USER_PROVIDED,
}

@kotlinx.serialization.Serializable
data class NutritionReference(
    val sourceId: String,
    val description: String,
    val dataType: String,
    val per100g: Nutrition,
)

@kotlinx.serialization.Serializable
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

@kotlinx.serialization.Serializable
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

@kotlinx.serialization.Serializable
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

/** Converts a shared meal estimate into the single-person amount saved by the app. */
fun MealRecognition.scaledForServing(servingCount: Double): MealRecognition {
    if (!servingCount.isFinite() || servingCount <= 1.0) return this
    return copy(dishes = dishes.map { it.scaledForServing(servingCount) })
}

private fun RecognizedDish.scaledForServing(servingCount: Double): RecognizedDish = copy(
    components = components.map { component ->
        component.copy(
            estimatedWeightG = component.estimatedWeightG / servingCount,
            weightMinG = component.weightMinG / servingCount,
            weightMaxG = component.weightMaxG / servingCount,
        )
    },
    children = children.map { it.scaledForServing(servingCount) },
)

/** Promote recognized sub-dishes without losing components attached to their parent. */
fun MealRecognition.splitDishes(): MealRecognition = copy(
    dishes = dishes.flatMap { it.splitDishes() },
)

private fun RecognizedDish.splitDishes(): List<RecognizedDish> =
    if (children.isEmpty()) listOf(this) else buildList {
        if (components.isNotEmpty()) add(copy(children = emptyList()))
        children.forEach { addAll(it.splitDishes()) }
    }

@kotlinx.serialization.Serializable
enum class MealType(val label: String) {
    BREAKFAST("早餐"),
    LUNCH("午餐"),
    DINNER("晚餐"),
    SNACK("加餐"),
}

/** User-visible stages shared by the screen and the foreground live update. */
@kotlinx.serialization.Serializable
enum class RecognitionStage(
    val title: String,
    val detail: String,
    val progress: Int,
) {
    PREPARING("准备中", "正在准备识别任务", 8),
    RECOGNIZING("识别中", "正在分析食物与重量", 25),
    REVIEWING("复核中", "正在复核识别结果", 50),
    MATCHING("匹配中", "正在匹配营养数据库", 75),
    COMPLETED("识别完成", "点击查看识别结果", 100),
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
