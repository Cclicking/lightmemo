package com.click.lightmemo.network

import android.content.Context
import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.domain.MealRecognition
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.domain.NutritionReference
import com.click.lightmemo.domain.RecognizedDish
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Offline nutrition lookup: USDA first, China Food Composition fallback, optional USDA API last. */
class FoodDataCentralClient internal constructor(
    private val openAsset: (String) -> java.io.InputStream,
    private val listAssets: () -> Array<out String>,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    constructor(context: Context) : this(
        openAsset = { context.assets.open(it) },
        listAssets = { context.assets.list("").orEmpty() },
    )
    private val memoryCache = ConcurrentHashMap<String, NutritionReference>()
    private val usdaFoods: List<LocalFood> by lazy { loadUsdaFoods() }
    private val chinaFoods: List<ChinaFood> by lazy { loadChinaFoods() }

    data class OfflineDbStatus(
        val usdaAvailable: Boolean,
        val chinaAvailable: Boolean,
    )

    enum class DatabaseSource {
        ALL,
        CHINA,
        USDA,
    }

    /** 仅探测离线资产是否存在，避免触发全库解析。 */
    fun offlineStatus(): OfflineDbStatus {
        return OfflineDbStatus(
            usdaAvailable = assetsExist("fdc_sr_legacy_macros.tsv", "fdc_sr_legacy_macros.tsv.gz"),
            chinaAvailable = assetsExist("china_food_composition.tsv", "china_food_composition.tsv.gz"),
        )
    }

    private fun assetsExist(vararg names: String): Boolean {
        val listed = runCatching { listAssets().toSet() }.getOrDefault(emptySet())
        return names.any { it in listed }
    }

    suspend fun enrich(meal: MealRecognition, apiKey: String): MealRecognition = withContext(Dispatchers.IO) {
        val components = meal.dishes.flatMap { it.allComponents }.distinctBy { it.id }
        val matches = components.associate { component ->
            component.id to resolve(component, apiKey)
        }
        meal.copy(dishes = meal.dishes.map { it.withReferences(matches) })
    }

    suspend fun lookup(query: String, apiKey: String): NutritionReference? = withContext(Dispatchers.IO) {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return@withContext null
        val cacheKey = "lookup:$normalized"
        val resolved = memoryCache[cacheKey] ?: findUsdaLocal(normalized)
            ?: apiKey.takeIf { it.isNotBlank() }?.let { searchOnline(normalized, it, 8).firstOrNull() }
            ?: findChinaLocal(query, query)
        resolved?.also { memoryCache[cacheKey] = it }
    }

    /**
     * Returns ranked matches for a component that the automatic enrichment could not resolve.
     * Local data is always searched first; the optional USDA API is only used when configured.
     */
    suspend fun searchCandidates(
        query: String,
        apiKey: String,
        limit: Int = 20,
    ): List<NutritionReference> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val localUsda = findUsdaCandidates(trimmed, limit)
        val localChina = findChinaCandidates(trimmed, trimmed, limit)
        val online = apiKey.takeIf { it.isNotBlank() }
            ?.let { searchOnline(trimmed, it, limit) }
            .orEmpty()

        (localChina + localUsda + online)
            .distinctBy { "${it.dataType}:${it.sourceId}" }
            .take(limit.coerceAtLeast(1))
    }

    /** Returns a lightweight, searchable view of the bundled offline database. */
    suspend fun browseOffline(
        query: String = "",
        source: DatabaseSource = DatabaseSource.ALL,
        limit: Int = 120,
    ): List<NutritionReference> = withContext(Dispatchers.IO) {
        val sourceItems = buildList {
            if (source == DatabaseSource.ALL || source == DatabaseSource.CHINA) {
                addAll(chinaFoods.map { it.reference })
            }
            if (source == DatabaseSource.ALL || source == DatabaseSource.USDA) {
                addAll(usdaFoods.map { it.reference })
            }
        }
        val normalized = query.trim()
        if (normalized.isBlank()) {
            sourceItems
                .distinctBy { "${it.dataType}:${it.sourceId}" }
                .take(limit.coerceAtLeast(1))
        } else {
            searchCandidates(normalized, "", limit)
                .filter { candidate ->
                    source == DatabaseSource.ALL || when (source) {
                        DatabaseSource.CHINA -> candidate.dataType.contains("中国")
                        DatabaseSource.USDA -> !candidate.dataType.contains("中国")
                        DatabaseSource.ALL -> true
                    }
                }
        }
    }

    private suspend fun resolve(component: FoodComponent, apiKey: String): NutritionReference? {
        val key = "component:${component.databaseQuery.lowercase()}|${component.chinaDatabaseQuery}"
        val resolved = memoryCache[key]
            ?: findUsdaLocal(component.databaseQuery)
            ?: apiKey.takeIf { it.isNotBlank() }
                ?.let { searchOnline(component.databaseQuery, it, 8).firstOrNull() }
            ?: findChinaLocal(component.chinaDatabaseQuery, component.databaseQuery)
        return resolved?.also { memoryCache[key] = it }
    }

    private fun findUsdaLocal(query: String): NutritionReference? {
        val normalized = normalize(query)
        val terms = normalized.split(' ').filter { it.length > 1 }.toSet()
        if (terms.isEmpty()) return null
        preferredReference(terms)?.let { preferred ->
            return preferred
        }
        val match = usdaFoods.asSequence()
            .map { food -> food to matchScore(normalized, terms, food.normalizedDescription) }
            .filter { it.second >= 0.75 }
            .maxByOrNull { it.second }
            ?.first
            ?: return null
        return match.reference
    }

    private fun findUsdaCandidates(query: String, limit: Int): List<NutritionReference> {
        val normalized = normalize(query)
        val terms = normalized.split(' ').filter { it.length > 1 }.toSet()
        if (terms.isEmpty()) return emptyList()
        return usdaFoods.asSequence()
            .map { food -> food to matchScore(normalized, terms, food.normalizedDescription) }
            .filter { it.second >= 0.5 }
            .sortedByDescending { it.second }
            .map { it.first.reference }
            .distinctBy { it.sourceId }
            .take(limit)
            .toList()
    }

    private fun preferredReference(terms: Set<String>): NutritionReference? {
        val preferredId = when {
            "oil" !in terms -> null
            "olive" in terms -> 171413L
            else -> 171411L // neutral cooking oil: soybean salad/cooking oil
        }
        return preferredId?.let { id -> usdaFoods.firstOrNull { it.reference.sourceId == id.toString() }?.reference }
    }

    private fun matchScore(query: String, terms: Set<String>, description: String): Double {
        val descriptionTerms = description.split(' ').toSet()
        val coverage = terms.count { it in descriptionTerms }.toDouble() / terms.size
        if (coverage < 0.75) return coverage
        val phraseBonus = if (description.contains(query)) 1.0 else 0.0
        val stateWords = setOf("raw", "cooked", "fried", "boiled", "roasted", "baked", "grilled")
        val requestedState = terms.intersect(stateWords)
        val statePenalty = if (requestedState.isNotEmpty() && requestedState.none { it in descriptionTerms }) 0.35 else 0.0
        return coverage + phraseBonus - statePenalty
    }

    private fun findChinaLocal(chineseQuery: String, englishQuery: String): NutritionReference? {
        val chinese = normalizeChinese(chineseQuery)
        if (chinese.isNotBlank()) {
            chinaFoods.asSequence()
                .map { food -> food to chineseScore(chinese, food.normalizedChineseName) }
                .filter { it.second >= 0.72 }
                .maxByOrNull { it.second }
                ?.first?.reference
                ?.let { return it }
        }
        val english = normalize(englishQuery)
        val terms = english.split(' ').filter { it.length > 1 }.toSet()
        if (terms.isEmpty()) return null
        return chinaFoods.asSequence()
            .filter { it.normalizedEnglishName.isNotBlank() }
            .map { food -> food to matchScore(english, terms, food.normalizedEnglishName) }
            .filter { it.second >= 0.75 }
            .maxByOrNull { it.second }
            ?.first?.reference
    }

    private fun findChinaCandidates(
        chineseQuery: String,
        englishQuery: String,
        limit: Int,
    ): List<NutritionReference> {
        val chinese = normalizeChinese(chineseQuery)
        if (chinese.isNotBlank()) {
            val chineseMatches = chinaFoods.asSequence()
                .map { food -> food to chineseScore(chinese, food.normalizedChineseName) }
                .filter { it.second >= 0.25 }
                .sortedByDescending { it.second }
                .map { it.first.reference }
                .distinctBy { it.sourceId }
                .take(limit)
                .toList()
            if (chineseMatches.isNotEmpty()) return chineseMatches
        }

        val english = normalize(englishQuery)
        val terms = english.split(' ').filter { it.length > 1 }.toSet()
        if (terms.isEmpty()) return emptyList()
        return chinaFoods.asSequence()
            .filter { it.normalizedEnglishName.isNotBlank() }
            .map { food -> food to matchScore(english, terms, food.normalizedEnglishName) }
            .filter { it.second >= 0.5 }
            .sortedByDescending { it.second }
            .map { it.first.reference }
            .distinctBy { it.sourceId }
            .take(limit)
            .toList()
    }

    private fun chineseScore(query: String, candidate: String): Double {
        if (query == candidate) return 2.0
        if (candidate.contains(query) || query.contains(candidate)) {
            return minOf(query.length, candidate.length).toDouble() / maxOf(query.length, candidate.length) + 0.8
        }
        val queryPairs = query.windowed(2).toSet()
        val candidatePairs = candidate.windowed(2).toSet()
        if (queryPairs.isEmpty() || candidatePairs.isEmpty()) return 0.0
        return queryPairs.intersect(candidatePairs).size.toDouble() / queryPairs.size
    }

    private fun loadUsdaFoods(): List<LocalFood> = try {
        // Android's asset packager expands .gz assets and exposes them without the .gz suffix.
        val input = runCatching { openAsset("fdc_sr_legacy_macros.tsv") }
            .getOrElse {
                GZIPInputStream(openAsset("fdc_sr_legacy_macros.tsv.gz"))
            }
        input.bufferedReader().useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val columns = line.split('\t')
                if (columns.size != 6) return@mapNotNull null
                val reference = NutritionReference(
                    sourceId = columns[0].takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                    description = columns[1],
                    dataType = "SR Legacy (offline 2018-04)",
                    per100g = Nutrition(
                        caloriesKcal = columns[2].toDoubleOrNull() ?: return@mapNotNull null,
                        proteinG = columns[3].toDoubleOrNull() ?: return@mapNotNull null,
                        carbsG = columns[4].toDoubleOrNull() ?: return@mapNotNull null,
                        fatG = columns[5].toDoubleOrNull() ?: return@mapNotNull null,
                    ),
                )
                LocalFood(reference, normalize(reference.description))
            }.toList()
        }
    } catch (e: Exception) {
        throw RecognitionException("离线营养数据库读取失败", e)
    }

    private fun loadChinaFoods(): List<ChinaFood> = try {
        openExpandedOrGzipAsset("china_food_composition.tsv").bufferedReader().useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val columns = line.split('\t')
                if (columns.size != 7) return@mapNotNull null
                val reference = NutritionReference(
                    sourceId = columns[0].takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                    description = columns[1],
                    dataType = "中国食物成分表标准版（第6版）",
                    per100g = Nutrition(
                        caloriesKcal = columns[3].toDoubleOrNull() ?: return@mapNotNull null,
                        proteinG = columns[4].toDoubleOrNull() ?: return@mapNotNull null,
                        carbsG = columns[5].toDoubleOrNull() ?: return@mapNotNull null,
                        fatG = columns[6].toDoubleOrNull() ?: return@mapNotNull null,
                    ),
                )
                ChinaFood(
                    reference = reference,
                    normalizedChineseName = normalizeChinese(columns[1]),
                    normalizedEnglishName = normalize(columns[2]),
                )
            }.toList()
        }
    } catch (e: Exception) {
        throw RecognitionException("中国食物成分离线数据库读取失败", e)
    }

    private fun openExpandedOrGzipAsset(baseName: String) = runCatching {
        openAsset(baseName)
    }.getOrElse {
        GZIPInputStream(openAsset("$baseName.gz"))
    }

    private suspend fun searchOnline(query: String, apiKey: String, limit: Int): List<NutritionReference> {
        return try {
            requestOnline(query, apiKey, limit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: java.io.IOException) {
            emptyList()
        } catch (_: RecognitionException) {
            emptyList()
        } catch (_: kotlinx.serialization.SerializationException) {
            emptyList()
        }
    }

    private suspend fun requestOnline(query: String, apiKey: String, limit: Int): List<NutritionReference> {
        val body = json.encodeToString(
            SearchRequest.serializer(),
            SearchRequest(query = query, pageSize = limit.coerceIn(1, 50)),
        )
        val request = Request.Builder()
            .url("https://api.nal.usda.gov/fdc/v1/foods/search?api_key=$apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) {
                throw RecognitionException("营养数据库查询失败 HTTP ${response.code}")
            }
            val result = json.decodeFromString<SearchResponse>(response.body?.string().orEmpty())
            return result.foods.asSequence()
                .mapNotNull { it.toReferenceOrNull() }
                .toList()
        }
    }
}

private data class LocalFood(
    val reference: NutritionReference,
    val normalizedDescription: String,
)

private data class ChinaFood(
    val reference: NutritionReference,
    val normalizedChineseName: String,
    val normalizedEnglishName: String,
)

private fun normalize(value: String): String = value.lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private fun normalizeChinese(value: String): String = value.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), "")

@Serializable
private data class SearchRequest(
    val query: String,
    val dataType: List<String> = listOf("Foundation", "SR Legacy", "Survey (FNDDS)"),
    val pageSize: Int = 8,
)

@Serializable
private data class SearchResponse(val foods: List<FdcFood> = emptyList())

@Serializable
private data class FdcFood(
    val fdcId: Long,
    val description: String,
    val dataType: String = "",
    val foodNutrients: List<FdcNutrient> = emptyList(),
)

@Serializable
private data class FdcNutrient(
    val nutrientId: Int? = null,
    val nutrientNumber: String? = null,
    val nutrientName: String = "",
    val unitName: String = "",
    val value: Double = 0.0,
)

private fun FdcFood.toReferenceOrNull(): NutritionReference? {
    fun nutrient(number: String, name: String, unit: String? = null): Double? = foodNutrients
        .firstOrNull {
            (it.nutrientNumber == number || it.nutrientName.equals(name, ignoreCase = true)) &&
                (unit == null || it.unitName.equals(unit, ignoreCase = true))
        }?.value

    val calories = nutrient("208", "Energy", "KCAL") ?: return null
    val protein = nutrient("203", "Protein") ?: return null
    val carbs = nutrient("205", "Carbohydrate, by difference") ?: return null
    val fat = nutrient("204", "Total lipid (fat)") ?: return null
    return NutritionReference(
        sourceId = fdcId.toString(),
        description = description,
        dataType = dataType,
        per100g = Nutrition(calories, protein, carbs, fat),
    )
}

private fun RecognizedDish.withReferences(matches: Map<String, NutritionReference?>): RecognizedDish = copy(
    components = components.map { it.withReference(matches) },
    children = children.map { it.withReferences(matches) },
)

private fun FoodComponent.withReference(matches: Map<String, NutritionReference?>): FoodComponent =
    copy(nutritionReference = matches[id])
