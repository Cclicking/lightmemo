package com.foodcalorie.app.network

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

    @Test fun bundledDatabasesAreReadable() = runBlocking {
        val database = client()
        assertTrue(database.browseOffline(source = FoodDataCentralClient.DatabaseSource.USDA).isNotEmpty())
        assertTrue(database.browseOffline(source = FoodDataCentralClient.DatabaseSource.CHINA).isNotEmpty())
    }
}
