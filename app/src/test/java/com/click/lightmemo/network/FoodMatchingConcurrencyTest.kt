package com.click.lightmemo.network

import com.click.lightmemo.data.NutritionDatabases
import com.click.lightmemo.domain.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class FoodMatchingConcurrencyTest {
    @Test fun prefetchIsOfflineAndSilentAndChangedReviewIdentityCannotReuseIt() = runBlocking {
        val reports = mutableListOf<NutritionMatchSource>()
        val enabled = NutritionDatabases(china = false)
        val requests = AtomicInteger()
        val database = FoodDataCentralClient(
            openAsset = { "id\tdescription\tkcal\tprotein\tcarbs\tfat\nwater\tWater bottled plain\t0\t0\t0\t0\n".byteInputStream() },
            listAssets = { arrayOf("fdc_sr_legacy_macros.tsv") }, databases = { enabled },
            onMatch = { reports += it },
            httpClient = OkHttpClient.Builder().addInterceptor { chain ->
                requests.incrementAndGet()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body("{\"foods\":[]}".toResponseBody()).build()
            }.build(),
        )
        database.prefetchOffline(meal(listOf(component("a", "water"), component("b", "unknownfood"))), "key")
        assertTrue(reports.isEmpty())
        assertEquals(0, requests.get())
        val matched = database.enrich(meal(listOf(component("a", "water"))), "key")
        assertEquals("water", matched.dishes.single().components.single().nutritionReference?.sourceId)
        assertEquals(listOf(NutritionMatchSource.USDA_OFFLINE), reports)
        // The reviewer corrected identity while retaining a stale generated search term.
        val changed = database.enrich(meal(listOf(component("new", "water").copy(name = "egg", estimatedWeightG = 150.0))), "key")
            .dishes.single().components.single()
        assertNull(changed.nutritionReference)
        assertEquals("new", changed.id)
        assertEquals(150.0, changed.estimatedWeightG, 0.0)
        assertTrue(requests.get() > 0)
    }

    @Test fun disablingSourcesAfterPrefetchCannotReuseTheirCachedReferences() = runBlocking {
        var enabled = NutritionDatabases(china = false, online = false)
        val database = FoodDataCentralClient(
            openAsset = { "id\tdescription\tkcal\tprotein\tcarbs\tfat\nwater\tWater bottled plain\t0\t0\t0\t0\n".byteInputStream() },
            listAssets = { arrayOf("fdc_sr_legacy_macros.tsv") }, databases = { enabled },
        )
        val input = meal(listOf(component("a", "water")))
        database.prefetchOffline(input, "key")
        enabled = enabled.copy(srLegacy = false)
        assertNull(database.enrich(input, "key").dishes.single().components.single().nutritionReference)
    }

    @Test fun progressiveCandidatesKeepFinalRankingAndDoNotRepeatOfflineSearch() = runBlocking {
        val release = CountDownLatch(1)
        val partial = CompletableDeferred<FoodDataCentralClient.CandidateUpdate>()
        val database = FoodDataCentralClient(
            openAsset = { "id\tdescription\tkcal\tprotein\tcarbs\tfat\nlocal\tRice cooked\t130\t3\t28\t0\n".byteInputStream() },
            listAssets = { arrayOf("fdc_sr_legacy_macros.tsv") },
            databases = { NutritionDatabases(china = false) },
            httpClient = OkHttpClient.Builder().addInterceptor { chain ->
                check(release.await(10, TimeUnit.SECONDS))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body("{\"foods\":[]}".toResponseBody()).build()
            }.build(),
        )
        val updates = async {
            val collected = mutableListOf<FoodDataCentralClient.CandidateUpdate>()
            database.candidateUpdates("rice", "key").collect {
                collected += it
                if (it.searchingOnline) partial.complete(it)
            }
            collected
        }
        try {
            val first = withTimeout(5_000) { partial.await() }
            assertEquals("local", first.results.single().sourceId)
            assertFalse(updates.isCompleted)
        } finally { release.countDown() }
        val all = updates.await()
        assertEquals(2, all.size)
        assertFalse(all.last().searchingOnline)
        assertEquals(all.first().results, all.last().results)
    }

    private fun database(onRequest: () -> String) = FoodDataCentralClient(
        openAsset = { error("No assets expected") }, listAssets = { emptyArray() },
        databases = { NutritionDatabases(china = false) },
        httpClient = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(onRequest().toResponseBody()).build()
        }.build(),
    )
    private fun component(id: String, query: String, grams: Double = 50.0) = FoodComponent(
        id, query, query, source = ComponentSource.VISIBLE, estimatedWeightG = grams,
        weightMinG = grams, weightMaxG = grams, confidence = 0.9,
    )
    private fun meal(components: List<FoodComponent>) = MealRecognition(true, "餐食", listOf(
        RecognizedDish("dish", "菜品", DishType.MIXED_DISH, 0.9, components)), 0.9, emptyList())

    @Test fun repeatedUnknownIngredientUsesOneSearchAndDoesNotCacheFailureAcrossMeals() = runBlocking {
        val requests = AtomicInteger()
        val database = database { requests.incrementAndGet(); "{\"foods\":[]}" }
        val components = listOf(component("a", "unknownfood", 50.0), component("b", "unknownfood", 150.0))
        val result = database.enrich(meal(components), "key").dishes.single().components
        assertEquals(1, requests.get())
        assertEquals(listOf("a", "b"), result.map { it.id })
        assertEquals(listOf(50.0, 150.0), result.map { it.estimatedWeightG })
        assertTrue(result.all { it.nutritionReference == null && it.hasNutritionCandidates == false })
        database.enrich(meal(components), "key")
        assertEquals(2, requests.get())
    }

    @Test fun onlineReviewCandidateIsReusedInsteadOfSearchedAgain() = runBlocking {
        val requests = AtomicInteger()
        val database = database {
            requests.incrementAndGet()
            """{"foods":[{"fdcId":1,"description":"Pork cooked","foodNutrients":[
                {"nutrientNumber":"208","unitName":"KCAL","value":239},
                {"nutrientNumber":"203","value":27},{"nutrientNumber":"205","value":0},
                {"nutrientNumber":"204","value":14}]}]}"""
        }
        val result = database.enrich(meal(listOf(component("a", "pork meatball cooked"))), "key")
            .dishes.single().components.single()
        assertEquals(1, requests.get())
        assertNull(result.nutritionReference)
        assertEquals(true, result.hasNutritionCandidates)
    }

    @Test fun independentIngredientsUseBoundedConcurrency() = runBlocking {
        val started = CountDownLatch(3)
        val release = CountDownLatch(1)
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val requests = AtomicInteger()
        val database = database {
            requests.incrementAndGet()
            val current = active.incrementAndGet()
            maximum.accumulateAndGet(current, ::maxOf)
            started.countDown()
            try {
                check(release.await(10, TimeUnit.SECONDS))
                "{\"foods\":[]}"
            } finally { active.decrementAndGet() }
        }
        val task = async { database.enrich(meal((1..7).map { component("$it", "unknownfood$it") }), "key") }
        try {
            assertTrue("Three independent lookups should start before any completes",
                withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
        } finally { release.countDown() }
        assertEquals(7, task.await().dishes.single().components.size)
        assertEquals(7, requests.get())
        assertEquals(3, maximum.get())
    }

    @Test fun localProbeNeverCallsOnlineAndDoesNotReportAMiss() = runBlocking {
        val reports = mutableListOf<NutritionMatchSource>()
        val database = FoodDataCentralClient(
            openAsset = { error("No assets expected") }, listAssets = { emptyArray() },
            databases = { NutritionDatabases(china = false) }, onMatch = { reports += it },
            httpClient = OkHttpClient.Builder().addInterceptor { error("Local probe must not use HTTP") }.build(),
        )
        assertNull(database.lookupOffline("unknownfood"))
        assertTrue(reports.isEmpty())
    }
}
