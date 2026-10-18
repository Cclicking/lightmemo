package com.click.lightmemo.network

import com.click.lightmemo.data.NutritionDatabases
import com.click.lightmemo.domain.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

/** Opt-in only: the Python runner adds this and the actual Git baseline to test sources. */
class RecognitionSpeedBenchmark {
    private val assets = File(requireNotNull(System.getProperty("food.assets")))
    private val localSettings = NutritionDatabases(online = false)
    private fun current() = FoodDataCentralClient(
        openAsset = { File(assets, it).inputStream() }, listAssets = { assets.list().orEmpty() }, databases = { localSettings })
    private fun baseline() = BaselineFoodDataCentralClient(
        openAsset = { File(assets, it).inputStream() }, listAssets = { assets.list().orEmpty() }, databases = { localSettings })

    private data class Value(val fingerprint: String, val requests: Int = 0)
    private data class Sample(val ms: Double, val value: Value)
    private val cases = mutableListOf<JsonObject>()
    private fun List<Double>.median() = sorted()[size / 2]
    private fun List<Double>.p95() = sorted()[(ceil(size * 0.95).toInt() - 1).coerceIn(indices)]
    private suspend fun measure(action: suspend () -> Value): Sample {
        val started = System.nanoTime()
        val value = action()
        return Sample((System.nanoTime() - started) / 1_000_000.0, value)
    }

    private suspend fun compare(
        name: String, samples: Int,
        before: suspend () -> (suspend () -> Value), after: suspend () -> (suspend () -> Value),
    ) {
        val old = mutableListOf<Sample>()
        val new = mutableListOf<Sample>()
        repeat(samples + 2) { round ->
            // Factories/preload are outside timing. Cold scenarios intentionally initialize in the action.
            val beforeAction = before()
            val afterAction = after()
            val a: Sample
            val b: Sample
            if (round % 2 == 0) { a = measure(beforeAction); b = measure(afterAction) }
            else { b = measure(afterAction); a = measure(beforeAction) }
            assertEquals("Result parity: $name", a.value.fingerprint, b.value.fingerprint)
            if (round >= 2) { old += a; new += b }
        }
        val beforeTimes = old.map { it.ms }
        val afterTimes = new.map { it.ms }
        cases += buildJsonObject {
            put("name", name); put("samples", samples)
            put("beforeMedianMs", beforeTimes.median()); put("afterMedianMs", afterTimes.median())
            put("beforeP95Ms", beforeTimes.p95()); put("afterP95Ms", afterTimes.p95())
            put("beforeSamplesMs", JsonArray(beforeTimes.map(::JsonPrimitive)))
            put("afterSamplesMs", JsonArray(afterTimes.map(::JsonPrimitive)))
            put("beforeRequests", old.map { it.value.requests }.distinct().joinToString(","))
            put("afterRequests", new.map { it.value.requests }.distinct().joinToString(","))
        }
        println("$name: ${beforeTimes.median()}ms -> ${afterTimes.median()}ms")
    }

    private fun fingerprint(ref: NutritionReference?) = ref?.let { "${it.dataType}:${it.sourceId}:${it.per100g}" }.orEmpty()
    private fun fingerprint(meal: MealRecognition) = meal.dishes.flatMap { it.allComponents }.joinToString("|") {
        "${it.name}:${it.estimatedWeightG}:${fingerprint(it.nutritionReference)}:${it.hasNutritionCandidates}"
    }
    private fun meal(queries: List<String>) = MealRecognition(true, "测试餐", listOf(
        RecognizedDish("dish", "测试菜品", DishType.MIXED_DISH, 0.9, queries.mapIndexed { i, query ->
            FoodComponent("c$i", query, query, source = ComponentSource.VISIBLE,
                estimatedWeightG = 100.0, weightMinG = 80.0, weightMaxG = 120.0, confidence = 0.9)
        })), 0.9, emptyList())

    private fun http(counter: AtomicInteger, delayMs: Long, body: String) = OkHttpClient.Builder().addInterceptor { chain ->
        counter.incrementAndGet()
        Thread.sleep(delayMs) // Controlled network simulation; never calls a real endpoint.
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("benchmark")
            .body(body.toResponseBody()).build()
    }.build()

    @Test fun compareActualGitBaselineWithCurrentWorkspace() = runBlocking {
        val lookupQueries = listOf("米饭", "北豆腐", "rice white cooked", "egg whole boiled", "broccoli raw", "unknownfood")
        compare("离线首次匹配（6 组，含库加载）", 5, before = {
            val db = baseline()
            suspend { Value(lookupQueries.map { fingerprint(db.lookup(it, "")) }.joinToString("|")) }
        }, after = {
            val db = current()
            suspend { Value(lookupQueries.map { fingerprint(db.lookup(it, "")) }.joinToString("|")) }
        })

        val searches = listOf("rice cooked", "egg boiled", "chicken breast cooked", "broccoli raw", "西蓝花", "青椒", "黄瓜条", "米饭", "果", "油", "unknownfood", "sea cucumber")
        val oldSearch = baseline().also { it.searchCandidates("warmup", "") }
        val newSearch = current().also { it.searchCandidates("warmup", "") }
        compare("离线候选搜索（12 组，库与索引已加载）", 9, before = {
            suspend { Value(searches.map { q -> oldSearch.searchCandidates(q, "").joinToString(",") { fingerprint(it) } }.joinToString("|")) }
        }, after = {
            suspend { Value(searches.map { q -> newSearch.searchCandidates(q, "").joinToString(",") { fingerprint(it) } }.joinToString("|")) }
        })

        val localMeal = meal(List(2) { listOf("rice white cooked", "egg whole boiled", "broccoli raw", "potato boiled", "milk whole", "water") }.flatten())
        compare("整餐离线匹配（12 组成，6 个不同查询）", 9, before = {
            val db = baseline().also { it.searchCandidates("warmup", "") }
            suspend { Value(fingerprint(db.enrich(localMeal, ""))) }
        }, after = {
            val db = current().also { it.searchCandidates("warmup", "") }
            suspend { Value(fingerprint(db.enrich(localMeal, ""))) }
        })

        val unknownQueries = List(2) { (1..4).map { "unknownfood$it" } }.flatten()
        val unknownMeal = meal(unknownQueries)
        val noLocal = NutritionDatabases(china = false)
        compare("整餐在线回退（8 组成，每次 HTTP 模拟 120ms）", 5, before = {
            val requests = AtomicInteger()
            val db = BaselineFoodDataCentralClient(openAsset = { error("No assets") }, listAssets = { emptyArray() },
                databases = { noLocal }, httpClient = http(requests, 120, "{\"foods\":[]}"))
            suspend { Value(fingerprint(db.enrich(unknownMeal, "benchmark-only")), requests.get()) }
        }, after = {
            val requests = AtomicInteger()
            val db = FoodDataCentralClient(openAsset = { error("No assets") }, listAssets = { emptyArray() },
                databases = { noLocal }, httpClient = http(requests, 120, "{\"foods\":[]}"))
            suspend { Value(fingerprint(db.enrich(unknownMeal, "benchmark-only")), requests.get()) }
        })

        val visual = VisualMealDto(isFoodImage = true, mealName = "测试餐", overallConfidence = 0.9, dishes = listOf(
            VisualDishDto("测试菜品", components = unknownQueries.map { VisualComponentDto(it, it,
                estimatedWeightG = 100.0, weightMinG = 80.0, weightMaxG = 120.0, confidence = 0.9) })))
        val modelResponse = buildJsonObject {
            put("choices", buildJsonArray { add(buildJsonObject { put("message", buildJsonObject {
                put("content", Json.encodeToString(VisualMealDto.serializer(), visual))
            }) }) })
        }.toString()
        compare("文字识别核心流程（模型 150ms＋数据库 HTTP 120ms，均模拟）", 5, before = {
            val requests = AtomicInteger()
            val client = FoodRecognitionClient(httpClient = http(requests, 150, modelResponse))
            val db = BaselineFoodDataCentralClient(openAsset = { error("No assets") }, listAssets = { emptyArray() },
                databases = { noLocal }, httpClient = http(requests, 120, "{\"foods\":[]}"))
            suspend {
                val visualMeal = client.recognizeFromText("https://benchmark.invalid/v1", "benchmark-only", "fixture", "测试餐")
                Value(fingerprint(db.enrich(visualMeal, "benchmark-only").splitDishes()), requests.get())
            }
        }, after = {
            val requests = AtomicInteger()
            val client = FoodRecognitionClient(httpClient = http(requests, 150, modelResponse))
            val db = FoodDataCentralClient(openAsset = { error("No assets") }, listAssets = { emptyArray() },
                databases = { noLocal }, httpClient = http(requests, 120, "{\"foods\":[]}"))
            suspend {
                val visualMeal = client.recognizeFromText("https://benchmark.invalid/v1", "benchmark-only", "fixture", "测试餐")
                Value(fingerprint(db.enrich(visualMeal, "benchmark-only").splitDishes()), requests.get())
            }
        })

        val result = buildJsonObject {
            put("javaVersion", System.getProperty("java.version")); put("os", System.getProperty("os.name"))
            put("processors", Runtime.getRuntime().availableProcessors()); put("cases", JsonArray(cases))
            put("notes", buildJsonArray {
                add("每组先预热两轮，随后交替先运行基线/当前版；冷加载项每次新建客户端，包含离线库与索引初始化。")
                add("候选搜索没有查询结果缓存；整餐离线匹配在计时前加载库/索引，但查询缓存为空。")
                add("在线回退使用 8 个组成、4 个不同的未知查询；返回空 foods，单次 HTTP 固定等待 120ms。")
                add("文字核心流程增加一次 150ms 模型响应与真实 JSON 解析；不含 Android Service、页面展示和磁盘保存。")
                add("原版源文件由 git show 提取，仅重命名类型以与当前实现共存；数据库资产相同。")
            })
        }
        File("build/recognition-benchmark/results.json").apply { parentFile?.mkdirs() }.writeText(
            Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), result))
    }
}
