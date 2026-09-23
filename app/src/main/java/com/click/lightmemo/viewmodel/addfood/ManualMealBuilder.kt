package com.click.lightmemo.viewmodel

import com.click.lightmemo.domain.ComponentSource
import com.click.lightmemo.domain.DishType
import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.domain.MealRecognition
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.domain.NutritionReference
import com.click.lightmemo.domain.RecognizedDish
import kotlinx.coroutines.flow.MutableStateFlow

const val NewComponentId = "__new_food_component__"

/** 文字/手动识别结果卡片：与视觉识别 Review 同构。 */
internal fun buildManualMealRecognition(
    name: String,
    grams: Double,
    nutrition: Nutrition,
): MealRecognition {
    val safeName = name.ifBlank { "食物" }
    val safeGrams = grams.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    val per100g = if (safeGrams > 0.0) nutrition * (100.0 / safeGrams) else Nutrition()
    val component = FoodComponent(
        id = java.util.UUID.randomUUID().toString(),
        name = safeName,
        databaseQuery = safeName,
        chinaDatabaseQuery = safeName,
        source = ComponentSource.USER_PROVIDED,
        estimatedWeightG = safeGrams,
        weightMinG = safeGrams,
        weightMaxG = safeGrams,
        confidence = 1.0,
        nutritionReference = per100g.takeIf { nutrition ->
            nutrition.caloriesKcal > 0.0 || nutrition.proteinG > 0.0 ||
                nutrition.carbsG > 0.0 || nutrition.fatG > 0.0
        }?.let { resolvedNutrition ->
            NutritionReference(
                sourceId = "manual-recognition",
                description = safeName,
                dataType = "文字识别",
                per100g = resolvedNutrition,
            )
        },
    )
    val dish = RecognizedDish(
        id = java.util.UUID.randomUUID().toString(),
        name = safeName,
        type = DishType.SINGLE_FOOD,
        confidence = 1.0,
        components = listOf(component),
    )
    return MealRecognition(
        isFoodImage = false,
        mealName = safeName,
        dishes = listOf(dish),
        overallConfidence = 1.0,
        confirmationQuestions = emptyList(),
    )
}

internal fun RecognizedDish.updateWeight(componentId: String, grams: Double): RecognizedDish = copy(
    components = components.map { component ->
        if (component.id == componentId) component.withWeight(grams) else component
    },
    children = children.map { it.updateWeight(componentId, grams) },
)

internal fun RecognizedDish.removeComponent(componentId: String): RecognizedDish = copy(
    components = components.filterNot { it.id == componentId },
    children = children.map { it.removeComponent(componentId) },
)

internal fun FoodComponent.withWeight(grams: Double): FoodComponent {
    val oldEstimate = estimatedWeightG.takeIf { it > 0.0 } ?: 1.0
    val minRatio = weightMinG / oldEstimate
    val maxRatio = weightMaxG / oldEstimate
    return copy(
        estimatedWeightG = grams,
        weightMinG = (grams * minRatio).coerceAtMost(grams),
        weightMaxG = (grams * maxRatio).coerceAtLeast(grams),
    )
}

internal fun RecognizedDish.withReference(
    componentId: String,
    reference: NutritionReference,
    replacementName: String? = null,
): RecognizedDish = copy(
    components = components.map { component ->
        if (component.id == componentId) {
            val nextName = replacementName ?: component.name
            val isChinaReference = reference.dataType.contains("中国")
            component.copy(
                name = nextName,
                databaseQuery = when {
                    replacementName == null -> component.databaseQuery
                    isChinaReference -> nextName
                    else -> reference.description
                },
                chinaDatabaseQuery = replacementName ?: component.chinaDatabaseQuery,
                nutritionReference = reference,
            )
        } else {
            component
        }
    },
    children = children.map { it.withReference(componentId, reference, replacementName) },
)

internal fun RecognizedDish.withFreshIds(): RecognizedDish = copy(
    id = java.util.UUID.randomUUID().toString(),
    components = components.map { it.copy(id = java.util.UUID.randomUUID().toString()) },
    children = children.map { it.withFreshIds() },
)

internal inline fun MutableStateFlow<AddFoodUiState>.updateDatabaseSearch(
    componentId: String,
    transform: (DatabaseSearchState) -> DatabaseSearchState,
) {
    val current = value.databaseSearch ?: return
    if (current.componentId == componentId) {
        value = value.copy(databaseSearch = transform(current))
    }
}
