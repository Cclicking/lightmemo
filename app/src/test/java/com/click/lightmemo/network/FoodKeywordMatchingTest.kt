package com.click.lightmemo.network

import com.click.lightmemo.domain.NutritionReference
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Exercises the user's failing Chinese keywords against the actual bundled databases. */
class FoodKeywordMatchingTest {
    @Test fun commonChineseKeywordsFindTheRightFoodOffline() = runBlocking {
        val assets = File(requireNotNull(System.getProperty("food.assets")))
        val database = FoodDataCentralClient(
            openAsset = { File(assets, it).inputStream() },
            listAssets = { assets.list().orEmpty() },
        )
        val reports = mutableListOf<JsonElement>()
        for (query in listOf("熟鸡肉块", "青椒", "黄瓜", "西兰花", "青菜", "韭菜", "橘子")) {
            val selected = database.lookup(query, "")
            assertNotNull(query, selected)
            val candidates = database.searchCandidates(query, "", limit = 20)
            assertTrue(query, candidates.isNotEmpty())
            when (query) {
                "熟鸡肉块" -> {
                    val reference = selected!!
                    val isChinese = reference.description.any { it in '\u4e00'..'\u9fff' }
                    assertTrue(FoodSearchMatching.chineseStatesAgree(listOf(query), listOf("chicken cooked"),
                        if (isChinese) reference.description else "",
                        if (isChinese) "" else reference.description))
                    assertFalse(reference.description.contains("鸡肉松"))
                }
                "青椒" -> {
                    assertEquals("043124", selected!!.sourceId)
                    assertTrue(candidates.any { it.sourceId == "043123" })
                }
                "黄瓜" -> {
                    assertEquals("043208", selected!!.sourceId)
                    assertTrue(candidates.none { it.description.contains("sea cucumber", true) || it.description.contains("海参") || it.sourceId == "082118" })
                    assertFalse(candidates.first().description.contains("pickle", true))
                }
                "青菜" -> assertEquals("045120", selected!!.sourceId)
                "韭菜" -> assertEquals("044404", selected!!.sourceId)
                "橘子" -> {
                    val description = selected!!.description
                    assertTrue(description.contains("tangerine", true) || description.contains("mandarin", true))
                    assertFalse(description.contains("juice", true))
                    assertFalse(description.contains("canned", true))
                    assertEquals(selected.sourceId, database.lookup("桔子", "")?.sourceId)
                }
                "西兰花" -> {
                    assertEquals("045217", selected!!.sourceId)
                    assertTrue(candidates.none { it.sourceId == "045116" || it.description.contains("broccoli ra", true) })
                }
            }
            reports += buildJsonObject {
                put("query", query)
                put("englishVariants", JsonArray(FoodSearchMatching.englishQueries(listOf(query)).map(::JsonPrimitive)))
                put("automatic", selected!!.asJson())
                put("candidates", JsonArray(candidates.take(5).map { it.asJson() }))
            }
        }
        println(Json { prettyPrint = true }.encodeToString(JsonArray.serializer(), JsonArray(reports)))
    }

    private fun NutritionReference.asJson() = buildJsonObject {
        put("id", sourceId)
        put("name", description)
        put("source", dataType)
        put("kcal", per100g.caloriesKcal)
        put("protein", per100g.proteinG)
        put("carbs", per100g.carbsG)
        put("fat", per100g.fatG)
    }
}
