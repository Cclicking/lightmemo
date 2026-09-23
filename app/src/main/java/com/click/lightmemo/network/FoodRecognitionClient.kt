package com.click.lightmemo.network

import com.click.lightmemo.data.DEFAULT_API_MODEL
import com.click.lightmemo.domain.ComponentSource
import com.click.lightmemo.domain.DishType
import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.domain.MealRecognition
import com.click.lightmemo.domain.RecognizedDish
import com.click.lightmemo.domain.RecognitionStage
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RecognitionException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.1,
    val max_tokens: Int = 3000,
)

@Serializable
data class ChatMessage(val role: String, val content: List<ContentPart>)

@Serializable
data class ContentPart(
    val type: String,
    val text: String? = null,
    val image_url: ImageUrl? = null,
)

@Serializable
data class ImageUrl(val url: String)

@Serializable
internal data class VisualMealDto(
    @SerialName("is_food_image") val isFoodImage: Boolean = false,
    @SerialName("meal_name") val mealName: String = "餐食",
    val dishes: List<VisualDishDto> = emptyList(),
    @SerialName("overall_confidence") val overallConfidence: Double = 0.0,
    @SerialName("confirmation_questions") val confirmationQuestions: List<String> = emptyList(),
    @SerialName("image_quality_issues") val imageQualityIssues: List<String> = emptyList(),
)

@Serializable
internal data class VisualDishDto(
    @SerialName("dish_name") val dishName: String,
    @SerialName("dish_type") val dishType: String = "other",
    @SerialName("dish_confidence") val dishConfidence: Double = 0.0,
    val components: List<VisualComponentDto> = emptyList(),
    val children: List<VisualDishDto> = emptyList(),
    @SerialName("needs_confirmation") val needsConfirmation: Boolean = false,
    @SerialName("uncertainty_reason") val uncertaintyReason: String? = null,
)

@Serializable
internal data class VisualComponentDto(
    val name: String,
    @SerialName("database_query") val databaseQuery: String,
    @SerialName("china_database_query") val chinaDatabaseQuery: String? = null,
    val source: String = "visible",
    @SerialName("estimated_weight_g") val estimatedWeightG: Double,
    @SerialName("weight_min_g") val weightMinG: Double,
    @SerialName("weight_max_g") val weightMaxG: Double,
    val confidence: Double = 0.0,
    @SerialName("needs_confirmation") val needsConfirmation: Boolean = false,
)

@Serializable
internal data class NutritionEstimateDto(
    @SerialName("calories_kcal_per_100g") val caloriesKcalPer100g: Double,
    @SerialName("protein_g_per_100g") val proteinGPer100g: Double,
    @SerialName("carbs_g_per_100g") val carbsGPer100g: Double,
    @SerialName("fat_g_per_100g") val fatGPer100g: Double,
)

class FoodRecognitionClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val promptOverrides: suspend () -> Map<String, String> = { emptyMap() },
) {
    suspend fun normalizeFoodQuery(
        baseUrl: String,
        apiKey: String,
        model: String,
        foodName: String,
    ): String = withContext(Dispatchers.IO) {
        val content = complete(
            baseUrl,
            apiKey,
            model,
            listOf(
                promptMessage(RecognitionPrompt.NORMALIZE),
                textMessage("user", foodName),
            ),
        )
        json.parseToJsonElement(extractJson(content)).jsonObject["database_query"]
            ?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: throw RecognitionException("无法标准化食物名称")
    }

    /** 将「份数 + 食物名」交给大模型估计可食用克重。 */
    suspend fun estimatePortionGrams(
        baseUrl: String,
        apiKey: String,
        model: String,
        foodName: String,
        portions: Double,
        portionHint: String = "",
    ): Double = withContext(Dispatchers.IO) {
        validateConfig(baseUrl, apiKey)
        val content = complete(
            baseUrl,
            apiKey,
            model,
            listOf(
                promptMessage(RecognitionPrompt.PORTION),
                textMessage(
                    "user",
                    "食物：$foodName\n份数：$portions 份\n补充说明：${portionHint.ifBlank { "无" }}",
                ),
            ),
        )
        json.parseToJsonElement(extractJson(content)).jsonObject["estimated_weight_g"]
            ?.jsonPrimitive?.content?.toDoubleOrNull()
            ?.takeIf { it > 0.0 }
            ?: throw RecognitionException("无法估计该份数对应的克重")
    }

    /** Estimates nutrition per 100g for a food that is absent from the bundled databases. */
    suspend fun estimateNutrition(
        baseUrl: String,
        apiKey: String,
        model: String,
        foodName: String,
        estimatedWeightG: Double,
    ): com.click.lightmemo.domain.Nutrition = withContext(Dispatchers.IO) {
        validateConfig(baseUrl, apiKey)
        if (foodName.isBlank()) throw RecognitionException("食物名称为空")
        if (!estimatedWeightG.isFinite() || estimatedWeightG <= 0.0) {
            throw RecognitionException("食物重量无效")
        }
        val content = complete(
            baseUrl,
            apiKey,
            model,
            listOf(
                promptMessage(RecognitionPrompt.NUTRITION_ESTIMATE),
                textMessage(
                    "user",
                    "食物名称：${foodName.trim()}\n当前可食用重量：${estimatedWeightG.formatForPrompt()}克",
                ),
            ),
        )
        parseNutritionEstimate(extractJson(content))
    }

    /** 纯文字快速识别：根据描述拆出完整菜品与组成，并估计克重。 */
    suspend fun recognizeFromText(
        baseUrl: String,
        apiKey: String,
        model: String,
        text: String,
        userDescription: String = "",
        mealType: String = "",
        plateSize: String = "",
    ): MealRecognition = withContext(Dispatchers.IO) {
        validateConfig(baseUrl, apiKey)
        if (text.isBlank()) throw RecognitionException("请输入要识别的食物")
        val content = complete(
            baseUrl,
            apiKey,
            model,
            listOf(
                promptMessage(RecognitionPrompt.TEXT),
                textMessage(
                    "user",
                    """
                    食物描述：$text
                    餐具尺寸：${plateSize.ifBlank { "未提供" }}
                    用户补充说明：${userDescription.ifBlank { "未提供" }}
                    用餐类型：${mealType.ifBlank { "未提供" }}
                    严格按系统 JSON 结构输出，不要 Markdown。
                    """.trimIndent(),
                ),
            ),
        )
        parseVisualJson(extractJson(content))
    }

    private fun validateConfig(baseUrl: String, apiKey: String) {
        if (baseUrl.isBlank() || apiKey.isBlank()) throw RecognitionException("请先配置识别 API")
    }

    /** Tests the draft directly, bypassing saved overrides and production's tolerant parser. */
    suspend fun testPrompt(
        baseUrl: String,
        apiKey: String,
        model: String,
        kind: RecognitionPrompt,
        draft: String,
        imageBase64: String? = null,
    ) = withContext(Dispatchers.IO) {
        validateConfig(baseUrl, apiKey)
        require(draft.isNotBlank()) { "Prompt 不能为空" }
        require(!kind.requiresImage || !imageBase64.isNullOrBlank()) { "图片测试需要食物照片" }
        val image = imageBase64?.let { ContentPart("image_url", image_url = ImageUrl("data:image/jpeg;base64,$it")) }
        val input = when (kind) {
            RecognitionPrompt.TEXT -> "200克熟白米饭，不加油，不含其他食物。"
            RecognitionPrompt.NORMALIZE -> "熟白米饭"
            RecognitionPrompt.PORTION -> "食物：熟白米饭\n份数：2份\n补充说明：每份可食用重量150克"
            RecognitionPrompt.NUTRITION_ESTIMATE -> "食物名称：熟白米饭\n当前可食用重量：200克"
            RecognitionPrompt.IMAGE -> runtimePrompt("", "", "")
            RecognitionPrompt.REVIEW -> {
                val baseline = complete(baseUrl, apiKey, model, listOf(
                    textMessage("system", RecognitionPrompt.IMAGE.defaultText),
                    ChatMessage("user", listOfNotNull(ContentPart("text", text = runtimePrompt("", "", "")), image)),
                ))
                validatePromptResponse(RecognitionPrompt.IMAGE, baseline)
                "待审核结果：$baseline"
            }
        }
        val response = complete(baseUrl, apiKey, model, listOf(
            textMessage("system", draft),
            ChatMessage("user", listOfNotNull(ContentPart("text", text = input), image.takeIf { kind.requiresImage })),
        ))
        validatePromptResponse(kind, response)
        response
    }

    internal fun validatePromptResponse(kind: RecognitionPrompt, response: String) {
        val strict = Json { ignoreUnknownKeys = false }
        val root = strict.parseToJsonElement(response).jsonObject
        when (kind) {
            RecognitionPrompt.NORMALIZE -> {
                val query = root["database_query"]?.jsonPrimitive
                require(root.keys == setOf("database_query") && query?.isString == true &&
                    query.content.contains("rice", ignoreCase = true) && query.content.contains("cooked", ignoreCase = true)) {
                    "名称标准化结果缺少有效的熟米饭英文检索词"
                }
            }
            RecognitionPrompt.PORTION -> {
                val value = root["estimated_weight_g"]?.jsonPrimitive
                val grams = value?.content?.toDoubleOrNull()
                require(root.keys == setOf("estimated_weight_g") && value?.isString == false &&
                    grams != null && grams.isFinite() && kotlin.math.abs(grams - 300.0) < 0.01) {
                    "估重测试应返回数字300克（2份 × 150克）"
                }
            }
            RecognitionPrompt.NUTRITION_ESTIMATE -> {
                val value = strict.decodeFromString<NutritionEstimateDto>(response)
                require(
                    root.keys == setOf(
                        "calories_kcal_per_100g",
                        "protein_g_per_100g",
                        "carbs_g_per_100g",
                        "fat_g_per_100g",
                    ) && value.caloriesKcalPer100g.isFinite() && value.caloriesKcalPer100g >= 0.0 &&
                        value.proteinGPer100g.isFinite() && value.proteinGPer100g >= 0.0 &&
                        value.carbsGPer100g.isFinite() && value.carbsGPer100g >= 0.0 &&
                        value.fatGPer100g.isFinite() && value.fatGPer100g >= 0.0,
                ) { "营养估算值必须是非负数字" }
            }
            else -> {
                require(root.keys.containsAll(listOf("is_food_image", "dishes", "overall_confidence"))) { "缺少餐食必要字段" }
                val meal = strict.decodeFromString<VisualMealDto>(response)
                require(meal.isFoodImage && meal.dishes.isNotEmpty()) { "未识别到测试食物，请检查 Prompt 或照片" }
                require(meal.overallConfidence.isFinite() && meal.overallConfidence in 0.0..1.0) { "餐食置信度无效" }
                require(meal.confirmationQuestions.size <= 3) { "确认问题不能超过3个" }
                fun validateDish(dish: VisualDishDto): List<VisualComponentDto> {
                    require(dish.dishName.isNotBlank() && DishType.entries.any { it.name.equals(dish.dishType, true) }) { "菜品名称或类型无效" }
                    require(dish.dishConfidence.isFinite() && dish.dishConfidence in 0.0..1.0) { "菜品置信度无效" }
                    require(dish.children.isEmpty() || dish.components.isEmpty()) { "父菜品与子菜品重复计重" }
                    val components = dish.components + dish.children.flatMap { validateDish(it) }
                    require(components.isNotEmpty()) { "菜品缺少组成" }
                    components.forEach { c ->
                        require(c.name.isNotBlank() && c.databaseQuery.isNotBlank() && !c.chinaDatabaseQuery.isNullOrBlank()) { "组成缺少名称或中英文检索词" }
                        require(ComponentSource.entries.any { it.name.equals(c.source, true) }) { "组成来源无效" }
                        require(kind != RecognitionPrompt.TEXT || c.source != "visible") { "文字识别不能使用 visible 来源" }
                        require(c.confidence.isFinite() && c.confidence in 0.0..1.0) { "组成置信度无效" }
                        require(c.estimatedWeightG.isFinite() && c.weightMinG.isFinite() && c.weightMaxG.isFinite() &&
                            c.weightMinG > 0 && c.weightMinG <= c.estimatedWeightG && c.estimatedWeightG <= c.weightMaxG) { "组成重量或上下界无效" }
                    }
                    return components
                }
                val components = meal.dishes.flatMap { validateDish(it) }
                if (kind == RecognitionPrompt.TEXT) {
                    require(meal.dishes.size == 1 && components.size == 1 &&
                        components.single().databaseQuery.contains("rice", true) &&
                        kotlin.math.abs(components.sumOf { it.estimatedWeightG } - 200.0) < 0.01) { "文字测试必须保持200克米饭，不能新增配料或重复计重" }
                }
            }
        }
    }

    suspend fun recognize(
        baseUrl: String,
        apiKey: String,
        model: String,
        imageBase64: String,
        mimeType: String = "image/jpeg",
        userDescription: String = "",
        mealType: String = "",
        plateSize: String = "",
        onStage: suspend (RecognitionStage) -> Unit = {},
    ): MealRecognition = withContext(Dispatchers.IO) {
        validate(baseUrl, apiKey, imageBase64)
        onStage(RecognitionStage.RECOGNIZING)
        val dataUrl = "data:$mimeType;base64,$imageBase64"
        val initialContent = complete(
            baseUrl,
            apiKey,
            model,
            listOf(
                promptMessage(RecognitionPrompt.IMAGE),
                ChatMessage(
                    "user",
                    listOf(
                        ContentPart(type = "text", text = runtimePrompt(plateSize, userDescription, mealType)),
                        ContentPart(type = "image_url", image_url = ImageUrl(dataUrl)),
                    ),
                ),
            ),
        )
        val initialJson = extractJson(initialContent)
        val initial = parseVisualJson(initialJson)
        if (!initial.isFoodImage || initial.dishes.isEmpty()) return@withContext initial

        // 审核服务失败时保留第一阶段结果，营养查询仍可继续。
        runCatching {
            onStage(RecognitionStage.REVIEWING)
            val reviewed = complete(
                baseUrl,
                apiKey,
                model,
                listOf(
                    promptMessage(RecognitionPrompt.REVIEW),
                    ChatMessage(
                        "user",
                        listOf(
                            ContentPart(
                                type = "text",
                                text = runtimePrompt(plateSize, userDescription, mealType) + "\n待审核结果：$initialJson",
                            ),
                            ContentPart(type = "image_url", image_url = ImageUrl(dataUrl)),
                        ),
                    ),
                ),
            )
            parseVisualJson(extractJson(reviewed))
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            initial.copy(imageQualityIssues = initial.imageQualityIssues + "二次审核未完成，请仔细确认食物和重量")
        }
    }

    internal fun parseChatCompletion(raw: String): MealRecognition = try {
        parseVisualJson(extractJson(chatContent(raw)))
    } catch (e: RecognitionException) {
        throw e
    } catch (e: Exception) {
        throw RecognitionException("无法解析识别结果", e)
    }

    internal fun parseVisualJson(payload: String): MealRecognition = try {
        json.decodeFromString<VisualMealDto>(payload).toDomain()
    } catch (e: Exception) {
        throw RecognitionException("视觉模型返回的结构不完整", e)
    }

    internal fun parseNutritionEstimate(payload: String): com.click.lightmemo.domain.Nutrition = try {
        val value = json.decodeFromString<NutritionEstimateDto>(payload)
        require(
            value.caloriesKcalPer100g.isFinite() && value.caloriesKcalPer100g >= 0.0 &&
                value.proteinGPer100g.isFinite() && value.proteinGPer100g >= 0.0 &&
                value.carbsGPer100g.isFinite() && value.carbsGPer100g >= 0.0 &&
                value.fatGPer100g.isFinite() && value.fatGPer100g >= 0.0,
        )
        com.click.lightmemo.domain.Nutrition(
            caloriesKcal = value.caloriesKcalPer100g,
            proteinG = value.proteinGPer100g,
            carbsG = value.carbsGPer100g,
            fatG = value.fatGPer100g,
        )
    } catch (e: Exception) {
        throw RecognitionException("营养估算结果无效", e)
    }

    private suspend fun complete(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
    ): String {
        val body = json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(model = model.ifBlank { DEFAULT_API_MODEL }, messages = messages),
        )
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(request).awaitResponse().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw RecognitionException(recognitionHttpErrorMessage(response.code))
            }
            return chatContent(raw)
        }
    }

    private fun chatContent(raw: String): String = try {
        json.parseToJsonElement(raw).jsonObject["choices"]!!.jsonArray[0]
            .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
    } catch (e: Exception) {
        throw RecognitionException("接口响应中没有有效内容", e)
    }

    private fun validate(baseUrl: String, apiKey: String, imageBase64: String) {
        validateConfig(baseUrl, apiKey)
        if (imageBase64.isBlank()) throw RecognitionException("图片数据为空")
    }

    private suspend fun promptMessage(kind: RecognitionPrompt) =
        textMessage("system", kind.resolve(promptOverrides()))

    private fun textMessage(role: String, text: String) =
        ChatMessage(role, listOf(ContentPart(type = "text", text = text)))

    private fun extractJson(content: String): String {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) throw IOException("响应中没有 JSON 对象")
        return content.substring(start, end + 1)
    }

    private fun runtimePrompt(plateSize: String, userDescription: String, mealType: String) = """
        分析这张食物照片。先识别完整菜品，再拆解主要组成并估计可食用重量。
        餐具尺寸：${plateSize.ifBlank { "未提供" }}
        用户补充说明：${userDescription.ifBlank { "未提供" }}
        用餐类型：${mealType.ifBlank { "未提供" }}
        空缺信息仅根据图片判断，不要擅自当作已知事实。严格返回系统规定的 JSON。
    """.trimIndent()

    private fun Double.formatForPrompt(): String =
        if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(java.util.Locale.ROOT, this)


}

private fun VisualMealDto.toDomain() = MealRecognition(
    isFoodImage = isFoodImage,
    mealName = mealName,
    dishes = dishes.map { it.toDomain() },
    overallConfidence = overallConfidence.coerceIn(0.0, 1.0),
    confirmationQuestions = confirmationQuestions.take(3),
    imageQualityIssues = imageQualityIssues,
)

private fun VisualDishDto.toDomain(): RecognizedDish = RecognizedDish(
    id = UUID.randomUUID().toString(),
    name = dishName,
    type = runCatching { DishType.valueOf(dishType.uppercase()) }.getOrDefault(DishType.OTHER),
    confidence = dishConfidence.coerceIn(0.0, 1.0),
    components = components.map { it.toDomain() },
    needsConfirmation = needsConfirmation,
    uncertaintyReason = uncertaintyReason,
    children = children.map { it.toDomain() },
)

private fun VisualComponentDto.toDomain(): FoodComponent {
    val estimate = estimatedWeightG.coerceAtLeast(0.0)
    return FoodComponent(
        id = UUID.randomUUID().toString(),
        name = name,
        databaseQuery = databaseQuery,
        chinaDatabaseQuery = chinaDatabaseQuery?.takeIf { it.isNotBlank() } ?: name,
        source = runCatching { ComponentSource.valueOf(source.uppercase()) }.getOrDefault(ComponentSource.INFERRED),
        estimatedWeightG = estimate,
        weightMinG = weightMinG.coerceIn(0.0, estimate),
        weightMaxG = weightMaxG.coerceAtLeast(estimate),
        confidence = confidence.coerceIn(0.0, 1.0),
        needsConfirmation = needsConfirmation,
    )
}
