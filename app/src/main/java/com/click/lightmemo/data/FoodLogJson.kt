package com.click.lightmemo.data

import com.click.lightmemo.domain.ComponentSource
import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.domain.NutritionReference
import org.json.JSONArray
import org.json.JSONObject

internal fun FoodLog.toJsonLog(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("mealType", mealType.name)
    put("grams", grams)
    put("caloriesKcal", nutrition.caloriesKcal)
    put("proteinG", nutrition.proteinG)
    put("carbsG", nutrition.carbsG)
    put("fatG", nutrition.fatG)
    put("components", JSONArray().apply {
        components.forEach { component -> put(component.toJson()) }
    })
    if (imageUri == null) put("imageUri", JSONObject.NULL) else put("imageUri", imageUri)
    put("dateEpochDay", dateEpochDay)
    put("createdAtMillis", createdAtMillis)
    if (mealMinuteOfDay == null) put("mealMinuteOfDay", JSONObject.NULL) else put("mealMinuteOfDay", mealMinuteOfDay)
    if (note == null) put("note", JSONObject.NULL) else put("note", note)
    put("mealTags", JSONArray().apply { mealTags.forEach { tag -> put(tag) } })
}

internal fun parseFoodLog(o: JSONObject): FoodLog {
    val image = if (o.isNull("imageUri")) null else o.optString("imageUri")
    return FoodLog(
        id = o.getLong("id"),
        name = o.getString("name"),
        mealType = MealType.valueOf(o.getString("mealType")),
        grams = o.getDouble("grams"),
        nutrition = Nutrition(
            caloriesKcal = o.getDouble("caloriesKcal"),
            proteinG = o.getDouble("proteinG"),
            carbsG = o.getDouble("carbsG"),
            fatG = o.getDouble("fatG"),
        ),
        components = o.optJSONArray("components")?.let(::parseComponents).orEmpty(),
        imageUri = image,
        dateEpochDay = o.getLong("dateEpochDay"),
        createdAtMillis = o.getLong("createdAtMillis"),
        mealMinuteOfDay = if (o.isNull("mealMinuteOfDay")) null else o.optInt("mealMinuteOfDay"),
        note = if (o.isNull("note")) null else o.optString("note").takeIf { it.isNotBlank() },
        mealTags = o.optJSONArray("mealTags")?.let { tags ->
            buildList { for (i in 0 until tags.length()) add(tags.getString(i)) }
        }.orEmpty(),
    )
}

internal fun serializeFoodLogs(list: List<FoodLog>): String {
    val json = JSONArray()
    list.forEach { log -> json.put(log.toJsonLog()) }
    return json.toString()
}

internal fun parseFoodLogs(raw: String): List<FoodLog> {
    val arr = JSONArray(raw)
    return buildList {
        for (i in 0 until arr.length()) {
            add(parseFoodLog(arr.getJSONObject(i)))
        }
    }
}

internal fun serializeComponents(components: List<FoodComponent>): String {
    val json = JSONArray()
    components.forEach { json.put(it.toJson()) }
    return json.toString()
}

internal fun parseComponentsJson(raw: String): List<FoodComponent> {
    if (raw.isBlank()) return emptyList()
    return parseComponents(JSONArray(raw))
}

internal fun serializeMealTags(tags: List<String>): String {
    val json = JSONArray()
    tags.forEach { json.put(it) }
    return json.toString()
}

internal fun parseMealTagsJson(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    val tags = JSONArray(raw)
    return buildList { for (i in 0 until tags.length()) add(tags.getString(i)) }
}

private fun FoodComponent.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("databaseQuery", databaseQuery)
    put("chinaDatabaseQuery", chinaDatabaseQuery)
    put("source", source.name)
    put("estimatedWeightG", estimatedWeightG)
    put("weightMinG", weightMinG)
    put("weightMaxG", weightMaxG)
    put("confidence", confidence)
    put("needsConfirmation", needsConfirmation)
    nutritionReference?.let { reference ->
        put("nutritionReference", JSONObject().apply {
            put("sourceId", reference.sourceId)
            put("description", reference.description)
            put("dataType", reference.dataType)
            put("caloriesKcal", reference.per100g.caloriesKcal)
            put("proteinG", reference.per100g.proteinG)
            put("carbsG", reference.per100g.carbsG)
            put("fatG", reference.per100g.fatG)
        })
    }
}

private fun parseComponents(array: JSONArray): List<FoodComponent> = buildList {
    for (index in 0 until array.length()) {
        val component = array.getJSONObject(index)
        val reference = component.optJSONObject("nutritionReference")?.let { value ->
            NutritionReference(
                sourceId = value.optString("sourceId").ifBlank {
                    value.optLong("fdcId").toString()
                },
                description = value.optString("description"),
                dataType = value.optString("dataType"),
                per100g = Nutrition(
                    caloriesKcal = value.optDouble("caloriesKcal"),
                    proteinG = value.optDouble("proteinG"),
                    carbsG = value.optDouble("carbsG"),
                    fatG = value.optDouble("fatG"),
                ),
            )
        }
        add(
            FoodComponent(
                id = component.optString("id"),
                name = component.optString("name"),
                databaseQuery = component.optString("databaseQuery"),
                chinaDatabaseQuery = component.optString("chinaDatabaseQuery")
                    .ifBlank { component.optString("name") },
                source = runCatching {
                    ComponentSource.valueOf(component.optString("source"))
                }.getOrDefault(ComponentSource.VISIBLE),
                estimatedWeightG = component.optDouble("estimatedWeightG"),
                weightMinG = component.optDouble("weightMinG"),
                weightMaxG = component.optDouble("weightMaxG"),
                confidence = component.optDouble("confidence"),
                needsConfirmation = component.optBoolean("needsConfirmation"),
                nutritionReference = reference,
            ),
        )
    }
}
