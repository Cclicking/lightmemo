package com.foodcalorie.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foodcalorie.app.domain.FoodLog
import com.foodcalorie.app.domain.FoodComponent
import com.foodcalorie.app.domain.ComponentSource
import com.foodcalorie.app.domain.NutritionReference
import com.foodcalorie.app.domain.MealType
import com.foodcalorie.app.domain.Nutrition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.foodStore by preferencesDataStore(name = "food_logs")

class FoodLogRepository(private val context: Context) {
    private val key = stringPreferencesKey("logs_json")

    val logs: Flow<List<FoodLog>> = context.foodStore.data.map { prefs ->
        parse(prefs[key] ?: "[]")
    }

    suspend fun insert(log: FoodLog): Long {
        val existing = readAll()
        val id = (existing.maxOfOrNull { it.id } ?: 0L) + 1
        writeAll(existing + log.copy(id = id))
        return id
    }

    suspend fun deleteById(id: Long) {
        writeAll(readAll().filterNot { it.id == id })
    }

    suspend fun readAll(): List<FoodLog> {
        val prefs = context.foodStore.data.first()
        return parse(prefs[key] ?: "[]")
    }

    private suspend fun writeAll(list: List<FoodLog>) {
        val json = JSONArray()
        list.forEach { log ->
            json.put(
                JSONObject().apply {
                    put("id", log.id)
                    put("name", log.name)
                    put("mealType", log.mealType.name)
                    put("grams", log.grams)
                    put("caloriesKcal", log.nutrition.caloriesKcal)
                    put("proteinG", log.nutrition.proteinG)
                    put("carbsG", log.nutrition.carbsG)
                    put("fatG", log.nutrition.fatG)
                    put("components", JSONArray().apply {
                        log.components.forEach { component -> put(component.toJson()) }
                    })
                    if (log.imageUri == null) put("imageUri", JSONObject.NULL) else put("imageUri", log.imageUri)
                    put("dateEpochDay", log.dateEpochDay)
                    put("createdAtMillis", log.createdAtMillis)
                    if (log.mealMinuteOfDay == null) put("mealMinuteOfDay", JSONObject.NULL) else put("mealMinuteOfDay", log.mealMinuteOfDay)
                    if (log.note == null) put("note", JSONObject.NULL) else put("note", log.note)
                    put("mealTags", JSONArray().apply { log.mealTags.forEach { tag -> put(tag) } })
                },
            )
        }
        context.foodStore.edit { prefs -> prefs[key] = json.toString() }
    }

    private fun parse(raw: String): List<FoodLog> {
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val image = if (o.isNull("imageUri")) null else o.optString("imageUri")
                add(
                    FoodLog(
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
                    ),
                )
            }
        }
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
}
