package com.click.lightmemo.network

import com.click.lightmemo.domain.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FoodIdentityRegressionTest {
    @Test fun ingredientMentionsAndDifferentFoodFormsNeverAuthorizeAutomaticMatching() {
        val pairs = listOf(
            "water" to "Ham, water added", "vegetable oil" to "Fast foods, potato, fried in vegetable oil",
            "milk whole" to "Potatoes, mashed, prepared with whole milk",
            "hamburger bun" to "Hamburger on white bun, small patty",
            "soy sauce based sauce" to "Beef with soy-based sauce",
            "egg whole" to "Egg custards, dry mix, prepared with whole milk",
            "quail egg" to "Quail, cooked", "wood ear" to "Pork, ears",
            "cherry tomato" to "Cherries, raw", "potato cooked" to "Sweet potato, cooked, boiled",
            "sweet potato cooked" to "Sweet potato leaves, cooked, steamed",
            "chicken fried" to "Chicken, meatless, breaded, fried",
            "pork cooked" to "Sausage, Italian, pork, cooked, pan-fried",
            "beef" to "Frankfurter, beef, unheated",
            "vegetable oil" to "Vegetable oil-butter spread, reduced calorie",
            "broccoli cooked" to "Broccoli, chinese, cooked",
            "ice" to "Ice creams, vanilla, light",
            "water" to "Gelatin desserts, dry mix, prepared with water",
            "water" to "Water convolvulus, raw",
            "sweet potato cooked" to "Sweet potato, squash, pumpkin, chrysanthemum, or bean leaves, cooked",
            "pork cooked" to "Pork, cured, bacon, cooked, restaurant",
            "potato cooked" to "Potatoes, microwaved, cooked in skin, skin, without salt",
            "chicken cooked fried" to "Chicken, breaded, fried",
        )
        pairs.forEach { (query, candidate) ->
            assertFalse("$query -> $candidate", FoodSearchMatching.englishMatch(query, candidate).canAutoAccept)
        }
        assertTrue(FoodSearchMatching.englishMatch("chicken cooked", "Chicken, drumstick, cooked, braised").canAutoAccept)
        assertTrue(FoodSearchMatching.englishMatch("vegetable oil", "Oil, vegetable, soybean").canAutoAccept)
    }

    @Test fun bundledDatabaseKeepsCommonIngredientsAndRejectsConflictingOriginalNames() = runBlocking {
        val assets = File(requireNotNull(System.getProperty("food.assets")))
        val db = FoodDataCentralClient({ File(assets, it).inputStream() }, { assets.list().orEmpty() })
        val cases = listOf(
            Triple("water", "水", "water"), Triple("ice", "冰块", "water"),
            Triple("vegetable oil", "食用油", "oil"), Triple("oil vegetable", "植物油", "oil"),
            Triple("pork cooked", "熟猪肉片", "pork"), Triple("beef", "牛肉", "beef"),
            Triple("potato cooked", "土豆炖", "potato"), Triple("sweet potato cooked", "蒸红薯", "sweetpotato"),
            Triple("broccoli cooked", "西兰花", "broccoli"), Triple("chicken cooked", "熟鸡肉块", "chicken"),
            Triple("hamburger bun", "汉堡胚", "bun"), Triple("milk whole", "纯牛奶", "milk"),
            Triple("egg whole", "鸡蛋", "egg"), Triple("soy sauce", "酱油", "soysauce"),
        )
        cases.forEach { (query, name, identity) ->
            val selected = db.lookup(query, "", name, name)
            assertNotNull("$name / $query", selected)
            assertEquals("$name -> ${selected!!.description}", identity, FoodIdentitySafety.identity(selected.description))
            if (identity == "water") assertEquals(0.0, selected.per100g.caloriesKcal, 0.0)
        }
        assertNull(db.lookup("Peppers, hot chili, red, raw", "", "辣椒红油酱汁", "辣椒红油酱汁"))
    }
}
