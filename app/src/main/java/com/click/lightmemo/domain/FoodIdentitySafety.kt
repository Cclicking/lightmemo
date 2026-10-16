package com.click.lightmemo.domain

/** Conservative identity checks shared by database matching and automatic memory reuse.
 * Unknown foods remain unknown; a word mentioned as an ingredient cannot establish identity.
 */
internal object FoodIdentitySafety {
    private val identities = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val punctuation = Regex("[^a-z0-9]+")
    private val plainWaterWords = setOf("water", "ice", "beverage", "beverages", "bottled", "bottle", "bottles", "tap", "plain",
        "generic", "non", "carbonated", "drinking", "mineral", "spring", "purified", "distilled", "baby", "naya")
    private val boundary = Regex("\\b(?:with|in|on|containing|includes|made|prepared)\\b")
    private val compounds = listOf(
        "sea cucumber" to "seacucumber", "sweet potato leaves" to "sweetpotatoleaf",
        "sweet potato leaf" to "sweetpotatoleaf", "sweet potato" to "sweetpotato",
        "broccoli chinese" to "chinesebroccoli", "chinese broccoli" to "chinesebroccoli",
        "broccoli raab" to "broccoliraab", "broccoli rabe" to "broccoliraab",
        "rice noodle" to "ricenoodle", "ice cream" to "icecream",
        "cherry tomato" to "tomato", "wood ear" to "woodear",
        "soy sauce" to "soysauce", "soy based sauce" to "soysauce",
        "soy milk" to "soymilk", "almond milk" to "almondmilk",
        "soy beverage" to "soymilk", "almond beverage" to "almondmilk",
        "peanut butter" to "peanutbutter", "cocoa butter" to "cocoabutter",
        "water convolvulus" to "waterconvolvulus", "water chestnut" to "waterchestnut",
        "coconut water" to "coconutwater",
    ).map { (phrase, id) -> Regex("\\b$phrase\\b") to id }
    private val anchors = ("water ice icecream oil butter margarine spread milk soymilk almondmilk yogurt cheese " +
        "egg custard pudding quail chicken beef pork lamb duck turkey fish shrimp crab salmon tuna " +
        "sausage frankfurter bratwurst bologna ham bacon meatball hamburger burger bun roll bread " +
        "rice ricenoodle noodle pasta corn potato sweetpotato sweetpotatoleaf tofu mushroom woodear " +
        "broccoli chinesebroccoli broccoliraab cucumber seacucumber pepper tomato cherry carrot onion " +
        "cabbage lettuce spinach chive scallion eggplant zucchini bean lentil pea chickpea " +
        "sauce soysauce mayonnaise soup juice cola coffee tea orange tangerine apple banana grape pear " +
        "flour sugar salt peanutbutter cocoabutter").split(' ').toSet()
    private val aliases = mapOf("frankfurter" to "sausage", "bratwurst" to "sausage", "bologna" to "sausage",
        "burger" to "hamburger", "roll" to "bun", "ice" to "water", "cherry" to "cherry")
    private val singulars = mapOf("potatoes" to "potato", "tomatoes" to "tomato", "leaves" to "leaf",
        "cherries" to "cherry", "fries" to "potato", "quails" to "quail", "creams" to "cream", "mandarin" to "tangerine")
    private val chinese = mapOf(
        "西兰花" to "broccoli", "西蓝花" to "broccoli", "芥蓝" to "chinesebroccoli",
        "土豆" to "potato", "马铃薯" to "potato", "红薯" to "sweetpotato", "甘薯" to "sweetpotato",
        "番薯" to "sweetpotato", "鸡肉" to "chicken", "鸡" to "chicken", "猪肉" to "pork", "牛肉" to "beef",
        "鸡蛋" to "egg", "鹌鹑蛋" to "egg", "黑木耳" to "woodear", "木耳" to "woodear",
        "黄瓜" to "cucumber", "青椒" to "pepper", "番茄" to "tomato", "圣女果" to "tomato",
        "米饭" to "rice", "白米饭" to "rice", "豆腐" to "tofu", "北豆腐" to "tofu", "南豆腐" to "tofu", "玉米" to "corn",
        "牛奶" to "milk", "纯牛奶" to "milk", "全脂牛奶" to "milk", "酸奶" to "yogurt", "风味酸牛奶" to "yogurt",
        "水" to "water", "冰块" to "water", "冰" to "water", "食用油" to "oil", "植物油" to "oil",
        "汉堡胚" to "bun", "汉堡面包" to "bun", "汉堡面包胚" to "bun",
    )

    private fun normalized(value: String) = value.lowercase(java.util.Locale.ROOT)
        .replace(punctuation, " ").trim().split(' ').joinToString(" ") {
            singulars[it] ?: if (it.endsWith("s") && it.dropLast(1) in anchors) it.dropLast(1) else it
        }

    fun identity(value: String): String? {
        identities[value]?.let { return it.takeIf(String::isNotEmpty) }
        val result = identify(value)
        if (identities.size >= 32768) identities.clear()
        identities[value] = result.orEmpty()
        return result
    }

    private fun identify(value: String): String? {
        if (value.any { it in '\u4e00'..'\u9fff' }) {
            val name = value.replace(Regex("[（(].*?[）)]"), "").trim()
                .replace(Regex("^(?:水煮|红烧|清蒸|熟|蒸|煮|炒|煎|烤)"), "")
                .replace(Regex("(?:切片|切块|片|块|丁|丝|炖|炒)$"), "")
            chinese[name]?.let { return it }
            if (name.endsWith("酱汁") || name.endsWith("红油酱") || name == "汉堡酱") return "sauce"
            return null
        }
        var head = boundary.split(normalized(value), limit = 2).first()
        compounds.forEach { (phrase, id) -> head = head.replace(phrase, id) }
        val words = head.split(' ')
        if ("sweetpotato" in words && "leaf" in words) return "sweetpotatoleaf"
        // Form defines the food, even when a species is also present (pork sausage, egg custard).
        if (words.any { it in setOf("sausage", "frankfurter", "bratwurst", "bologna") }) return "sausage"
        if ("spread" in words) return "spread"
        if ("custard" in words) return "custard"
        if ("pudding" in words) return "pudding"
        if ("bacon" in words) return "bacon"
        if ("ham" in words) return "ham"
        if ("mayonnaise" in words) return "mayonnaise"
        if (words.any { it in setOf("liver", "kidney", "heart", "gizzard") }) {
            return words.firstOrNull { it in setOf("pork", "beef", "chicken", "duck") }.orEmpty() +
                words.first { it in setOf("liver", "kidney", "heart", "gizzard") }
        }
        if ("chicken" in words && words.any { it in setOf("meatless", "vegetarian", "mock", "soy") }) return "plantbasedchicken"
        if ("egg" in words && "quail" in words) return "egg"
        if ("bun" in words && "hamburger" in words) return "bun"
        return words.firstOrNull { it in anchors || compounds.any { (_, id) -> id == it } }?.let { aliases[it] ?: it }
    }

    fun agrees(query: String, description: String): Boolean {
        val requested = identity(query) ?: return true
        val actual = identity(description) ?: return description.any { it in '\u4e00'..'\u9fff' }
        if (requested == "water" && (normalized(query).split(' ').filter(String::isNotBlank).toSet() - plainWaterWords).isEmpty() &&
            (normalized(description).split(' ').toSet() - plainWaterWords).isNotEmpty()) return false
        // A soy sauce is a sauce, but plain soy sauce must not become an arbitrary sauce.
        return requested == actual || requested == "sauce" && actual == "soysauce"
    }

    fun allowsReference(component: FoodComponent, reference: NutritionReference): Boolean {
        val inputs = listOf(component.name, component.databaseQuery, component.chinaDatabaseQuery).filter { it.isNotBlank() }
        if (!inputs.all { agrees(it, reference.description) }) return false
        val requested = normalized(inputs.joinToString(" ")).split(' ').toSet()
        val actual = normalized(reference.description).split(' ').toSet()
        val forms = setOf("breaded", "battered", "candied", "cured", "meatless", "vegetarian", "imitation", "substitute")
        return (actual.intersect(forms) - requested).isEmpty()
    }
}
