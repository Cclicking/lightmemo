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
    @Test fun enrichmentRepairsAnIncompatibleExistingReferenceInsteadOfReusingIt() = runBlocking {
        val component = com.click.lightmemo.domain.FoodComponent(
            "water", "水", "water", source = com.click.lightmemo.domain.ComponentSource.VISIBLE,
            estimatedWeightG = 100.0, weightMinG = 100.0, weightMaxG = 100.0, confidence = 0.9,
            nutritionReference = com.click.lightmemo.domain.NutritionReference(
                "ham", "Ham, water added", "USDA", com.click.lightmemo.domain.Nutrition(100.0)))
        val meal = com.click.lightmemo.domain.MealRecognition(true, "水", listOf(
            com.click.lightmemo.domain.RecognizedDish("water", "水", com.click.lightmemo.domain.DishType.SINGLE_FOOD, 0.9, listOf(component))), 0.9, emptyList())
        val database = fixtureClient("water\tWater, bottled, plain\t0\t0\t0\t0\n")
        val corrected = database.enrich(meal, "")
        assertEquals("water", corrected.dishes.single().components.single().nutritionReference?.sourceId)
        assertEquals(0.0, corrected.nutrition.caloriesKcal, 0.0)
        assertNull(fixtureClient().enrich(meal, "").dishes.single().components.single().nutritionReference)
    }
    private fun fixtureClient(
        usdaRows: String = "", onlineResponse: String = "{\"foods\":[]}", chinaRows: String = "",
        foundationRows: String = "", fnddsRows: String = "",
    ) = FoodDataCentralClient(
        openAsset = { name -> when (name) {
            "fdc_sr_legacy_macros.tsv" -> "id\tdescription\tkcal\tprotein\tcarbs\tfat\n$usdaRows".byteInputStream()
            "fdc_foundation_macros.tsv" -> "id\tdescription\tkcal\tprotein\tcarbs\tfat\n$foundationRows".byteInputStream()
            "fdc_fndds_macros.tsv" -> "id\tdescription\tkcal\tprotein\tcarbs\tfat\n$fnddsRows".byteInputStream()
            "china_food_composition.tsv" -> ("id\tname\tenglish\tkcal\tprotein\tcarbs\tfat\n" +
                "cn-rice\t米饭（蒸）\t\t116\t2.6\t25.9\t0.3\n" +
                "cn-tofu\t北豆腐\t\t98\t12.2\t2\t4.8\n" + chinaRows).byteInputStream()
            else -> throw IOException("unknown asset")
        } },
        listAssets = { buildList {
            add("fdc_sr_legacy_macros.tsv")
            add("china_food_composition.tsv")
            if (foundationRows.isNotBlank()) add("fdc_foundation_macros.tsv")
            if (fnddsRows.isNotBlank()) add("fdc_fndds_macros.tsv")
        }.toTypedArray() },
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
        // The fallback has no cooking state, so it is available for review only.
        assertNull(database.lookup("tofu white cooked boiled", "", "北豆腐"))
        assertTrue(database.searchCandidates("tofu white cooked boiled", "", chineseQuery = "北豆腐")
            .any { it.sourceId == "cn-tofu" })
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
        assertNull(fixtureClient(onlineResponse = online)
            .lookup("tofu white cooked boiled", "configured-key", "北豆腐"))
        assertEquals("123", fixtureClient(onlineResponse = online)
            .lookup("rice white cooked boiled", "configured-key", "米饭（煮）")?.sourceId)
    }

    @Test fun reliableEnglishMatchStillTakesPriority() = runBlocking {
        val database = fixtureClient("us-rice\tRice white cooked\t130\t2.7\t28\t0.3\n")
        assertEquals("us-rice", database.lookup("rice white cooked", "", "米饭（蒸）")?.sourceId)
    }

    @Test fun pluralAndCookingSynonymsFindOfflineFood() = runBlocking {
        val database = fixtureClient("us-egg\tEgg whole boiled\t150\t12\t1\t10\n")
        assertEquals("us-egg", database.lookup("fresh eggs whole boiling", "")?.sourceId)
    }

    @Test fun originalChineseNameIsTriedWhenGeneratedChineseQueryFails() = runBlocking {
        val database = fixtureClient()
        assertEquals("cn-tofu", database.lookup("unrecognized food", "", "豆制食品", "北豆腐")?.sourceId)
    }

    @Test fun cookedOriginalNameCannotBeBypassedByAnUnspecifiedAlternateQuery() = runBlocking {
        val database = fixtureClient("us-raw\tChicken breast raw\t120\t20\t0\t3\n")
        assertNull(database.lookup("chicken breast", "", "熟鸡胸肉"))
        assertTrue(database.searchCandidates("chicken breast", "", chineseQuery = "熟鸡胸肉")
            .any { it.sourceId == "us-raw" })
    }

    @Test fun conflictingCookingMethodsAcrossLanguagesRequireReview() = runBlocking {
        val database = fixtureClient("us-rice\tRice white cooked boiled\t130\t3\t28\t0.3\n")
        assertNull(database.lookup("rice white cooked boiled", "", "米饭（蒸）"))
        assertTrue(database.searchCandidates("rice white cooked boiled", "", chineseQuery = "米饭（蒸）").isNotEmpty())
    }

    @Test fun chineseAliasFindsStandardNameAndCachesEachOriginalNameSeparately() = runBlocking {
        val database = fixtureClient(chinaRows = "cn-potato\t土豆\t\t80\t2\t18\t0.1\n")
        assertEquals("cn-potato", database.lookup("unknown food", "", "马铃薯")?.sourceId)
        assertEquals("cn-tofu", database.lookup("unknown food", "", "unknown", "北豆腐")?.sourceId)
        assertEquals("cn-rice", database.lookup("unknown food", "", "unknown", "米饭（蒸）")?.sourceId)
    }

    @Test fun partialIdentityAndProcessedFormsRemainManualCandidates() = runBlocking {
        val database = fixtureClient("us-thigh\tChicken thigh cooked\t180\t20\t0\t10\n" +
            "us-flour\tRice flour\t360\t7\t80\t1\n")
        assertNull(database.lookup("chicken breast cooked", ""))
        assertNull(database.lookup("rice", ""))
        assertTrue(database.searchCandidates("chicken breast cooked", "").any { it.sourceId == "us-thigh" })
        assertTrue(database.searchCandidates("rice", "").any { it.sourceId == "us-flour" })
    }

    @Test fun candidateSearchMergesChineseAndEnglishInsteadOfStoppingAtFirstLanguage() = runBlocking {
        val database = fixtureClient(chinaRows = "cn-egg\t鸡蛋\tEgg whole\t140\t12\t1\t10\n")
        val ids = database.searchCandidates("eggs whole", "", chineseQuery = "北豆腐").map { it.sourceId }
        assertTrue("cn-egg" in ids)
        assertTrue("cn-tofu" in ids)
    }

    @Test fun exactChineseCandidateIsNotCrowdedOutByBroadEnglishResults() = runBlocking {
        val rows = (1..25).joinToString("") { "us-$it\tTofu assorted variety $it\t100\t12\t2\t5\n" }
        val results = fixtureClient(rows).searchCandidates("tofu", "", limit = 5, chineseQuery = "北豆腐")
        assertEquals("cn-tofu", results.first().sourceId)
        assertEquals(5, results.size)
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

    @Test fun foundationWinsEqualMatchesButDoesNotHideBetterLegacyMatches() = runBlocking {
        val database = fixtureClient(
            usdaRows = "legacy\tRice white cooked\t130\t3\t28\t0.3\n",
            foundationRows = "foundation\tRice white cooked\t125\t3\t27\t0.3\n",
            fnddsRows = "survey\tRice white cooked\t135\t3\t29\t0.3\n",
        )
        val result = database.lookup("rice white cooked", "")!!
        assertEquals("foundation", result.sourceId)
        assertEquals("USDA Foundation (offline 2026-04)", result.dataType)
        val betterLegacy = fixtureClient(
            usdaRows = "legacy\tRice white cooked\t130\t3\t28\t0.3\n",
            foundationRows = "foundation\tRice brown raw\t360\t7\t76\t3\n",
        )
        assertEquals("legacy", betterLegacy.lookup("rice white cooked", "")?.sourceId)
    }

    @Test fun fnddsIsAvailableOfflineAndRecipesCannotReplacePlainIngredients() = runBlocking {
        val database = fixtureClient(fnddsRows = "survey\tRice white cooked\t130\t3\t28\t0.3\n")
        val result = database.lookup("rice white cooked", "")!!
        assertEquals("survey", result.sourceId)
        assertTrue(result.dataType.contains("FNDDS"))
        assertEquals(com.click.lightmemo.domain.NutritionMatchSource.USDA_OFFLINE,
            com.click.lightmemo.domain.DiagnosticEvents.source(result))

        val recipe = fixtureClient(fnddsRows = "recipe\tRice white cooked fat added\t180\t3\t28\t6\n")
        assertNull(recipe.lookup("rice white cooked", ""))
        assertEquals("recipe", recipe.searchCandidates("rice white cooked", "").first().sourceId)
    }

    @Test fun bundledNewSourcesAreCompleteAndIndividuallyFilterable() = runBlocking {
        val database = client()
        val foundation = database.browseOffline(source = FoodDataCentralClient.DatabaseSource.USDA_FOUNDATION, limit = 10000)
        val survey = database.browseOffline(source = FoodDataCentralClient.DatabaseSource.USDA_FNDDS, limit = 10000)
        val legacy = database.browseOffline(source = FoodDataCentralClient.DatabaseSource.USDA_SR_LEGACY, limit = 10000)
        assertEquals(367, foundation.size)
        assertEquals(5431, survey.size)
        assertEquals(7793, legacy.size)
        assertTrue(foundation.all { it.dataType.contains("Foundation") })
        assertTrue(survey.all { it.dataType.contains("FNDDS") })
        assertEquals(27.0, foundation.single { it.sourceId == "321360" }.per100g.caloriesKcal, 0.0)
        assertEquals(130.0, survey.single { it.sourceId == "2710788" }.per100g.caloriesKcal, 0.0)
        assertTrue(database.browseOffline("rice", FoodDataCentralClient.DatabaseSource.USDA_FNDDS)
            .all { it.dataType.contains("FNDDS") })
        val status = database.offlineStatus()
        assertTrue(status.srLegacyAvailable && status.foundationAvailable && status.fnddsAvailable)
    }

    @Test fun statusChecksExpandedAndGzipAssetsWithoutReadingThem() {
        val database = FoodDataCentralClient(
            openAsset = { error("Status must not parse assets") },
            listAssets = { arrayOf("fdc_foundation_macros.tsv", "fdc_fndds_macros.tsv.gz") },
        )
        val status = database.offlineStatus()
        assertTrue(status.usdaAvailable && status.foundationAvailable && status.fnddsAvailable)
        assertFalse(status.srLegacyAvailable)
        assertFalse(status.chinaAvailable)
    }

    @Test fun onlineFoundationAcceptsAtwaterCaloriesAndNutrientIds() = runBlocking {
        val response = """{"foods":[{"fdcId":123,"description":"Rice white cooked","dataType":"Foundation",
            "foodNutrients":[{"nutrientId":2048,"unitName":"KCAL","value":130},
            {"nutrientId":2047,"unitName":"KCAL","value":140},
            {"nutrientId":1003,"value":3},{"nutrientId":1005,"value":28},
            {"nutrientId":1004,"value":0.3}]}]}"""
        val reference = fixtureClient(onlineResponse = response).lookup("rice white cooked", "configured-key")!!
        assertEquals("123", reference.sourceId)
        assertEquals(130.0, reference.per100g.caloriesKcal, 0.0)
    }

    @Test fun onlineSurveyCannotBypassOfflineRecipeChecks() = runBlocking {
        val response = """{"foods":[{"fdcId":123,"description":"Rice white cooked fat added","dataType":"Survey (FNDDS)",
            "foodNutrients":[{"nutrientId":1008,"unitName":"KCAL","value":180},
            {"nutrientId":1003,"value":3},{"nutrientId":1005,"value":28},
            {"nutrientId":1004,"value":6}]}]}"""
        val database = fixtureClient(onlineResponse = response)
        assertNull(database.lookup("rice white cooked", "configured-key"))
        assertTrue(database.searchCandidates("rice white cooked", "configured-key").any { it.sourceId == "123" })
    }
}
