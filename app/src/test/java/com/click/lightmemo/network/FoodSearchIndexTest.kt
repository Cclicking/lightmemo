package com.click.lightmemo.network

import org.junit.Assert.*
import org.junit.Test

class FoodSearchIndexTest {
    @Test fun chineseRecallMatchesExhaustiveScoringForAllShortNames() {
        val names = mutableListOf<String>()
        var level = listOf("")
        repeat(4) {
            level = level.flatMap { prefix -> listOf("甲", "乙", "丙").map { prefix + it } }
            names += level
        }
        val foods = names.map { FoodSearchMatching.prepareChineseFood(it) }
        val index = FoodSearchIndex(foods) { it.recallKeys }
        for (name in names) {
            val query = FoodSearchMatching.prepareChineseQuery(name)
            val expected = foods.filter { FoodSearchMatching.chineseMatch(query, it).score >= 0.25 }
            val actual = index.candidates(FoodSearchMatching.chineseRecallKeys(listOf(query), emptyList()))
                .filter { FoodSearchMatching.chineseMatch(query, it).score >= 0.25 }
            assertEquals(name, expected, actual)
        }
    }

    @Test fun chineseIndexKeepsPositiveMatchesIncludingSingleCharactersContainmentAndAliases() {
        val foods = listOf("水", "水果", "苹果", "果", "鸡蛋", "鸡蛋面", "西兰花［绿菜花］", "牛肉［黄瓜条］", "黄瓜",
            "甜椒［灯笼椒、柿子椒］", "辣椒（青、尖）", "米饭（蒸）", "牛奶（脱脂）", "牛奶")
            .map { FoodSearchMatching.prepareChineseFood(it) }
        val index = FoodSearchIndex(foods) { it.recallKeys }
        for (name in listOf("水", "果", "苹果", "水果", "熟鸡蛋", "鸡蛋面", "绿菜花", "黄瓜", "黄瓜条", "青椒", "米饭", "牛奶", "不存在")) {
            val query = FoodSearchMatching.prepareChineseQuery(name)
            val expected = foods.filter { FoodSearchMatching.chineseMatch(query, it).score >= 0.25 }
            val candidates = index.candidates(FoodSearchMatching.chineseRecallKeys(listOf(query), emptyList()))
            assertEquals(name, expected, candidates.filter { FoodSearchMatching.chineseMatch(query, it).score >= 0.25 })
        }
    }

    @Test fun indexedScoringMatchesFullScanIncludingAliasesCompoundsAndPartialMatches() {
        val descriptions = listOf("Egg whole boiled", "Egg white raw", "Sea cucumber cooked",
            "Cucumber raw", "Broccoli raab raw", "Broccoli raw", "Mandarin orange raw",
            "Tangerines raw", "Soy chicken cooked", "Chicken breast cooked", "Chicken thigh cooked",
            "Pork balls cooked", "Pork meatball cooked", "Rice white cooked", "Rice flour",
            "Potatoes boiled", "Scallions raw", "Water bottled plain", "Rice white cooked")
        val index = FoodSearchIndex(descriptions, FoodSearchMatching::englishRecallTerms)
        val queries = listOf("fresh eggs boiled", "sea cucumber", "cucumber", "broccoli rabe",
            "broccoli", "mandarin orange", "tangerine", "soy chicken", "chicken breast cooked",
            "pork balls", "rice", "potato boiled", "green onions raw", "cooked boiled", "unknown")
        for (query in queries) {
            val expected = descriptions.map { it to FoodSearchMatching.englishMatch(query, it) }
                .filter { it.second.score >= 0.5 }.sortedByDescending { it.second.score }
            val actual = index.candidates(FoodSearchMatching.englishRecallTerms(query))
                .map { it to FoodSearchMatching.englishMatch(query, it) }
                .filter { it.second.score >= 0.5 }.sortedByDescending { it.second.score }
            assertEquals(query, expected, actual)
        }
    }

    @Test fun multipleQueriesKeepEveryCandidateOnceInOriginalOrder() {
        val foods = listOf("rice cooked", "egg rice", "egg boiled", "water")
        val index = FoodSearchIndex(foods, FoodSearchMatching::englishRecallTerms)
        assertEquals(foods.take(3), index.candidates(setOf("egg", "rice")))
        assertTrue(index.candidates(emptySet()).isEmpty())
        assertTrue(index.candidates(setOf("absent")).isEmpty())
    }
}
