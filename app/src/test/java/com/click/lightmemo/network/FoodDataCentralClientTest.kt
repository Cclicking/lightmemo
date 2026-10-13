package com.click.lightmemo.network

import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class FoodDataCentralClientTest {
    private fun fixtureClient(usdaRows: String = "", onlineResponse: String = "{\"foods\":[]}") = FoodDataCentralClient(
        openAsset = { name -> when (name) {
            "fdc_sr_legacy_macros.tsv" -> "id\tdescription\tkcal\tprotein\tcarbs\tfat\n$usdaRows".byteInputStream()
            "china_food_composition.tsv" -> ("id\tname\tenglish\tkcal\tprotein\tcarbs\tfat\n" +
                "cn-rice\t米饭（蒸）\t\t116\t2.6\t25.9\t0.3\n" +
                "cn-tofu\t北豆腐\t\t98\t12.2\t2\t4.8\n").byteInputStream()
            else -> throw IOException("unknown asset")
        } },
        listAssets = { arrayOf("fdc_sr_legacy_macros.tsv", "china_food_composition.tsv") },
        httpClient = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(onlineResponse.toResponseBody("application/json".toMediaType())).build()
        }.build(),
    )

    @Test fun emptyEnglishDatabaseUsesOriginalChineseName() = runBlocking {
        val result = fixtureClient().lookup("steamed rice", "configured-key", "米饭（蒸）")
        assertEquals("cn-rice", result?.sourceId)
        assertEquals(116.0, result!!.per100g.caloriesKcal, 0.0)
    }

    @Test fun chineseFallbackCacheDistinguishesDifferentOriginalNames() = runBlocking {
        val database = fixtureClient()
        assertEquals("cn-rice", database.lookup("unknown food", "", "米饭（蒸）")?.sourceId)
        assertEquals("cn-tofu", database.lookup("unknown food", "", "北豆腐")?.sourceId)
        assertEquals("cn-rice", database.lookup("unknown food", "", "米饭（蒸）")?.sourceId)
        assertNull(database.lookup("unknown food", "", "未知食物"))
    }

    @Test fun cookingWordsCannotMatchAnUnrelatedEnglishFood() = runBlocking {
        val database = fixtureClient("us-rice\tRice white cooked boiled\t130\t2.7\t28\t0.3\n")
        assertEquals("cn-tofu", database.lookup("tofu white cooked boiled", "", "北豆腐")?.sourceId)
        assertNull(database.lookup("cooked boiled", ""))
    }

    @Test fun rawEnglishFoodCannotReplaceCookedFood() = runBlocking {
        val database = fixtureClient("us-raw\tRice white raw\t365\t7\t80\t1\n")
        assertEquals("cn-rice", database.lookup("rice white cooked", "", "米饭（蒸）")?.sourceId)
    }

    @Test fun unrelatedOnlineResultDoesNotBlockChineseFallback() = runBlocking {
        val online = """{"foods":[{"fdcId":123,"description":"Rice white cooked boiled",
            "foodNutrients":[{"nutrientNumber":"208","unitName":"KCAL","value":130},
            {"nutrientNumber":"203","value":2.7},{"nutrientNumber":"205","value":28},
            {"nutrientNumber":"204","value":0.3}]}]}"""
        assertEquals("cn-tofu", fixtureClient(onlineResponse = online)
            .lookup("tofu white cooked boiled", "configured-key", "北豆腐")?.sourceId)
        assertEquals("123", fixtureClient(onlineResponse = online)
            .lookup("rice white cooked boiled", "configured-key", "米饭（蒸）")?.sourceId)
    }

    @Test fun reliableEnglishMatchStillTakesPriority() = runBlocking {
        val database = fixtureClient("us-rice\tRice white cooked\t130\t2.7\t28\t0.3\n")
        assertEquals("us-rice", database.lookup("rice white cooked", "", "米饭（蒸）")?.sourceId)
    }

    @Test fun componentWithoutChineseQueryFallsBackToItsName() = runBlocking {
        val component = com.click.lightmemo.domain.FoodComponent(
            id = "tofu", name = "北豆腐", databaseQuery = "tofu firm", chinaDatabaseQuery = "",
            source = com.click.lightmemo.domain.ComponentSource.VISIBLE,
            estimatedWeightG = 150.0, weightMinG = 150.0, weightMaxG = 150.0, confidence = 1.0,
        )
        val meal = com.click.lightmemo.domain.MealRecognition(true, "豆腐", listOf(
            com.click.lightmemo.domain.RecognizedDish("dish", "豆腐",
                com.click.lightmemo.domain.DishType.SINGLE_FOOD, 1.0, listOf(component)),
        ), 1.0, emptyList())
        val result = fixtureClient().enrich(meal, "")
        assertEquals("cn-tofu", result.dishes.single().components.single().nutritionReference?.sourceId)
        assertEquals(147.0, result.nutrition.caloriesKcal, 0.0)
    }

    private val assets get() = File(System.getProperty("food.assets"))
    private fun client(): FoodDataCentralClient = FoodDataCentralClient(
        openAsset = { File(assets, it).inputStream() },
        listAssets = { assets.list().orEmpty() },
        httpClient = OkHttpClient.Builder().addInterceptor { throw IOException("offline") }.build(),
    )

    @Test fun matchingReportsActualSourceButBrowsingDoesNotInflateCounters() = runBlocking {
        val sources = mutableListOf<com.click.lightmemo.domain.NutritionMatchSource>()
        val database = FoodDataCentralClient(
            openAsset = { File(assets, it).inputStream() }, listAssets = { assets.list().orEmpty() },
            httpClient = OkHttpClient.Builder().addInterceptor { throw IOException("offline") }.build(),
            onMatch = { sources += it })
        database.lookup("rice cooked", "")
        database.lookup("米饭", "")
        database.lookup("zzzzunmatchedxxxx", "")
        assertEquals(listOf(com.click.lightmemo.domain.NutritionMatchSource.USDA_OFFLINE,
            com.click.lightmemo.domain.NutritionMatchSource.CHINA, com.click.lightmemo.domain.NutritionMatchSource.MISSING), sources)
        database.browseOffline(query = "米饭")
        assertEquals(3, sources.size)
    }

    @Test fun optionalMatchRecorderFailureDoesNotPreventLookup() = runBlocking {
        val database = FoodDataCentralClient(openAsset = { File(assets, it).inputStream() },
            listAssets = { assets.list().orEmpty() }, onMatch = { error("diagnostics unavailable") })
        assertNotNull(database.lookup("米饭", ""))
    }

    @Test fun networkFailureStillReturnsChinaFallback() = runBlocking {
        val result = client().lookup("米饭", "configured-key")
        assertNotNull(result)
        assertTrue(result!!.dataType.contains("中国"))
        assertTrue(result.per100g.caloriesKcal > 0)
    }

    @Test fun candidateSearchPreservesLocalResultsWhenOnlineFails() = runBlocking {
        val result = client().searchCandidates("米饭", "configured-key")
        assertTrue(result.isNotEmpty())
    }

    @Test fun commonEnglishSearchShowsUsdaResultsBeforeFallbackResults() = runBlocking {
        val result = client().searchCandidates("beef", "")

        assertTrue(result.isNotEmpty())
        assertTrue(result.first().dataType.contains("USDA") || result.first().dataType.contains("SR Legacy"))
    }

    @Test fun offlineUsdaSearchIsNotHiddenByOtherSources() = runBlocking {
        val result = client().browseOffline(
            query = "beef",
            source = FoodDataCentralClient.DatabaseSource.USDA,
        )

        assertTrue(result.isNotEmpty())
        assertTrue(result.all { !it.dataType.contains("中国") })
    }

    @Test fun bundledDatabasesAreReadable() = runBlocking {
        val database = client()
        assertTrue(database.browseOffline(source = FoodDataCentralClient.DatabaseSource.USDA).isNotEmpty())
        assertTrue(database.browseOffline(source = FoodDataCentralClient.DatabaseSource.CHINA).isNotEmpty())
    }
}
