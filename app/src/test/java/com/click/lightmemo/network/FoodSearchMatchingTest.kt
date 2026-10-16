package com.click.lightmemo.network

import org.junit.Assert.*
import org.junit.Test

class FoodSearchMatchingTest {
    @Test fun pluralAndAliasesKeepFoodIdentity() {
        assertTrue(FoodSearchMatching.englishMatch("fresh eggs boiled", "Egg, boiled").canAutoAccept)
        assertTrue(FoodSearchMatching.englishMatch("aubergine raw", "Eggplant, uncooked").canAutoAccept)
        assertTrue(FoodSearchMatching.englishMatch("green onions raw", "Scallions, raw").canAutoAccept)
    }

    @Test fun partialFoodMatchesAreCandidatesOnly() {
        val match = FoodSearchMatching.englishMatch("chicken breast cooked", "Chicken thigh cooked")
        assertTrue(match.score >= 0.5)
        assertFalse(match.canAutoAccept)
        assertFalse(FoodSearchMatching.englishMatch("cooked boiled", "Rice cooked boiled").canAutoAccept)
    }

    @Test fun cookingStatesMustBeSupportedAndCannotConflict() {
        for (candidate in listOf("Rice uncooked", "Rice raw", "Rice fried", "Rice")) {
            assertFalse(candidate, FoodSearchMatching.englishMatch("rice steamed", candidate).canAutoAccept)
        }
        assertTrue(FoodSearchMatching.englishMatch("rice steamed", "Rice cooked").canAutoAccept)
        assertFalse(FoodSearchMatching.englishMatch("potato raw", "Potato dried").canAutoAccept)
        assertFalse(FoodSearchMatching.statesAgree(listOf("rice steamed", "rice cooked"), "rice fried"))
    }

    @Test fun processedFoodsCannotReplaceTheirIngredients() {
        for (candidate in listOf("Rice flour", "Rice pudding", "Rice soup", "Rice oil")) {
            val match = FoodSearchMatching.englishMatch("rice", candidate)
            assertTrue(match.score > 0)
            assertFalse(candidate, match.canAutoAccept)
        }
        assertTrue(FoodSearchMatching.englishMatch("rice flour", "Rice flour white").canAutoAccept)
        assertFalse(FoodSearchMatching.englishMatch("plain yogurt", "Yogurt sweetened").canAutoAccept)
        assertFalse(FoodSearchMatching.englishConstraintsAgree(listOf("chicken breast cooked", "chicken cooked"), "Chicken thigh cooked"))
    }

    @Test fun chineseAliasesAndBracketAliasesMatchButSubstringsDoNotAutoMatch() {
        assertTrue(FoodSearchMatching.chineseMatch("西蓝花", "西兰花[绿菜花]").canAutoAccept)
        assertTrue(FoodSearchMatching.chineseMatch("绿菜花", "西兰花[绿菜花]").canAutoAccept)
        assertTrue(FoodSearchMatching.chineseMatch("马铃薯", "土豆").canAutoAccept)
        val match = FoodSearchMatching.chineseMatch("鸡蛋", "鸡蛋面")
        assertTrue(match.score > 0)
        assertFalse(match.canAutoAccept)
        assertFalse(FoodSearchMatching.chineseMatch("牛奶", "牛奶（脱脂）").canAutoAccept)
        assertFalse(FoodSearchMatching.chineseMatch("鸡胸肉", "鸡胸肉（去皮）").canAutoAccept)
        assertFalse(FoodSearchMatching.chineseMatch("熟鸡蛋", "鸡蛋（生）").canAutoAccept)
        assertFalse(FoodSearchMatching.chineseMatch("熟鸡蛋", "鸡蛋").canAutoAccept)
        assertTrue(FoodSearchMatching.chineseMatch("鸡蛋（熟）", "鸡蛋", englishName = "Egg cooked").canAutoAccept)
    }

    @Test fun translationsAreBoundedToKnownWholeNames() {
        assertEquals(listOf("broccoli"), FoodSearchMatching.englishQueries(listOf("西蓝花")))
        assertEquals(listOf("egg whole boiled"), FoodSearchMatching.englishQueries(listOf("水煮鸡蛋")))
        assertTrue(FoodSearchMatching.englishQueries(listOf("鸡蛋面")).isEmpty())
        assertTrue(FoodSearchMatching.chineseMatch("生菜", "生菜").canAutoAccept)
    }

    @Test fun surveyRecipesNeedExplicitAddedIngredients() {
        assertFalse(FoodSearchMatching.surveyRecipeAgrees("rice white cooked", "Rice white cooked fat added"))
        assertFalse(FoodSearchMatching.surveyRecipeAgrees("rice white cooked", "Rice white cooked with peas"))
        assertFalse(FoodSearchMatching.surveyRecipeAgrees("egg", "Egg omelet"))
        assertFalse(FoodSearchMatching.surveyRecipeAgrees("milk", "Milk NFS"))
        assertTrue(FoodSearchMatching.surveyRecipeAgrees("rice white cooked", "Rice white cooked no added fat"))
        assertTrue(FoodSearchMatching.surveyRecipeAgrees("rice white cooked fat added", "Rice white cooked fat added"))
        assertTrue(FoodSearchMatching.surveyRecipeAgrees("rice white cooked with peas", "Rice white cooked with peas"))
    }

    @Test fun fullWidthBracketsAndMultipleChineseAliasesAreRecognized() {
        for (query in listOf("西兰花", "西蓝花", "绿菜花")) {
            assertTrue(query, FoodSearchMatching.chineseMatch(query, "西兰花［绿菜花］").canAutoAccept)
        }
        for (query in listOf("甜椒", "灯笼椒", "柿子椒")) {
            assertTrue(query, FoodSearchMatching.chineseMatch(query, "甜椒［灯笼椒、柿子椒］").canAutoAccept)
        }
        assertTrue(FoodSearchMatching.chineseMatch("黄瓜", "黄瓜（鲜）［胡瓜］").canAutoAccept)
        assertTrue(FoodSearchMatching.chineseMatch("米饭", "米饭（蒸，代表值）", "rice cooked", "Rice steamed").canAutoAccept)
        assertFalse(FoodSearchMatching.chineseMatch("牛奶", "牛奶（脱脂）［脱脂乳］").canAutoAccept)
    }

    @Test fun shapeSuffixesAreRemovedOnlyFromKnownFoodsAndKeepCookingState() {
        for (query in listOf("熟鸡肉块", "熟鸡肉小块", "熟鸡肉切丁")) {
            assertEquals(query, listOf("chicken cooked"), FoodSearchMatching.englishQueries(listOf(query)))
        }
        assertEquals(listOf("cucumber"), FoodSearchMatching.englishQueries(listOf("黄瓜片")))
        for (query in listOf("鸡肉松", "熟鸡肉松", "鸡蛋面", "豆腐干")) {
            assertTrue(query, FoodSearchMatching.englishQueries(listOf(query)).isEmpty())
        }
        assertTrue(FoodSearchMatching.chineseMatch("生姜", "生姜（鲜）").canAutoAccept)
        val misleadingAlias = FoodSearchMatching.chineseMatch("黄瓜", "牛肉（股内肉）［针扒、米龙、黄瓜条］")
        assertFalse(misleadingAlias.canAutoAccept)
        assertEquals(0.0, misleadingAlias.score, 0.0)
    }

    @Test fun greenPepperRecallsBothCommonMeaningsWithSweetPepperFirst() {
        val sweet = FoodSearchMatching.chineseMatch("青椒", "甜椒［灯笼椒、柿子椒］")
        val hot = FoodSearchMatching.chineseMatch("青椒", "辣椒（青、尖）")
        assertTrue(sweet.canAutoAccept && hot.canAutoAccept)
        assertTrue(sweet.score > hot.score)
        assertEquals(listOf("pepper green"), FoodSearchMatching.englishQueries(listOf("青椒")))
    }

    @Test fun sharedEnglishWordsDoNotConflateDifferentFoods() {
        for ((query, candidate) in listOf(
            "cucumber" to "Sea cucumber, yane (Alaska Native)",
            "cucumber raw" to "Sea cucumbers raw",
            "broccoli" to "Cabbage Rape, Broccoli Raab",
            "broccoli raw" to "Broccoli rabe raw",
            "chicken cooked" to "Soy chicken, rolled sheet, cooked with spices, semisoft",
        )) {
            val match = FoodSearchMatching.englishMatch(query, candidate)
            assertEquals(candidate, 0.0, match.score, 0.0)
            assertFalse(candidate, match.canAutoAccept)
        }
        assertTrue(FoodSearchMatching.englishMatch("sea cucumber", "Sea cucumber, yane").canAutoAccept)
        assertTrue(FoodSearchMatching.englishMatch("broccoli raab", "Broccoli rabe raw").canAutoAccept)
        assertTrue(FoodSearchMatching.englishMatch("soy chicken cooked", "Soy chicken, rolled sheet, cooked").canAutoAccept)
        assertTrue(FoodSearchMatching.englishMatch("chicken cooked",
            "Chicken, broilers or fryers, drumstick, meat only, cooked, braised").canAutoAccept)
    }

    @Test fun ordinaryVegetableCandidatesRankAbovePreservedProducts() {
        val plain = FoodSearchMatching.englishMatch("cucumber", "Cucumber with peel raw")
        val pickle = FoodSearchMatching.englishMatch("cucumber", "Pickles cucumber dill")
        assertTrue(plain.score > pickle.score)
        assertFalse(pickle.canAutoAccept)
        assertTrue(FoodSearchMatching.englishMatch("cucumber pickles", "Pickles cucumber dill").canAutoAccept)
    }

    @Test fun everydayTangerineNamesMatchWholeFruit() {
        assertEquals(listOf("tangerine"), FoodSearchMatching.englishQueries(listOf("橘子", "桔子")))
        assertTrue(FoodSearchMatching.englishMatch("tangerine", "Mandarin seedless peeled raw").canAutoAccept)
        assertTrue(FoodSearchMatching.englishMatch("tangerine", "Tangerines mandarin oranges raw").canAutoAccept)
        assertFalse(FoodSearchMatching.englishMatch("tangerine", "Tangerine juice raw").canAutoAccept)
        assertFalse(FoodSearchMatching.englishMatch("tangerine", "Tangerines canned light syrup pack").canAutoAccept)
    }
}
