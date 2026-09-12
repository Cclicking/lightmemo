package com.foodcalorie.app.network

import com.foodcalorie.app.domain.Nutrition
import com.foodcalorie.app.domain.RecognizedFood
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
    val temperature: Double = 0.2,
    val max_tokens: Int = 800,
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: List<ContentPart>,
)

@Serializable
data class ContentPart(
    val type: String,
    val text: String? = null,
    val image_url: ImageUrl? = null,
)

@Serializable
data class ImageUrl(val url: String)

class FoodRecognitionClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    suspend fun recognize(
        baseUrl: String,
        apiKey: String,
        model: String,
        imageBase64: String,
        mimeType: String = "image/jpeg",
    ): List<RecognizedFood> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw RecognitionException("请先在设置中填写 API Base URL 与 API Key")
        }
        if (imageBase64.isBlank()) {
            throw RecognitionException("图片数据为空")
        }
        val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
        val dataUrl = "data:$mimeType;base64,$imageBase64"
        val prompt = """
            你是营养分析助手。识别图片中的食物，并估算整份摄入的营养。
            只输出 JSON，不要 markdown，格式：
            {"items":[{"name":"食物名","grams":123.0,"caloriesKcal":200.0,"proteinG":10.0,"carbsG":20.0,"fatG":5.0}]}
            grams 为估算克数；热量单位 kcal；蛋白质/碳水/脂肪单位 g。若图中无食物，items 为空数组。
        """.trimIndent()
        val bodyObj = ChatRequest(
            model = model.ifBlank { "gpt-4o-mini" },
            messages = listOf(
                ChatMessage(
                    role = "system",
                    content = listOf(ContentPart(type = "text", text = "You output strict JSON only.")),
                ),
                ChatMessage(
                    role = "user",
                    content = listOf(
                        ContentPart(type = "text", text = prompt),
                        ContentPart(type = "image_url", image_url = ImageUrl(url = dataUrl)),
                    ),
                ),
            ),
        )
        val bodyText = json.encodeToString(ChatRequest.serializer(), bodyObj)
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(bodyText.toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw RecognitionException("识别失败 HTTP ${response.code}: ${raw.take(200)}")
        }
        parseChatCompletion(raw)
    }

    internal fun parseChatCompletion(raw: String): List<RecognizedFood> {
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            val content = root["choices"]!!.jsonArray[0]
                .jsonObject["message"]!!
                .jsonObject["content"]!!
                .jsonPrimitive.content
            parseItemsJson(extractJson(content))
        } catch (e: RecognitionException) {
            throw e
        } catch (e: Exception) {
            throw RecognitionException("无法解析识别结果", e)
        }
    }

    internal fun parseItemsJson(payload: String): List<RecognizedFood> {
        val root = json.parseToJsonElement(payload).jsonObject
        val items = root["items"] as? JsonArray ?: JsonArray(emptyList())
        return items.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (name.isBlank()) return@mapNotNull null
            RecognizedFood(
                name = name,
                grams = obj.double("grams"),
                nutrition = Nutrition(
                    caloriesKcal = obj.double("caloriesKcal"),
                    proteinG = obj.double("proteinG"),
                    carbsG = obj.double("carbsG"),
                    fatG = obj.double("fatG"),
                ),
            )
        }
    }

    private fun JsonObject.double(key: String): Double {
        val prim = this[key] as? JsonPrimitive ?: return 0.0
        return prim.contentOrNull?.toDoubleOrNull() ?: 0.0
    }

    private fun extractJson(content: String): String {
        val trimmed = content.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw IOException("响应中没有 JSON 对象")
        }
        return trimmed.substring(start, end + 1)
    }
}
