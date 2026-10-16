package com.click.lightmemo.network

import com.click.lightmemo.domain.FoodIdentitySafety

/** Candidate recall is deliberately broader than permission to use its nutrition automatically. */
internal data class FoodSearchMatch(val score: Double, val canAutoAccept: Boolean)

internal object FoodSearchMatching {
    private val englishPunctuation = Regex("[^a-z0-9]+")
    private val chinesePunctuation = Regex("[^\\p{L}\\p{N}]+")
    private val brackets = Regex("[（(【［\\[]([^）)】］\\]]+)[）)】］\\]]")
    private val aliasBrackets = setOf('[', '［', '【')
    private val aliasSeparators = Regex("[、,，;；/]+")
    private val shapeSuffixes = listOf("切成小块", "切小块", "切成块", "切成片", "切成丝", "切成丁", "切块", "切片", "切丝", "切丁", "小块", "块", "片", "丁", "丝", "条", "段", "粒")
    private val intrinsicPrefixNames = setOf("生菜", "生姜", "生抽", "熟地黄")
    private val optionalChineseDescriptors = setOf("鲜", "新鲜", "代表值", "均值", "平均值")
    // These are distinct foods, despite sharing an English word with a common vegetable.
    private val distinctEnglishFoods = listOf(
        Regex("\\bsea cucumber\\b") to "seacucumber",
        Regex("\\bbroccoli (?:raab|rabe)\\b") to "broccoliraab",
        Regex("\\bcabbage rape\\b") to "broccoliraab",
        Regex("\\b(?:soy|vegetarian|mock|meatless) chicken\\b") to "plantbasedchicken",
        Regex("\\bmandarin orange\\b") to "tangerine",
    )
    private val states = setOf("raw", "cooked", "steamed", "boiled", "fried", "roasted", "baked", "grilled", "dried", "braised", "stewed", "broiled", "sauteed", "microwaved")
    private val methods = states - setOf("raw", "cooked")
    private val optionalWords = setOf("fresh", "food", "the", "and", "sliced", "diced", "chopped", "pieces", "slice", "cubed")
    private val wordAliases = mapOf(
        "eggs" to "egg", "whites" to "white", "yolks" to "yolk", "potatoes" to "potato",
        "tomatoes" to "tomato", "noodles" to "noodle", "beans" to "bean", "peas" to "pea",
        "carrots" to "carrot", "onions" to "onion", "mushrooms" to "mushroom",
        "peppers" to "pepper", "breasts" to "breast", "thighs" to "thigh",
        "uncooked" to "raw", "steaming" to "steamed", "steam" to "steamed",
        "boiling" to "boiled", "boil" to "boiled", "frying" to "fried", "fry" to "fried",
        "roast" to "roasted", "bake" to "baked", "grill" to "grilled",
        "aubergine" to "eggplant", "aubergines" to "eggplant", "scallions" to "scallion",
        "garbanzo" to "chickpea", "chickpeas" to "chickpea",
        "apples" to "apple", "bananas" to "banana", "oranges" to "orange",
        "pears" to "pear", "grapes" to "grape", "strawberries" to "strawberry",
        "peanuts" to "peanut", "almonds" to "almond", "walnuts" to "walnut",
        "lentils" to "lentil", "oats" to "oat", "cucumbers" to "cucumber",
        "courgette" to "zucchini", "courgettes" to "zucchini", "prawns" to "shrimp",
        "prawn" to "shrimp", "shrimps" to "shrimp",
        "tangerines" to "tangerine", "mandarins" to "mandarin",
        "chives" to "chive", "creams" to "cream", "leaves" to "leaf", "custards" to "custard",
        "cherries" to "cherry", "rolls" to "roll", "buns" to "bun",
    )
    private val chineseAliases = mapOf(
        "西蓝花" to "西兰花", "绿花菜" to "西兰花", "绿菜花" to "西兰花", "青花菜" to "西兰花",
        "马铃薯" to "土豆", "洋芋" to "土豆", "西红柿" to "番茄", "番薯" to "甘薯",
        "红薯" to "甘薯", "地瓜" to "甘薯", "包菜" to "结球甘蓝", "卷心菜" to "结球甘蓝",
        "橘子" to "橘", "桔子" to "橘",
    )
    // Query-only expansion: 青椒 can mean either sweet green pepper or pointed hot green pepper.
    private val chineseQueryAliases = mapOf(
        "青椒" to listOf("甜椒", "辣椒青尖"),
        "青尖椒" to listOf("辣椒青尖"),
    )
    private val chineseStates = mapOf(
        "生" to "raw", "熟" to "cooked", "蒸" to "steamed", "煮" to "boiled",
        "水煮" to "boiled", "炒" to "fried", "煎" to "fried", "油炸" to "fried",
        "炸" to "fried", "烤" to "roasted", "干制" to "dried",
    )
    // Exact identities only: a substring such as 鸡蛋 in 鸡蛋面 must never translate to egg.
    private val bilingualNames = mapOf(
        "米饭" to "rice cooked", "白米饭" to "rice white cooked", "大米" to "rice raw",
        "鸡蛋" to "egg whole", "蛋清" to "egg white", "蛋黄" to "egg yolk",
        "鸡肉" to "chicken", "鸡胸肉" to "chicken breast", "牛肉" to "beef", "猪肉" to "pork",
        "北豆腐" to "tofu firm", "南豆腐" to "tofu soft", "豆腐" to "tofu",
        "西兰花" to "broccoli", "土豆" to "potato", "番茄" to "tomato",
        "甘薯" to "sweet potato", "结球甘蓝" to "cabbage", "茄子" to "eggplant",
        "胡萝卜" to "carrot", "黄瓜" to "cucumber", "洋葱" to "onion",
        "青椒" to "pepper green", "青尖椒" to "pepper hot green", "甜椒" to "pepper sweet",
        "橘" to "tangerine",
        "水" to "water", "冰块" to "water", "冰" to "water", "食用油" to "vegetable oil", "植物油" to "vegetable oil",
        "牛奶" to "milk", "纯牛奶" to "milk whole", "鹌鹑蛋" to "quail egg",
        "汉堡胚" to "hamburger bun", "汉堡面包" to "hamburger bun",
    )
    private val transformedFoods = setOf("soup", "sauce", "flour", "powder", "juice", "bread", "pudding", "oil", "dried",
        "pickle", "pickles", "pickled", "floss", "jerky", "canned", "preserved", "syrup")
    private val identityQualifiers = setOf("breast", "thigh", "white", "brown", "whole", "firm", "soft", "plain",
        "skim", "nonfat", "skinless", "boneless", "sweetened", "unsweetened")

    fun english(value: String): String = value.lowercase(java.util.Locale.ROOT)
        .replace("stir-fried", "fried").replace("stir fried", "fried")
        .replace("green onions", "scallion").replace("green onion", "scallion")
        .replace(englishPunctuation, " ").trim().split(' ')
        .filter { it.isNotBlank() && it !in optionalWords }
        .joinToString(" ") { wordAliases[it] ?: it }

    fun chinese(value: String): String {
        var text = value.lowercase(java.util.Locale.ROOT).replace(chinesePunctuation, "")
        chineseAliases.forEach { (alias, name) -> text = text.replace(alias, name) }
        return text
    }

    private fun terms(value: String): Set<String> {
        val identityText = distinctEnglishFoods.fold(value) { text, (phrase, identity) -> text.replace(phrase, identity) }
        return identityText.split(' ').filter { it.isNotBlank() }.map { if (it == "mandarin") "tangerine" else it }.toSet()
    }

    fun englishQueries(inputs: List<String>): List<String> = inputs.flatMap { input ->
        if (input.none { it in '\u4e00'..'\u9fff' }) listOf(english(input).let { if (it == "ice") "water" else it }) else {
            chineseIdentities(input, stripShape = true).mapNotNull { identity ->
                bilingualNames[identity]?.let { name ->
                    val state = chineseState(input)
                    if (state.isEmpty()) name else (terms(name) - states + state).joinToString(" ")
                }
            }
        }
    }.filter { it.isNotBlank() }.distinct()

    private fun compatible(requested: Set<String>, actual: Set<String>, requireState: Boolean): Boolean {
        if ("raw" in requested && actual.any { it in states - "raw" }) return false
        if (requested.any { it in states - "raw" } && "raw" in actual) return false
        val requestedMethods = requested.intersect(methods)
        val actualMethods = actual.intersect(methods)
        if (requestedMethods.size > 1) return false
        if (requestedMethods.isNotEmpty() && actualMethods.isNotEmpty() && requestedMethods != actualMethods) return false
        return !requireState || requested.isEmpty() || actual.isNotEmpty()
    }

    fun statesAgree(queries: List<String>, description: String): Boolean = compatible(
        queries.flatMap { terms(english(it)).intersect(states) }.toSet(),
        terms(english(description)).intersect(states), requireState = true,
    )

    /** A broader alternate query must not erase a requested cut, variety, or processing detail. */
    fun englishConstraintsAgree(queries: List<String>, description: String): Boolean {
        val required = queries.flatMap { terms(english(it)).intersect(identityQualifiers + transformedFoods) }.toSet()
        return terms(english(description)).containsAll(required) && statesAgree(queries, description) &&
            queries.all { FoodIdentitySafety.agrees(it, description) }
    }

    /** Survey recipes may include fat or toppings that the recognized ingredient does not. */
    fun surveyRecipeAgrees(query: String, description: String): Boolean {
        val wanted = english(query)
        val actual = english(description)
        val recipeForms = setOf("sandwich", "pizza", "omelet", "scrambled", "salad", "casserole", "stew", "burger")
        if ((terms(actual).intersect(recipeForms) - terms(wanted)).isNotEmpty()) return false
        if (Regex("\\bnfs\\b|\\bns as to\\b").containsMatchIn(actual)) return false
        val additions = actual.substringAfter(" with ", "")
        if (additions.isNotBlank() && !terms(wanted).containsAll(terms(additions) - states)) return false
        for (ingredient in listOf("fat", "sugar")) {
            val withoutNegation = actual.replace("no added $ingredient", "").replace("no $ingredient added", "")
            if (("$ingredient added" in withoutNegation || "added $ingredient" in withoutNegation) &&
                !("$ingredient added" in wanted || "added $ingredient" in wanted)) return false
        }
        return true
    }

    fun chineseStatesAgree(inputs: List<String>, queries: List<String>, name: String, englishName: String): Boolean = compatible(
        inputs.flatMap { chineseState(it) }.toSet() + queries.flatMap { terms(english(it)).intersect(states) },
        chineseState(name) + terms(english(englishName)).intersect(states), requireState = true,
    )

    fun englishMatch(query: String, description: String): FoodSearchMatch {
        val normalizedQuery = english(query)
        val normalizedDescription = english(description)
        val wanted = terms(normalizedQuery)
        val actual = terms(normalizedDescription)
        val identity = wanted - states
        val overlap = identity.intersect(actual).size
        if (identity.isEmpty() || overlap == 0) return FoodSearchMatch(0.0, false)
        val safe = FoodIdentitySafety.agrees(normalizedQuery, normalizedDescription) && actual.containsAll(identity) &&
            preparationAgrees(normalizedQuery, normalizedDescription) &&
            compatible(wanted.intersect(states), actual.intersect(states), requireState = true) &&
            (actual.intersect(transformedFoods) - wanted).isEmpty() &&
            (actual.intersect(setOf("breaded", "battered", "meatless", "vegetarian", "imitation", "substitute", "spread", "custard")) - wanted).isEmpty() &&
            !("mayonnaise" in wanted && actual.any { it in setOf("tofu", "fatfree", "nonfat") }) &&
            !("cola" in wanted && "diet" in actual && "diet" !in wanted)
        val coverage = overlap.toDouble() / identity.size
        val stateCoverage = wanted.intersect(states).count { it in actual } * 0.15
        val exact = if (normalizedQuery == normalizedDescription) 1.0 else 0.0
        val identityBonus = if (safe) 0.3 else 0.0
        return FoodSearchMatch(coverage + stateCoverage + exact + identityBonus, safe)
    }

    private fun preparationAgrees(query: String, description: String): Boolean {
        val unwantedForms = setOf("breaded", "battered", "candied", "cured", "industrial", "platter", "restaurant", "sandwich")
        if ((terms(description).intersect(unwantedForms) - terms(query)).isNotEmpty()) return false
        val restricted = listOf("fat free", "nonfat", "fatfree", "low calorie", "reduced calorie", "reduced fat", "light", "diet")
        if (restricted.any { Regex("\\b$it\\b").containsMatchIn(description) && !Regex("\\b$it\\b").containsMatchIn(query) }) return false
        // USDA distinguishes the skin itself from flesh cooked in its skin.
        if ("skin" !in terms(query) && ("skin only" in description ||
                Regex("\\bskin (?:with|without) salt$").containsMatchIn(description) && "flesh" !in terms(description))) return false
        return true
    }

    private fun chineseState(value: String): Set<String> = buildSet {
        // Only bracketed/prefix cooking descriptions; 生菜、花生、干酪 are food identities.
        brackets.findAll(value).forEach { match ->
            match.groupValues[1].split(aliasSeparators).forEach { part -> chineseStates[part]?.let { add(it) } }
        }
        cookingPrefix(value)?.let { add(chineseStates.getValue(it)) }
        if (value.contains("米饭")) add("cooked")
    }

    private fun cookingPrefix(value: String): String? {
        if (intrinsicPrefixNames.any { value.startsWith(it) }) return null
        return chineseStates.keys.sortedByDescending { it.length }.firstOrNull {
            value.startsWith(it) && value.length > it.length
        }
    }

    private fun normalizeChineseIdentity(value: String, stripShape: Boolean): String {
        val identity = chinese(value.removePrefix(cookingPrefix(value).orEmpty()))
        if (!stripShape) return identity
        // Shape words may be discarded only when the remainder is a known whole food.
        // 鸡肉松、鸡蛋面、豆腐干 therefore retain their identity and processing distinctions.
        val suffix = shapeSuffixes.firstOrNull { suffix ->
            identity.endsWith(suffix) && identity.removeSuffix(suffix).let {
                it in bilingualNames || it in chineseQueryAliases
            }
        }
        return if (suffix == null) identity else identity.removeSuffix(suffix)
    }

    private fun chineseIdentities(value: String, stripShape: Boolean = false): List<String> {
        // Round brackets often encode a nutritional distinction (脱脂、去皮), not a synonym.
        val main = brackets.replace(value) { match ->
            if (match.groupValues[1].split(aliasSeparators).all { it in chineseStates || it in optionalChineseDescriptors } ||
                match.value.first() in aliasBrackets) "" else match.value
        }
        val aliases = brackets.findAll(value).filter { it.value.first() in aliasBrackets }
            .flatMap { it.groupValues[1].split(aliasSeparators).asSequence() }
            .filter { it !in chineseStates }.toList()
        return (listOf(main) + aliases).map { normalizeChineseIdentity(it.trim(), stripShape) }
            .filter { it.isNotBlank() }.distinct()
    }

    fun chineseMatch(query: String, name: String, englishQuery: String = "", englishName: String = ""): FoodSearchMatch {
        if (query.none { it in '\u4e00'..'\u9fff' }) return FoodSearchMatch(0.0, false)
        val requested = chineseIdentities(query, stripShape = true).flatMap { listOf(it) + chineseQueryAliases[it].orEmpty() }.distinct()
        val actual = chineseIdentities(name)
        val exact = requested.any { it in actual }
        val score = if (exact) 2.0 - minOf(requested.indexOfFirst { it in actual }, 3) * 0.1 else requested.maxOfOrNull { q ->
            // Aliases are whole names: a beef cut named 黄瓜条 must not be recalled as 黄瓜.
            actual.take(1).maxOfOrNull { c ->
                if (q.contains(c) || c.contains(q)) minOf(q.length, c.length).toDouble() / maxOf(q.length, c.length)
                else q.windowed(2).toSet().let { pairs ->
                    if (pairs.isEmpty()) 0.0 else pairs.intersect(c.windowed(2).toSet()).size.toDouble() / pairs.size
                }
            } ?: 0.0
        } ?: 0.0
        val wantedState = chineseState(query) + terms(english(englishQuery)).intersect(states)
        val actualState = chineseState(name) + terms(english(englishName)).intersect(states)
        val englishIdentityAgrees = englishQuery.isBlank() || englishName.isBlank() ||
            englishMatch(englishQuery, englishName).canAutoAccept
        return FoodSearchMatch(score, exact && englishIdentityAgrees && compatible(wantedState, actualState, requireState = true))
    }
}
