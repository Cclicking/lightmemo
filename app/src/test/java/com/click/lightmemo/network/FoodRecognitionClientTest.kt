package com.click.lightmemo.network

import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.domain.NutritionReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodRecognitionClientTest {
    private val client = FoodRecognitionClient()

    @Test
    fun parsesHierarchicalVisualResultWithoutModelNutrition() {
        val payload = """
            {"is_food_image":true,"meal_name":"午餐","overall_confidence":0.82,
             "confirmation_questions":["酱汁多吗？"],"image_quality_issues":[],"dishes":[{
               "dish_name":"牛肉盖饭","dish_type":"staple_with_toppings","dish_confidence":0.91,
               "needs_confirmation":true,"uncertainty_reason":"油量不可见","children":[],
               "components":[{"name":"熟白米饭","database_query":"rice white cooked","china_database_query":"米饭（蒸）","source":"visible",
                 "estimated_weight_g":200,"weight_min_g":160,"weight_max_g":240,"confidence":0.9}]}]}
        """.trimIndent()
        val result = client.parseVisualJson(payload)

        assertTrue(result.isFoodImage)
        assertEquals("牛肉盖饭", result.dishes.single().name)
        assertEquals("rice white cooked", result.dishes.single().components.single().databaseQuery)
        assertEquals("米饭（蒸）", result.dishes.single().components.single().chinaDatabaseQuery)
        assertEquals(0.0, result.nutrition.caloriesKcal, 0.01)
    }

    @Test
    fun extractsJsonFromFencedChatContent() {
        val raw = """
            {"choices":[{"message":{"content":"```json\n{\"is_food_image\":false,\"meal_name\":\"餐食\",\"dishes\":[],\"overall_confidence\":0,\"confirmation_questions\":[]}\n```"}}]}
        """.trimIndent()
        val result = client.parseChatCompletion(raw)
        assertTrue(!result.isFoodImage)
    }

    @Test
    fun componentNutritionIsAlwaysCalculatedFromPer100gReference() {
        val payload = """
            {"is_food_image":true,"meal_name":"午餐","overall_confidence":0.9,
             "dishes":[{"dish_name":"米饭","dish_type":"single_food","dish_confidence":0.9,
             "components":[{"name":"熟白米饭","database_query":"rice cooked","source":"visible",
             "estimated_weight_g":180,"weight_min_g":150,"weight_max_g":210,"confidence":0.9}]}]}
        """.trimIndent()
        val component = client.parseVisualJson(payload).dishes.single().components.single().copy(
            nutritionReference = NutritionReference("1", "Rice, cooked", "SR Legacy", Nutrition(116.0, 2.6, 25.9, 0.3)),
        )

        assertEquals(208.8, component.nutrition.caloriesKcal, 0.01)
        assertEquals(174.0, component.nutritionMin.caloriesKcal, 0.01)
        assertEquals(243.6, component.nutritionMax.caloriesKcal, 0.01)
    }

    @Test
    fun multiDishRecognitionKeepsSeparateCards() {
        val payload = """
            {"is_food_image":true,"meal_name":"晚餐","overall_confidence":0.88,
             "dishes":[
               {"dish_name":"红烧豆腐","dish_type":"mixed_dish","dish_confidence":0.9,
                "components":[{"name":"北豆腐","database_query":"tofu firm","source":"visible",
                 "estimated_weight_g":120,"weight_min_g":90,"weight_max_g":150,"confidence":0.9}]},
               {"dish_name":"清炒时蔬","dish_type":"mixed_dish","dish_confidence":0.85,
                "components":[{"name":"西兰花","database_query":"broccoli cooked","source":"visible",
                 "estimated_weight_g":100,"weight_min_g":80,"weight_max_g":120,"confidence":0.85}]},
               {"dish_name":"米饭","dish_type":"single_food","dish_confidence":0.95,
                "components":[{"name":"熟白米饭","database_query":"rice white cooked","source":"visible",
                 "estimated_weight_g":150,"weight_min_g":120,"weight_max_g":180,"confidence":0.95}]}
             ]}
        """.trimIndent()

        val result = client.parseVisualJson(payload)
        assertEquals(3, result.dishes.size)
        assertEquals(listOf("红烧豆腐", "清炒时蔬", "米饭"), result.dishes.map { it.name })
    }
}
