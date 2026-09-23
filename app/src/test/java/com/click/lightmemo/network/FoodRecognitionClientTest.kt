package com.click.lightmemo.network

import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.domain.NutritionReference
import java.net.SocketException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodRecognitionClientTest {
    private val client = FoodRecognitionClient()

    @Test
    fun lowLevelConnectionAbortGetsActionableMessage() {
        val message = userFacingRecognitionError(SocketException("Software caused connection abort"))

        assertFalse(message.contains("Software caused", ignoreCase = true))
        assertTrue(message.contains("网络连接被中断"))
        assertTrue(message.contains("代理"))
    }

    @Test
    fun httpErrorsExplainTheLikelyConfigurationProblem() {
        assertEquals("识别服务鉴权失败：请检查 API Key 是否正确。", recognitionHttpErrorMessage(401))
        assertEquals("图片或请求内容过大，请换一张更小的图片。", recognitionHttpErrorMessage(413))
        assertEquals("识别服务暂时不可用（HTTP 503），请稍后重试。", recognitionHttpErrorMessage(503))
    }

    @Test
    fun promptTestRejectsInvalidWeightsAndSourcesInsteadOfClamping() {
        val valid = """{"is_food_image":true,"overall_confidence":0.9,"dishes":[{"dish_name":"米饭","dish_type":"single_food","components":[{"name":"熟米饭","database_query":"rice cooked","china_database_query":"米饭","source":"user_provided","estimated_weight_g":200,"weight_min_g":200,"weight_max_g":200}]}]}"""
        client.validatePromptResponse(RecognitionPrompt.TEXT, valid)
        listOf(
            valid.replace("\"weight_min_g\":200", "\"weight_min_g\":300"),
            valid.replace("user_provided", "visible"),
            valid.replace("\"overall_confidence\":0.9", "\"overall_confidence\":2"),
            valid.replace("\"estimated_weight_g\":200", "\"estimated_weight_g\":199"),
            "```json\n$valid\n```",
        ).forEach { invalid ->
            assertTrue(runCatching { client.validatePromptResponse(RecognitionPrompt.TEXT, invalid) }.isFailure)
        }
    }

    @Test
    fun portionPromptTestChecksMultiplicationAndNumericType() {
        client.validatePromptResponse(RecognitionPrompt.PORTION, """{"estimated_weight_g":300}""")
        listOf("""{"estimated_weight_g":150}""", """{"estimated_weight_g":"300"}""", "{}").forEach {
            assertTrue(runCatching { client.validatePromptResponse(RecognitionPrompt.PORTION, it) }.isFailure)
        }
    }

    @Test
    fun nutritionEstimateIsParsedAsPer100gValues() {
        val nutrition = client.parseNutritionEstimate(
            """{"calories_kcal_per_100g":218.5,"protein_g_per_100g":7.2,"carbs_g_per_100g":31.0,"fat_g_per_100g":8.4}""",
        )

        assertEquals(218.5, nutrition.caloriesKcal, 0.01)
        assertEquals(7.2, nutrition.proteinG, 0.01)
        assertEquals(31.0, nutrition.carbsG, 0.01)
        assertEquals(8.4, nutrition.fatG, 0.01)
    }

    @Test
    fun nutritionEstimateRejectsNegativeValues() {
        assertTrue(
            runCatching {
                client.parseNutritionEstimate(
                    """{"calories_kcal_per_100g":-1,"protein_g_per_100g":0,"carbs_g_per_100g":0,"fat_g_per_100g":0}""",
                )
            }.isFailure,
        )
    }

    @Test
    fun savedPromptIsUsedForRecognitionButDraftIsUsedForTest() = kotlinx.coroutines.runBlocking {
        val prompts = mutableListOf<String>()
        val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = okio.Buffer()
            chain.request().body!!.writeTo(buffer)
            val request = kotlinx.serialization.json.Json.decodeFromString<ChatRequest>(buffer.readUtf8())
            prompts += request.messages.first().content.first().text!!
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(okhttp3.ResponseBody.create(null, """{"choices":[{"message":{"content":"{\"estimated_weight_g\":300}"}}]}"""))
                .build()
        }.build()
        val testClient = FoodRecognitionClient(httpClient = http, promptOverrides = { mapOf("PORTION" to "saved prompt") })
        testClient.estimatePortionGrams("https://example.test/v1", "test", "test", "米饭", 2.0)
        testClient.testPrompt("https://example.test/v1", "test", "test", RecognitionPrompt.PORTION, "draft prompt")
        assertEquals(listOf("saved prompt", "draft prompt"), prompts)
        assertEquals(RecognitionPrompt.TEXT.defaultText, RecognitionPrompt.TEXT.resolve(mapOf("TEXT" to " ")))
    }

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
