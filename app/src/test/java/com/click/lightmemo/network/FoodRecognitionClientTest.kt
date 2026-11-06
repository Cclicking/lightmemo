package com.click.lightmemo.network

import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.domain.NutritionReference
import java.net.SocketException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodRecognitionClientTest {
    @Test fun officialFlashDisablesThinkingOnBothVisualRequestsWithoutSkippingReview() = kotlinx.coroutines.runBlocking {
        val requests = mutableListOf<ChatRequest>()
        imageClient(onRequest = { requests += it }).recognize("https://api.deepseek.com/v1", "key", "deepseek-flash", "image")
        assertEquals(2, requests.size)
        requests.forEach {
            assertEquals("disabled", it.thinking?.type)
            assertEquals(3000, it.max_tokens)
            assertEquals(0.1, it.temperature, 0.0)
            assertTrue(it.messages.any { message -> message.content.any { part -> part.image_url != null } })
        }
    }

    @Test fun wireParametersAreExplicitAndThinkingIsLimitedToOfficialFlash() = kotlinx.coroutines.runBlocking {
        val requests = mutableListOf<org.json.JSONObject>()
        val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = okio.Buffer()
            chain.request().body!!.writeTo(buffer)
            requests += org.json.JSONObject(buffer.readUtf8())
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK").body(okhttp3.ResponseBody.create(null,
                    """{"choices":[{"message":{"content":"OK"}}]}""")).build()
        }.build()
        val testClient = FoodRecognitionClient(httpClient = http)
        val cases = listOf(
            Triple("https://api.deepseek.com", "deepseek-flash", true),
            Triple("https://api.deepseek.com/v1", "deepseek-flash", true),
            Triple("https://example.test/v1", "deepseek-flash", false),
            Triple("https://api.deepseek.com.evil.test", "deepseek-flash", false),
            Triple("https://api.deepseek.com", "deepseek-reasoner", false),
            Triple("https://api.deepseek.com", "another-model", false),
        )
        cases.forEach { (url, model, disabled) ->
            testClient.testConnection(url, "key", model)
            val body = requests.last()
            assertEquals(0.1, body.getDouble("temperature"), 0.0)
            assertEquals(3000, body.getInt("max_tokens"))
            if (disabled) assertEquals("disabled", body.getJSONObject("thinking").getString("type"))
            else assertFalse(body.has("thinking"))
        }
    }

    @Test fun connectionTestUsesDraftAndRejectsEmptyRepliesAndAuthenticationFailures() = kotlinx.coroutines.runBlocking {
        var status = 200
        var content = "OK"
        var requestedUrl = ""
        var authorization = ""
        val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            requestedUrl = chain.request().url.toString()
            authorization = chain.request().header("Authorization").orEmpty()
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(status).message("test")
                .body(okhttp3.ResponseBody.create(null, """{"choices":[{"message":{"content":"$content"}}]}"""))
                .build()
        }.build()
        val testClient = FoodRecognitionClient(httpClient = http)
        testClient.testConnection(" https://example.test/v1/ ", " draft-key ", "draft-model")
        assertEquals("https://example.test/v1/chat/completions", requestedUrl)
        assertEquals("Bearer draft-key", authorization)
        content = ""
        try {
            testClient.testConnection("https://example.test/v1", "draft-key", "draft-model")
            org.junit.Assert.fail("Empty reply must fail")
        } catch (e: RecognitionException) { assertTrue(e.message.orEmpty().contains("空")) }
        status = 401
        try {
            testClient.testConnection("https://example.test/v1", "draft-key", "draft-model")
            org.junit.Assert.fail("Authentication failure must fail")
        } catch (e: RecognitionException) { assertTrue(e.message.orEmpty().contains("鉴权")) }
    }

    private val client = FoodRecognitionClient()

    private fun imageClient(
        promptOverrides: suspend () -> Map<String, String> = { emptyMap() },
        onRequest: (ChatRequest) -> Unit = {},
        payloadOverride: String? = null,
    ): FoodRecognitionClient {
        val payload = payloadOverride ?: """{"is_food_image":true,"meal_name":"午餐","overall_confidence":0.95,"confirmation_questions":[],"dishes":[{"dish_name":"米饭","dish_type":"single_food","dish_confidence":0.95,"components":[{"name":"米饭","database_query":"rice cooked","source":"visible","estimated_weight_g":200,"weight_min_g":180,"weight_max_g":220,"confidence":0.95}]}]}"""
        val body = kotlinx.serialization.json.buildJsonObject {
            put("choices", kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.buildJsonObject {
                    put("message", kotlinx.serialization.json.buildJsonObject {
                        put("content", kotlinx.serialization.json.JsonPrimitive(payload))
                    })
                })
            })
        }.toString()
        val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = okio.Buffer()
            chain.request().body!!.writeTo(buffer)
            onRequest(kotlinx.serialization.json.Json.decodeFromString<ChatRequest>(buffer.readUtf8()))
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK").body(okhttp3.ResponseBody.create(null, body)).build()
        }.build()
        return FoodRecognitionClient(httpClient = http, promptOverrides = promptOverrides)
    }

    private fun labelPayload(basis: Double = 100.0, unit: String = "kJ", weight: String = "250") =
        """{"nutrition_label":{"food_name":"饼干","basis_weight_g":$basis,"energy":836.8,"energy_unit":"$unit","protein_g":8,"carbs_g":30,"fat_g":6,"weight_g":$weight}}"""

    @Test fun nutritionLabelConvertsKilojoulesAndPackageWeightWithoutIngredients() {
        val result = imageClient().parseVisualJson(labelPayload())
        assertTrue(result.isNutritionLabel)
        assertTrue(result.isFoodImage)
        val dish = result.dishes.single()
        assertTrue(dish.allComponents.isEmpty())
        assertEquals(250.0, dish.grams, 0.001)
        assertEquals(500.0, result.nutrition.caloriesKcal, 0.001)
        assertEquals(20.0, result.nutrition.proteinG, 0.001)
        assertEquals(75.0, result.nutrition.carbsG, 0.001)
        assertEquals(15.0, result.nutrition.fatG, 0.001)
    }

    @Test fun perServingLabelKeepsNutritionWhenWeightIsMissingAndRecalculatesOnEntry() {
        val result = imageClient().parseVisualJson(labelPayload(basis = 40.0, unit = "kcal", weight = "null"))
        val dish = result.dishes.single()
        val label = requireNotNull(dish.nutritionLabel)
        assertEquals(0.0, dish.grams, 0.0)
        assertEquals(0.0, result.nutrition.caloriesKcal, 0.0)
        assertEquals(2092.0, label.per100g.caloriesKcal, 0.001)
        val weighed = dish.copy(nutritionLabel = label.copy(weightG = 80.0))
        assertEquals(1673.6, weighed.nutrition.caloriesKcal, 0.001)
        assertEquals(16.0, weighed.nutrition.proteinG, 0.001)
        val restored = kotlinx.serialization.json.Json.decodeFromString<com.click.lightmemo.domain.MealRecognition>(
            kotlinx.serialization.json.Json.encodeToString(com.click.lightmemo.domain.MealRecognition.serializer(), result),
        )
        assertEquals(result, restored)
    }

    @Test fun nutritionLabelSkipsReviewMemoryAndPrefetch() = kotlinx.coroutines.runBlocking {
        var calls = 0
        val result = imageClient(onRequest = { calls++ }, payloadOverride = labelPayload()).recognize(
            "https://example.test/v1", "test", "test", "image",
            fastResult = { error("Must not replace label with food memory") },
            onInitialResult = { error("Must not prefetch food database") },
        )
        assertEquals(1, calls)
        assertTrue(result.isNutritionLabel)
    }

    @Test fun nutritionLabelRejectsInvalidBasisUnitsAndNegativeNutrients() {
        listOf(
            labelPayload(basis = 0.0), labelPayload(unit = "%"),
            labelPayload().replace("\"protein_g\":8", "\"protein_g\":-8"),
        ).forEach { payload ->
            assertTrue(runCatching { imageClient().parseVisualJson(payload) }.exceptionOrNull() is RecognitionException)
        }
    }

    @Test fun fastPathStillCallsVisionButSkipsSecondModelReview() = kotlinx.coroutines.runBlocking {
        var calls = 0
        val stages = mutableListOf<com.click.lightmemo.domain.RecognitionStage>()
        val result = imageClient().recognize("https://example.test/v1", "test", "test", "image",
            onStage = { stages += it }, fastResult = { it.copy(confirmationQuestions = listOf("核对历史组成")) },
            onInitialResult = { error("Fast path must not start speculative matching") },
            onModelCall = { calls++ })
        assertEquals(1, calls)
        assertEquals(listOf(com.click.lightmemo.domain.RecognitionStage.RECOGNIZING), stages)
        assertEquals(listOf("核对历史组成"), result.confirmationQuestions)
    }

    @Test fun bothVisualCallsUseOnePromptSnapshotAndPrefetchStartsBeforeReview() = kotlinx.coroutines.runBlocking {
        var reads = 0
        var initialSeen = false
        val prompts = mutableListOf<String>()
        val testClient = imageClient(
            promptOverrides = { reads++; mapOf("IMAGE" to "custom image", "REVIEW" to "custom review") },
            onRequest = {
                if (prompts.isNotEmpty()) assertTrue(initialSeen)
                prompts += it.messages.first().content.first().text!!
            },
        )
        testClient.recognize("https://example.test/v1", "test", "test", "image", onInitialResult = { initialSeen = true })
        assertEquals(1, reads)
        assertEquals(listOf("custom image", "custom review"), prompts)
    }

    @Test fun explicitPromptSnapshotUsesTheTaskConfigurationWithoutRereadingSettings() = kotlinx.coroutines.runBlocking {
        val prompts = mutableListOf<String>()
        val testClient = imageClient(promptOverrides = { error("Task already captured settings") },
            onRequest = { prompts += it.messages.first().content.first().text!! })
        testClient.recognize("https://example.test/v1", "test", "test", "image",
            promptSnapshot = mapOf("IMAGE" to "image snapshot", "REVIEW" to "review snapshot"))
        assertEquals(listOf("image snapshot", "review snapshot"), prompts)
    }

    @Test fun fastMissContinuesOriginalTwoCallWorkflow() = kotlinx.coroutines.runBlocking {
        var calls = 0
        val stages = mutableListOf<com.click.lightmemo.domain.RecognitionStage>()
        val result = imageClient().recognize("https://example.test/v1", "test", "test", "image",
            onStage = { stages += it }, fastResult = { null }, onModelCall = { calls++ })
        assertEquals(2, calls)
        assertTrue(stages.contains(com.click.lightmemo.domain.RecognitionStage.REVIEWING))
        assertEquals("米饭", result.dishes.single().name)
    }

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
