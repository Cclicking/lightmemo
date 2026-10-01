package com.click.lightmemo.network

import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class FoodDataCentralClientTest {
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
