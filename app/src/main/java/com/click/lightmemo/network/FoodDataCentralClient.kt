package com.click.lightmemo.network

import com.click.lightmemo.recognition.RecognitionLogProvider

import com.click.lightmemo.recognition.RecognitionLogKind

import com.click.lightmemo.recognition.RecognitionDiagnostics

import android.content.Context
import com.click.lightmemo.data.NutritionDatabases
import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.domain.FoodIdentitySafety
import com.click.lightmemo.domain.MealRecognition
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.domain.NutritionReference
import com.click.lightmemo.domain.RecognizedDish
import com.click.lightmemo.recognition.RecognitionPhase
import com.click.lightmemo.recognition.measureRecognitionPhase
import com.click.lightmemo.recognition.logCategory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    private val databases: suspend () -> NutritionDatabases = { NutritionDatabases() },
    private val onMatch: (com.click.lightmemo.domain.NutritionMatchSource) -> Unit = {},
) {
    constructor(context: Context, databases: suspend () -> NutritionDatabases = { NutritionDatabases() }, onMatch: (com.click.lightmemo.domain.NutritionMatchSource) -> Unit = {}) : this(
        openAsset = { context.assets.open(it) },
        listAssets = { context.assets.list("").orEmpty() },
        onMatch = onMatch,
        databases = databases,
    )
    private val memoryCache = ConcurrentHashMap<LookupCacheKey, NutritionReference>()
    private data class LookupCacheKey(
        val inputs: List<String>,
        val databases: NutritionDatabases,
        val onlineConfigured: Boolean,
    )

    private val usdaFoods: List<LocalFood> by lazy { loadUsdaFoods() }
    private val usdaIndex by lazy {
        FoodSearchIndex(usdaFoods) { FoodSearchMatching.englishRecallTerms(it.normalizedDescription) }
    }
    private val chinaFoods: List<ChinaFood> by lazy { loadChinaFoods() }
    private val chinaIndex by lazy { FoodSearchIndex(chinaFoods) { it.features.recallKeys } }

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

    suspend fun enrich(meal: MealRecognition, apiKey: String): MealRecognition = measureRecognitionPhase(RecognitionPhase.NUTRITION_MATCH) {
        withContext(Dispatchers.IO) {
            val enabled = databases()
            val components = meal.dishes.flatMap { it.allComponents }.distinctBy { it.id }
            val pending = components.filter { component ->
                component.nutritionReference?.let {
                    enabled.allows(it.dataType) && FoodIdentitySafety.allowsReference(component, it)
                } != true
            }.groupBy { inputs(it.databaseQuery, it.chinaDatabaseQuery, it.name) }
            val permits = Semaphore(3)
            val resolved = pending.keys.map { queries -> async {
                permits.withPermit {
                    val session = OnlineSearchSession(apiKey)
                    val reference = lookupEnabled(queries, apiKey, enabled, session)
                    val candidates = if (reference != null) null else hasCandidates(queries, apiKey, enabled, session)
                    queries to (reference to candidates)
                }
            } }.awaitAll().toMap()
            val matches = components.associate { component ->
                val reference = component.nutritionReference?.takeIf {
                    enabled.allows(it.dataType) && FoodIdentitySafety.allowsReference(component, it)
                }
                val match = if (reference != null) reference to null
                    else resolved.getValue(inputs(component.databaseQuery, component.chinaDatabaseQuery, component.name))
                // Emit diagnostics on this coroutine, once per original unresolved component.
                if (reference == null) reportMatch(match.first)
                component.id to component.copy(nutritionReference = match.first, hasNutritionCandidates = match.second)
            }
            meal.copy(dishes = meal.dishes.map { it.withNutritionMatches(matches) })
        }
    }

    suspend fun lookup(
        query: String,
        apiKey: String,
        chineseQuery: String = query,
        originalName: String = "",
    ): NutritionReference? = measureRecognitionPhase(RecognitionPhase.NUTRITION_MATCH) {
        withContext(Dispatchers.IO) {
            val enabled = databases()
            lookupEnabled(inputs(query, chineseQuery, originalName), apiKey, enabled, OnlineSearchSession(apiKey))
                .also { reportMatch(it) }
        }
    }

    /** Speculative local work only. Exact query/source keys prevent changed review results from
     * inheriting stale matches. Final enrichment owns all diagnostics and candidate decisions. */
    internal suspend fun prefetchOffline(meal: MealRecognition, apiKey: String) =
        measureRecognitionPhase(RecognitionPhase.NUTRITION_PREFETCH) {
            withContext(Dispatchers.IO) {
                val enabled = databases()
                val queries = meal.dishes.flatMap { it.allComponents }
                    .map { inputs(it.databaseQuery, it.chinaDatabaseQuery, it.name) }.distinct().take(12)
                val permits = Semaphore(2)
                queries.map { input -> async {
                    permits.withPermit {
                        lookupEnabled(input, apiKey, enabled, OnlineSearchSession(""), allowOnline = false)
                    }
                } }.awaitAll()
                Unit
            }
        }

    /** A local probe is not a failed recognition; only successful probes emit match diagnostics. */
    suspend fun lookupOffline(query: String, chineseQuery: String = query, originalName: String = ""): NutritionReference? =
        measureRecognitionPhase(RecognitionPhase.NUTRITION_MATCH) {
            withContext(Dispatchers.IO) {
                lookupEnabled(inputs(query, chineseQuery, originalName), "", databases(), OnlineSearchSession(""))
                    .also { if (it != null) reportMatch(it) }
            }
        }

    private fun inputs(query: String, chineseQuery: String, originalName: String) =
        listOf(query, chineseQuery, originalName).map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun reportMatch(reference: NutritionReference?) {
        runCatching { onMatch(com.click.lightmemo.domain.DiagnosticEvents.source(reference)) }
    }

    private suspend fun lookupEnabled(
        inputs: List<String>, apiKey: String, enabled: NutritionDatabases, session: OnlineSearchSession,
        allowOnline: Boolean = true,
    ): NutritionReference? {
        if (inputs.isEmpty()) return null
        // Source preferences are part of the key so disabled sources cannot survive in cache.
        val cacheKey = LookupCacheKey(inputs, enabled, apiKey.isNotBlank())
        memoryCache[cacheKey]?.let { return it }
        val englishQueries = FoodSearchMatching.englishQueries(inputs)
        val suppliedEnglish = FoodSearchMatching.englishQueries(inputs.filter { input ->
            input.none { it in '\u4e00'..'\u9fff' }
        })
        val translatedEnglish = englishQueries - suppliedEnglish.toSet()
        // The original recognized food is an independent constraint; generated search terms can be wrong.
        val validationQueries = englishQueries + inputs.filter { it.any { c -> c in '\u4e00'..'\u9fff' } }
        val resolved = scoreUsda(suppliedEnglish, true, validationQueries, enabled = enabled).maxByOrNull { it.score }?.reference
            ?: findChinaLocal(inputs, englishQueries, englishRecallQueries = suppliedEnglish, enabled = enabled)
            // Keep exact Chinese database matches ahead of dictionary translations.
            ?: scoreUsda(translatedEnglish, true, validationQueries, enabled = enabled).maxByOrNull { it.score }?.reference
            ?: findChinaLocal(inputs, englishQueries, enabled = enabled)
            // Exhaust reliable offline matches before waiting for network fallbacks.
            ?: apiKey.takeIf { allowOnline && enabled.online && it.isNotBlank() }?.let { findOnlineMatch(englishQueries, session, validationQueries) }
        resolved?.also { memoryCache[cacheKey] = it }
        return resolved
    }

    data class CandidateUpdate(val results: List<NutritionReference>, val searchingOnline: Boolean)

    /** Local candidates can be selected while online sources are still loading. */
    fun candidateUpdates(
        query: String, apiKey: String, limit: Int = 20, chineseQuery: String = query, originalName: String = "",
    ): Flow<CandidateUpdate> = channelFlow {
        val results = searchCandidatesInternal(query, apiKey, limit, chineseQuery, originalName) {
            send(CandidateUpdate(it, searchingOnline = true))
        }
        send(CandidateUpdate(results, searchingOnline = false))
    }

    /** Broad recall for user selection; these results are not automatically accepted. */
    suspend fun searchCandidates(
        query: String,
        apiKey: String,
        limit: Int = 20,
        chineseQuery: String = query,
        originalName: String = "",
    ): List<NutritionReference> = searchCandidatesInternal(query, apiKey, limit, chineseQuery, originalName)

    private suspend fun searchCandidatesInternal(
        query: String, apiKey: String, limit: Int, chineseQuery: String, originalName: String,
        onLocalResults: suspend (List<NutritionReference>) -> Unit = {},
    ): List<NutritionReference> = withContext(Dispatchers.IO) {
        val inputs = listOf(query, chineseQuery, originalName).filter { it.isNotBlank() }.distinct()
        if (inputs.isEmpty()) return@withContext emptyList()
        val enabled = databases()
        val englishQueries = FoodSearchMatching.englishQueries(inputs)
        val count = limit.coerceAtLeast(1)
        val localUsda = rankUsda(englishQueries, automatic = false, enabled = enabled).take(count)
        val localChina = rankChina(inputs, englishQueries, automatic = false, enabled = enabled).take(count)
        if (enabled.online && apiKey.isNotBlank() && englishQueries.isNotEmpty()) {
            onLocalResults(candidateReferences(localUsda + localChina, count))
        }
        val online = if (!enabled.online || apiKey.isBlank()) emptyList() else englishQueries.flatMap {
            searchOnline(it, apiKey, count)
        }.map { ref -> RankedReference(ref, englishQueries.maxOfOrNull {
            FoodSearchMatching.englishMatch(it, ref.description).score
        } ?: 0.0) }.filter { it.score >= 0.5 }
        // Rank both languages together before truncating; source priority breaks score ties.
        candidateReferences(localUsda + online + localChina, count)
    }

    private fun candidateReferences(ranked: List<RankedReference>, count: Int) = ranked.sortedByDescending { it.score }
        .map { it.reference }
        .distinctBy { "${it.dataType}:${it.sourceId}" }
        .take(count)

    /** Returns a lightweight, searchable view of the bundled offline database. */
    suspend fun browseOffline(
        query: String = "",
        source: DatabaseSource = DatabaseSource.ALL,
        limit: Int = 120,
    ): List<NutritionReference> = withContext(Dispatchers.IO) {
        val enabled = databases()
        val normalized = query.trim()
        if (normalized.isBlank()) {
            val sourceItems = buildList {
                if (enabled.china && (source == DatabaseSource.ALL || source == DatabaseSource.CHINA)) {
                    addAll(chinaFoods.map { it.reference })
                }
                if (source != DatabaseSource.CHINA) {
                    addAll(usdaFoods.filter { enabled.allows(it.reference.dataType) &&
                        (source == DatabaseSource.ALL || source == DatabaseSource.USDA || it.source.filter == source) }
                        .map { it.reference })
                }
            }
            sourceItems
                .distinctBy { "${it.dataType}:${it.sourceId}" }
                .take(limit.coerceAtLeast(1))
        } else {
            val englishQueries = FoodSearchMatching.englishQueries(listOf(normalized))
            when (source) {
                DatabaseSource.CHINA -> rankChina(listOf(normalized), englishQueries, false, enabled = enabled)
                    .take(limit.coerceAtLeast(1)).map { it.reference }
                DatabaseSource.USDA, DatabaseSource.USDA_FOUNDATION,
                DatabaseSource.USDA_SR_LEGACY, DatabaseSource.USDA_FNDDS -> rankUsda(englishQueries, false, source = source, enabled = enabled)
                    .take(limit.coerceAtLeast(1)).map { it.reference }
                DatabaseSource.ALL -> searchCandidates(normalized, "", limit)
            }.distinctBy { "${it.dataType}:${it.sourceId}" }
                .take(limit.coerceAtLeast(1))
        }
    }

    /** Enrichment only needs existence; a local candidate must not wait for online search. */
    private suspend fun hasCandidates(
        inputs: List<String>, apiKey: String, enabled: NutritionDatabases, session: OnlineSearchSession,
    ): Boolean {
        val queries = FoodSearchMatching.englishQueries(inputs)
        if (scoreUsda(queries, automatic = false, enabled = enabled).any() ||
            scoreChina(inputs, queries, automatic = false, enabled = enabled).any()) return true
        return enabled.online && apiKey.isNotBlank() && queries.any { query ->
            session.search(query, 1).any { reference ->
                queries.any { FoodSearchMatching.englishMatch(it, reference.description).score >= 0.5 }
            }
        }
    }

    private fun rankUsda(
        queries: List<String>,
        automatic: Boolean,
        validationQueries: List<String> = queries,
        source: DatabaseSource = DatabaseSource.USDA,
        enabled: NutritionDatabases,
    ): List<RankedReference> = scoreUsda(queries, automatic, validationQueries, source, enabled)
        .sortedByDescending { it.score }.toList()

    private fun scoreUsda(
        queries: List<String>,
        automatic: Boolean,
        validationQueries: List<String> = queries,
        source: DatabaseSource = DatabaseSource.USDA,
        enabled: NutritionDatabases,
    ): Sequence<RankedReference> = if (queries.isEmpty() || !(enabled.foundation || enabled.srLegacy || enabled.fndds)) emptySequence() else usdaIndex
        .candidates(queries.flatMap { FoodSearchMatching.englishRecallTerms(it) }.toSet())
        .asSequence()
        .filter { enabled.allows(it.reference.dataType) && (source == DatabaseSource.USDA || it.source.filter == source) }
        .mapNotNull { food ->
            val matches = queries.map { FoodSearchMatching.englishMatch(it, food.normalizedDescription) }
            val eligible = matches.filterIndexed { index, match -> !automatic ||
                (match.canAutoAccept && (food.source != OfflineUsdaDatabase.FNDDS ||
                    FoodSearchMatching.surveyRecipeAgrees(queries[index], food.normalizedDescription))) }
            val score = eligible.maxOfOrNull { it.score } ?: return@mapNotNull null
            if (score < 0.5 || (automatic && !FoodSearchMatching.englishConstraintsAgree(
                    validationQueries, food.normalizedDescription))) null
            else RankedReference(food.reference, score)
        }

    private fun findChinaLocal(
        inputs: List<String>,
        queries: List<String>,
        englishRecallQueries: List<String> = queries,
        enabled: NutritionDatabases,
    ): NutritionReference? = scoreChina(inputs, queries, automatic = true, englishRecallQueries, enabled).maxByOrNull { it.score }?.reference

    private fun rankChina(
        inputs: List<String>,
        queries: List<String>,
        automatic: Boolean,
        englishRecallQueries: List<String> = queries,
        enabled: NutritionDatabases,
    ): List<RankedReference> = scoreChina(inputs, queries, automatic, englishRecallQueries, enabled)
        .sortedByDescending { it.score }.toList()

    private fun scoreChina(
        inputs: List<String>,
        queries: List<String>,
        automatic: Boolean,
        englishRecallQueries: List<String> = queries,
        enabled: NutritionDatabases,
    ): Sequence<RankedReference> {
        if (!enabled.china) return emptySequence()
        val chineseQueries = inputs.map { FoodSearchMatching.prepareChineseQuery(it, queries.firstOrNull().orEmpty()) }
        val requestedStates = FoodSearchMatching.requestedChineseStates(inputs, queries)
        return chinaIndex.candidates(FoodSearchMatching.chineseRecallKeys(chineseQueries, englishRecallQueries)).asSequence()
            .mapNotNull { food ->
                if (automatic && inputs.any { !FoodIdentitySafety.agrees(it, food.normalizedEnglishName.ifBlank { food.reference.description }) }) return@mapNotNull null
                val chineseMatches = chineseQueries.map { query ->
                    FoodSearchMatching.chineseMatch(query, food.features)
                }
                val englishMatches = englishRecallQueries.map { FoodSearchMatching.englishMatch(it, food.normalizedEnglishName) }
                val matches = (chineseMatches + englishMatches).filter { !automatic || it.canAutoAccept }
                val score = matches.maxOfOrNull { it.score } ?: return@mapNotNull null
                if (automatic && food.normalizedEnglishName.isNotBlank() &&
                    !FoodSearchMatching.englishConstraintsAgree(queries, food.normalizedEnglishName)) return@mapNotNull null
                if (score < 0.25 || (automatic && !FoodSearchMatching.chineseStatesAgree(requestedStates, food.features))) null
                else RankedReference(food.reference, score)
            }
    }

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
        session: OnlineSearchSession,
        validationQueries: List<String> = queries,
    ): NutritionReference? {
        for (query in queries) {
            val match = session.search(query, 8)
                .map { it to FoodSearchMatching.englishMatch(query, it.description) }
                .filter { it.second.canAutoAccept && FoodSearchMatching.englishConstraintsAgree(validationQueries, it.first.description) }
                .filter { !it.first.dataType.contains("FNDDS", ignoreCase = true) ||
                    FoodSearchMatching.surveyRecipeAgrees(query, it.first.description) }
                .maxByOrNull { it.second.score }?.first
            if (match != null) return match
        }
        return null
    }

    /** Request-scoped only: network failures and empty results never poison future recognitions. */
    private inner class OnlineSearchSession(private val apiKey: String) {
        private val responses = mutableMapOf<String, Pair<Int, List<NutritionReference>>>()
        suspend fun search(query: String, limit: Int): List<NutritionReference> {
            responses[query]?.takeIf { it.first >= limit }?.let { return it.second }
            return searchOnline(query, apiKey, limit).also { responses[query] = limit to it }
        }
    }

    private suspend fun requestOnline(query: String, apiKey: String, limit: Int): List<NutritionReference> {
        val criteria = SearchRequest(query = query,
            dataType = listOf("Foundation", "SR Legacy", "Survey (FNDDS)"), pageSize = limit.coerceIn(1, 50))
        val body = json.encodeToString(
            SearchRequest.serializer(),
            criteria,
        )
        val request = Request.Builder()
            .url("https://api.nal.usda.gov/fdc/v1/foods/search?api_key=$apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val metrics = kotlinx.coroutines.currentCoroutineContext()[RecognitionDiagnostics]
        val call = metrics?.nextNetworkRequest()
        val started = System.nanoTime()
        fun elapsed() = ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(0)
        metrics?.logEvent(RecognitionLogKind.HTTP_START, RecognitionPhase.NUTRITION_MATCH,
            request = call, bytes = request.body?.contentLength(), provider = RecognitionLogProvider.USDA,
            candidateLimit = criteria.pageSize)
        try {
            httpClient.newCall(request).awaitResponse().use { response ->
                metrics?.logEvent(RecognitionLogKind.HTTP_HEADERS, RecognitionPhase.NUTRITION_MATCH,
                    durationMs = elapsed(), request = call, httpStatus = response.code)
                if (!response.isSuccessful) {
                    throw RecognitionException("营养数据库查询失败 HTTP ${response.code}")
                }
                val raw = response.body?.string().orEmpty()
                metrics?.logEvent(RecognitionLogKind.HTTP_BODY, RecognitionPhase.NUTRITION_MATCH,
                    durationMs = elapsed(), request = call, httpStatus = response.code, bytes = raw.toByteArray(Charsets.UTF_8).size.toLong())
                val result = json.decodeFromString<SearchResponse>(raw)
                return result.foods.asSequence()
                    .mapNotNull { it.toReferenceOrNull() }
                    .toList()
            }
        } catch (error: Exception) {
            metrics?.logEvent(RecognitionLogKind.HTTP_ERROR, RecognitionPhase.NUTRITION_MATCH,
                durationMs = elapsed(), request = call, error = error.logCategory())
            throw error
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
) {
    val features = FoodSearchMatching.prepareChineseFood(reference.description, normalizedEnglishName)
}

@Serializable
private data class SearchRequest(
    val query: String,
    // Required serializer fields: defaults would be omitted, silently restoring the server's 50/all-types search.
    val dataType: List<String>,
    val pageSize: Int,
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

private fun RecognizedDish.withNutritionMatches(matches: Map<String, FoodComponent>): RecognizedDish = copy(
    components = components.map { matches[it.id] ?: it },
    children = children.map { it.withNutritionMatches(matches) },
)
