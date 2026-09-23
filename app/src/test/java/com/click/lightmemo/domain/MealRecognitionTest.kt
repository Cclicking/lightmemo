package com.click.lightmemo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MealRecognitionTest {
    @Test fun splitsNestedDishesWithoutLosingWeightOrNutrition() {
        fun dish(id: String, children: List<RecognizedDish> = emptyList()) = RecognizedDish(
            id = id, name = id, type = DishType.MIXED_DISH, confidence = 1.0,
            components = listOf(FoodComponent(
                id = "component-$id", name = id, databaseQuery = id,
                source = ComponentSource.VISIBLE, estimatedWeightG = 100.0,
                weightMinG = 80.0, weightMaxG = 120.0, confidence = 1.0,
                nutritionReference = NutritionReference(id, id, "test", Nutrition(150.0, 10.0, 20.0, 3.0)),
            )), children = children,
        )
        val parent = dish("套餐", listOf(dish("米饭"), dish("配菜", listOf(dish("蔬菜")))))
        val meal = MealRecognition(true, "午餐", listOf(parent), 1.0, emptyList())
        val split = meal.splitDishes()
        assertEquals(listOf("套餐", "米饭", "配菜", "蔬菜"), split.dishes.map { it.name })
        assertEquals(parent.grams, split.dishes.sumOf { it.grams }, 0.0)
        assertEquals(meal.nutrition, split.nutrition)
        assertEquals(meal.nutritionMin, split.nutritionMin)
        assertEquals(meal.nutritionMax, split.nutritionMax)
        assertEquals(split, split.splitDishes())

        val containerOnly = meal.copy(dishes = listOf(parent.copy(components = emptyList())))
        assertEquals(listOf("米饭", "配菜", "蔬菜"), containerOnly.splitDishes().dishes.map { it.name })
        assertEquals(containerOnly.nutrition, containerOnly.splitDishes().nutrition)
    }

    @Test fun scalesSharedMealToSingleServing() {
        val component = FoodComponent(
            id = "rice",
            name = "米饭",
            databaseQuery = "rice",
            source = ComponentSource.USER_PROVIDED,
            estimatedWeightG = 200.0,
            weightMinG = 160.0,
            weightMaxG = 240.0,
            confidence = 1.0,
            nutritionReference = NutritionReference(
                "rice",
                "rice",
                "test",
                Nutrition(130.0, 2.7, 28.0, 0.3),
            ),
        )
        val meal = MealRecognition(
            isFoodImage = true,
            mealName = "番茄牛肉饭",
            dishes = listOf(
                RecognizedDish(
                    id = "dish",
                    name = "番茄牛肉饭",
                    type = DishType.STAPLE_WITH_TOPPINGS,
                    confidence = 1.0,
                    components = listOf(component),
                ),
            ),
            overallConfidence = 1.0,
            confirmationQuestions = emptyList(),
        )

        val scaled = meal.scaledForServing(2.0)

        assertEquals(100.0, scaled.dishes.single().grams, 0.0)
        assertEquals(80.0, scaled.dishes.single().allComponents.single().weightMinG, 0.0)
        assertEquals(120.0, scaled.dishes.single().allComponents.single().weightMaxG, 0.0)
        assertEquals(meal.nutrition * 0.5, scaled.nutrition)
    }
}
