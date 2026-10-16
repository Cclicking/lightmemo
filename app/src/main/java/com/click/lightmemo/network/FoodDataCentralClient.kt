package com.click.lightmemo.network

import android.content.Context
import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.domain.FoodIdentitySafety
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

/** Supplied English uses USDA first; exact Chinese matches precede dictionary translations. */
class FoodDataCentralClient internal constructor(
    private val openAsset: (String) -> java.io.InputStream,
    private val listAssets: () -> Array<out String>,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val onMatch: (com.click.lightmemo.domain.NutritionMatchSource) -> Unit = {},
) {
    constructor(context: Context, onMatch: (com.click.lightmemo.domain.NutritionMatchSource) -> Unit = {}) : this(
        openAsset = { context.assets.open(it) },
        listAssets = { context.assets.list("").orEmpty() },
        onMatch = onMatch,
    )
    private val memoryCache = ConcurrentHashMap<String, NutritionReference>()
    private val usdaFoods: List<LocalFood> by lazy { loadUsdaFoods() }
    private val chinaFoods: List<ChinaFood> by lazy { loadChinaFoods() }

    data class OfflineDbStatus(
        val usdaAvailable: Boolean,
        val chinaAvailable: Boolean,
        val srLegacyAvailable: Boolean = usdaAvailable,
        val foundationAvailable: Boolean = false,
        val fnddsAvailable: Boolean = false,
    )

    enum class DatabaseSource {
        ALL,
        CHINA,
        USDA,
        USDA_FOUNDATION,
        USDA_SR_LEGACY,
        USDA_FNDDS,
    }

    /** 仅探测离线资产是否存在，避免触发全库解析。 */
    fun offlineStatus(): OfflineDbStatus {
        val sources = OfflineUsdaDatabase.entries.associateWith { source ->
            assetsExist(source.assetName, "${source.assetName}.gz")
        }
        return OfflineDbStatus(
            usdaAvailable = sources.values.any { it },
            chinaAvailable = assetsExist("china_food_composition.tsv", "china_food_composition.tsv.gz"),
            srLegacyAvailable = sources[OfflineUsdaDatabase.SR_LEGACY] == true,
            foundationAvailable = sources[OfflineUsdaDatabase.FOUNDATION] == true,
            fnddsAvailable = sources[OfflineUsdaDatabase.FNDDS] == true,
        )
    }

    private fun assetsExist(vararg names: String): Boolean {
        val listed = runCatching { listAssets().toSet() }.getOrDefault(emptySet())
        return names.any { it in listed }
    }

    suspend fun enrich(meal: MealRecognition, apiKey: String): MealRecognition = withContext(Dispatchers.IO) {
        val components = meal.dishes.flatMap { it.allComponents }.distinctBy { it.id }
        val matches = components.associate { component ->
            component.id to (component.nutritionReference?.takeIf {
                FoodIdentitySafety.allowsReference(component, it)
            } ?: resolve(component, apiKey))
        }
        meal.copy(dishes = meal.dishes.map { it.withReferences(matches) })
    }

    suspend fun lookup(
        query: String,
        apiKey: String,
        chineseQuery: String = query,
        originalName: String = "",
    ): NutritionReference? = withContext(Dispatchers.IO) {
        val inputs = listOf(query, chineseQuery, originalName).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (inputs.isEmpty()) return@withContext null
        // Length prefixes prevent different tuples from sharing a cache entry.
        val cacheKey = inputs.joinToString("") { "${it.length}:$it" }
        val englishQueries = FoodSearchMatching.englishQueries(inputs)
        val suppliedEnglish = FoodSearchMatching.englishQueries(inputs.filter { input ->
            input.none { it in '\u4e00'..'\u9fff' }
        })
        val translatedEnglish = englishQueries - suppliedEnglish.toSet()
        // The original recognized food is an independent constraint; generated search terms can be wrong.
        val validationQueries = englishQueries + inputs.filter { it.any { c -> c in '\u4e00'..'\u9fff' } }
        val resolved = memoryCache[cacheKey] ?: rankUsda(suppliedEnglish, true, validationQueries).firstOrNull()?.reference
            ?: apiKey.takeIf { it.isNotBlank() }?.let { findOnlineMatch(suppliedEnglish, it, validationQueries) }
            ?: findChinaLocal(inputs, englishQueries, englishRecallQueries = suppliedEnglish)
            // Keep exact Chinese database matches ahead of dictionary translations.
            ?: rankUsda(translatedEnglish, true, validationQueries).firstOrNull()?.reference
            ?: apiKey.takeIf { it.isNotBlank() }?.let { findOnlineMatch(translatedEnglish, it, validationQueries) }
            ?: findChinaLocal(inputs, englishQueries)
        resolved?.also { memoryCache[cacheKey] = it }
        runCatching { onMatch(com.click.lightmemo.domain.DiagnosticEvents.source(resolved)) }
        resolved
    }

    /** Broad recall for user selection; these results are not automatically accepted. */
    suspend fun searchCandidates(
        query: String,
        apiKey: String,
        limit: Int = 20,
        chineseQuery: String = query,
        originalName: String = "",
    ): List<NutritionReference> = withContext(Dispatchers.IO) {
        val inputs = listOf(query, chineseQuery, originalName).filter { it.isNotBlank() }.distinct()
        if (inputs.isEmpty()) return@withContext emptyList()
        val englishQueries = FoodSearchMatching.englishQueries(inputs)
        val count = limit.coerceAtLeast(1)
        val localUsda = rankUsda(englishQueries, automatic = false).take(count)
        val online = if (apiKey.isBlank()) emptyList() else englishQueries.flatMap {
            searchOnline(it, apiKey, count)
        }.map { ref -> RankedReference(ref, englishQueries.maxOfOrNull {
            FoodSearchMatching.englishMatch(it, ref.description).score
        } ?: 0.0) }.filter { it.score >= 0.5 }
        val localChina = rankChina(inputs, englishQueries, automatic = false).take(count)
        // Rank both languages together before truncating; source priority breaks score ties.
        (localUsda + online + localChina)
            .sortedByDescending { it.score }
            .map { it.reference }
            .distinctBy { "${it.dataType}:${it.sourceId}" }
            .take(count)
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
            if (source != DatabaseSource.CHINA) {
                addAll(usdaFoods.filter { source == DatabaseSource.ALL || source == DatabaseSource.USDA || it.source.filter == source }
                    .map { it.reference })
            }
        }
        val normalized = query.trim()
        if (normalized.isBlank()) {
            sourceItems
                .distinctBy { "${it.dataType}:${it.sourceId}" }
                .take(limit.coerceAtLeast(1))
        } else {
            val englishQueries = FoodSearchMatching.englishQueries(listOf(normalized))
            when (source) {
                DatabaseSource.CHINA -> rankChina(listOf(normalized), englishQueries, false)
                    .take(limit.coerceAtLeast(1)).map { it.reference }
                DatabaseSource.USDA, DatabaseSource.USDA_FOUNDATION,
                DatabaseSource.USDA_SR_LEGACY, DatabaseSource.USDA_FNDDS -> rankUsda(englishQueries, false, source = source)
                    .take(limit.coerceAtLeast(1)).map { it.reference }
                DatabaseSource.ALL -> searchCandidates(normalized, "", limit)
            }.distinctBy { "${it.dataType}:${it.sourceId}" }
                .take(limit.coerceAtLeast(1))
        }
    }

    private suspend fun resolve(component: FoodComponent, apiKey: String): NutritionReference? = lookup(
        query = component.databaseQuery,
        apiKey = apiKey,
        chineseQuery = component.chinaDatabaseQuery,
        originalName = component.name,
    )

    private fun rankUsda(
        queries: List<String>,
        automatic: Boolean,
        validationQueries: List<String> = queries,
        source: DatabaseSource = DatabaseSource.USDA,
    ): List<RankedReference> = if (queries.isEmpty()) emptyList() else usdaFoods
        .filter { source == DatabaseSource.USDA || it.source.filter == source }
        .mapNotNull { food ->
            val matches = queries.map { FoodSearchMatching.englishMatch(it, food.normalizedDescription) }
            val eligible = matches.filterIndexed { index, match -> !automatic ||
                (match.canAutoAccept && (food.source != OfflineUsdaDatabase.FNDDS ||
                    FoodSearchMatching.surveyRecipeAgrees(queries[index], food.normalizedDescription))) }
            val score = eligible.maxOfOrNull { it.score } ?: return@mapNotNull null
            if (score < 0.5 || (automatic && !FoodSearchMatching.englishConstraintsAgree(
                    validationQueries, food.normalizedDescription))) null
            else RankedReference(food.reference, score)
        }.sortedByDescending { it.score }

    private fun findChinaLocal(
        inputs: List<String>,
        queries: List<String>,
        englishRecallQueries: List<String> = queries,
    ): NutritionReference? = rankChina(inputs, queries, automatic = true, englishRecallQueries).firstOrNull()?.reference

    private fun rankChina(
        inputs: List<String>,
        queries: List<String>,
        automatic: Boolean,
        englishRecallQueries: List<String> = queries,
    ): List<RankedReference> = chinaFoods
        .mapNotNull { food ->
            if (automatic && inputs.any { !FoodIdentitySafety.agrees(it, food.normalizedEnglishName.ifBlank { food.reference.description }) }) return@mapNotNull null
            val chineseMatches = inputs.map { input ->
                FoodSearchMatching.chineseMatch(input, food.reference.description,
                    queries.firstOrNull().orEmpty(), food.normalizedEnglishName)
            }
            val englishMatches = englishRecallQueries.map { FoodSearchMatching.englishMatch(it, food.normalizedEnglishName) }
            val matches = (chineseMatches + englishMatches).filter { !automatic || it.canAutoAccept }
            val score = matches.maxOfOrNull { it.score } ?: return@mapNotNull null
            if (automatic && food.normalizedEnglishName.isNotBlank() &&
                !FoodSearchMatching.englishConstraintsAgree(queries, food.normalizedEnglishName)) return@mapNotNull null
            if (score < 0.25 || (automatic && !FoodSearchMatching.chineseStatesAgree(inputs, queries,
                    food.reference.description, food.normalizedEnglishName))) null
            else RankedReference(food.reference, score)
        }.sortedByDescending { it.score }

    private fun loadUsdaFoods(): List<LocalFood> = OfflineUsdaDatabase.entries.flatMap { source ->
        // Optional sources are absent in older installations and isolated test fixtures.
        if (!assetsExist(source.assetName, "${source.assetName}.gz")) {
            emptyList()
        } else try {
            openExpandedOrGzipAsset(source.assetName).bufferedReader().useLines { lines ->
                lines.drop(1).mapNotNull { line ->
                    val columns = line.split('\t')
                    if (columns.size != 6) return@mapNotNull null
                    val values = columns.drop(2).map { it.toDoubleOrNull() ?: return@mapNotNull null }
                    if (values.any { !it.isFinite() || it < 0 }) return@mapNotNull null
                    val reference = NutritionReference(
                        sourceId = columns[0].takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                        description = columns[1].takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                        dataType = source.label,
                        per100g = Nutrition(values[0], values[1], values[2], values[3]),
                    )
                    LocalFood(reference, FoodSearchMatching.english(reference.description), source)
                }.toList()
            }
        } catch (e: Exception) {
            throw RecognitionException("${source.label} 数据库读取失败", e)
        }
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
                    normalizedEnglishName = FoodSearchMatching.english(columns[2]),
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

    private suspend fun findOnlineMatch(
        queries: List<String>,
        apiKey: String,
        validationQueries: List<String> = queries,
    ): NutritionReference? {
        for (query in queries) {
            val match = searchOnline(query, apiKey, 8)
                .map { it to FoodSearchMatching.englishMatch(query, it.description) }
                .filter { it.second.canAutoAccept && FoodSearchMatching.englishConstraintsAgree(validationQueries, it.first.description) }
                .filter { !it.first.dataType.contains("FNDDS", ignoreCase = true) ||
                    FoodSearchMatching.surveyRecipeAgrees(query, it.first.description) }
                .maxByOrNull { it.second.score }?.first
            if (match != null) return match
        }
        return null
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

/** Declaration order is the tie-break priority for equally relevant offline matches. */
private enum class OfflineUsdaDatabase(val assetName: String, val label: String, val filter: FoodDataCentralClient.DatabaseSource) {
    FOUNDATION("fdc_foundation_macros.tsv", "USDA Foundation (offline 2026-04)", FoodDataCentralClient.DatabaseSource.USDA_FOUNDATION),
    SR_LEGACY("fdc_sr_legacy_macros.tsv", "SR Legacy (offline 2018-04)", FoodDataCentralClient.DatabaseSource.USDA_SR_LEGACY),
    FNDDS("fdc_fndds_macros.tsv", "USDA Survey (FNDDS) (offline 2021-2023, released 2024-10)", FoodDataCentralClient.DatabaseSource.USDA_FNDDS),
}

private data class RankedReference(val reference: NutritionReference, val score: Double)

private data class LocalFood(
    val reference: NutritionReference,
    val normalizedDescription: String,
    val source: OfflineUsdaDatabase,
)

private data class ChinaFood(
    val reference: NutritionReference,
    val normalizedEnglishName: String,
)

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
    fun nutrient(id: Int, number: String, name: String, unit: String? = null): Double? = foodNutrients
        .firstOrNull {
            (it.nutrientId == id || it.nutrientNumber == number || it.nutrientName.equals(name, ignoreCase = true)) &&
                it.value.isFinite() && it.value >= 0 &&
                (unit == null || it.unitName.equals(unit, ignoreCase = true))
        }?.value

    val calories = nutrient(2048, "958", "Energy (Atwater Specific Factors)", "KCAL")
        ?: nutrient(2047, "957", "Energy (Atwater General Factors)", "KCAL")
        ?: nutrient(1008, "208", "Energy", "KCAL") ?: return null
    val protein = nutrient(1003, "203", "Protein") ?: return null
    val carbs = nutrient(1005, "205", "Carbohydrate, by difference") ?: return null
    val fat = nutrient(1004, "204", "Total lipid (fat)") ?: return null
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
