package com.click.lightmemo.network

import com.click.lightmemo.domain.ComponentSource
import com.click.lightmemo.domain.DishType
import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.domain.MealRecognition
import com.click.lightmemo.domain.RecognizedDish
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

class FoodRecognitionClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
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
                textMessage(
                    "system",
                    "将食物名称转换成适合 USDA FoodData Central 检索的简洁英文词组，包含生熟和烹饪方式。只输出 {\"database_query\":\"...\"}。不要输出营养值。",
                ),
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
                textMessage(
                    "system",
                    """
                    你是食物估重助手。根据份数估计可食用重量（克），合理考虑常见盛装量。
                    只输出合法 JSON：{"estimated_weight_g": 数字}
                    """.trimIndent(),
                ),
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
                textMessage("system", TEXT_SYSTEM_PROMPT),
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

    suspend fun recognize(
        baseUrl: String,
        apiKey: String,
        model: String,
        imageBase64: String,
        mimeType: String = "image/jpeg",
        userDescription: String = "",
        mealType: String = "",
        plateSize: String = "",
    ): MealRecognition = withContext(Dispatchers.IO) {
        validate(baseUrl, apiKey, imageBase64)
        val dataUrl = "data:$mimeType;base64,$imageBase64"
        val initialContent = complete(
            baseUrl,
            apiKey,
            model,
            listOf(
                textMessage("system", SYSTEM_PROMPT),
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
            val reviewed = complete(
                baseUrl,
                apiKey,
                model,
                listOf(
                    textMessage("system", REVIEW_PROMPT),
                    ChatMessage(
                        "user",
                        listOf(
                            ContentPart(type = "text", text = "待审核结果：$initialJson"),
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

    private suspend fun complete(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
    ): String {
        val body = json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(model = model.ifBlank { "gpt-4o-mini" }, messages = messages),
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
                throw RecognitionException("识别失败 HTTP ${response.code}: ${raw.take(200)}")
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

    private companion object {
        val SYSTEM_PROMPT = """
            你是专业的食物视觉识别系统。你只负责看图、分层和估重；营养数据库负责计算。
            严格执行：图片质量检查 → 完整菜品识别 → 组成拆解 → 重量范围估计。
            一级必须是用户认知中的完整菜品。画面中若有多个独立菜品/盘子，必须分别作为独立 dish 列出，禁止合并成一道。
            套餐放在一级，子餐品放 children，不得把全部原料平铺。
            只拆对营养有明显影响的组成。油、酱汁、糖、奶油、芝士、汤底等可合理推测，但 source 必须为 inferred。
            每个组成提供适合 USDA FoodData Central 检索的简洁英文 database_query，需包含生熟状态和烹饪方式。
            禁止输出或猜测任何热量、蛋白质、碳水、脂肪数值。重量使用合理整值并给上下界。
            低置信度时使用更宽泛名称。最多提出 3 个真正影响热量的确认问题。
            只输出合法 JSON，不要 Markdown：
            {"is_food_image":true,"meal_name":"午餐","overall_confidence":0.82,
             "image_quality_issues":[],"confirmation_questions":[],"dishes":[{
             "dish_name":"牛肉盖饭","dish_type":"staple_with_toppings","dish_confidence":0.9,
             "needs_confirmation":true,"uncertainty_reason":"油量不可见","children":[],"components":[{
             "name":"熟白米饭","database_query":"rice white cooked","china_database_query":"米饭（蒸）","source":"visible",
             "estimated_weight_g":200,"weight_min_g":160,"weight_max_g":240,
             "confidence":0.9,"needs_confirmation":false}]}]}
            dish_type 只能为 single_food、mixed_dish、staple_with_toppings、soup_or_noodle、salad、
            sandwich_or_burger、combo_meal、beverage、dessert、other。
            source 只能为 visible、inferred、user_provided。
            china_database_query 应是适合《中国食物成分表》检索的简洁中文标准食物名，保留关键烹饪状态。
        """.trimIndent()

        val REVIEW_PROMPT = """
            你是食物视觉识别质量审核模块。对照原图审核给定结果，只在有充分视觉依据时修正。
            检查漏菜、层级、重复、不可食部分、烹饪方式、相对重量、餐具体积、隐藏油酱和过度具体判断。
            若画面中有多道独立菜品，必须分别作为独立 dish 列出，禁止合并成一道。
            输出与输入完全相同的合法 JSON 结构；不要输出审核说明、营养值或 Markdown。
        """.trimIndent()

        val TEXT_SYSTEM_PROMPT = """
            你是专业的食物识别系统。根据文字描述拆出完整菜品与组成，并估计可食用重量。
            一级必须是用户认知中的完整菜品。多道菜分别作为独立 dish，禁止合并。
            只拆对营养有明显影响的组成。油、酱汁、糖等可合理推测，source 为 inferred。
            database_query 使用适合 USDA 检索的简洁英文（含生熟与烹饪方式）。
            禁止输出或猜测热量、蛋白质、碳水、脂肪。重量给合理整值并带上下界。
            只输出合法 JSON，不要 Markdown：
            {"is_food_image":true,"meal_name":"文字识别","overall_confidence":0.8,
             "image_quality_issues":[],"confirmation_questions":[],"dishes":[{
             "dish_name":"红烧豆腐","dish_type":"mixed_dish","dish_confidence":0.85,
             "needs_confirmation":false,"uncertainty_reason":null,"children":[],"components":[{
             "name":"北豆腐","database_query":"tofu firm","china_database_query":"北豆腐",
             "source":"visible","estimated_weight_g":120,"weight_min_g":90,"weight_max_g":150,
             "confidence":0.8,"needs_confirmation":false}]}]}
            dish_type 只能为 single_food、mixed_dish、staple_with_toppings、soup_or_noodle、salad、
            sandwich_or_burger、combo_meal、beverage、dessert、other。
            source 只能为 visible、inferred、user_provided。
        """.trimIndent()
    }
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
