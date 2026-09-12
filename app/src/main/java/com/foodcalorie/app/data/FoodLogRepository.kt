package com.foodcalorie.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foodcalorie.app.domain.FoodLog
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
                    if (log.imageUri == null) put("imageUri", JSONObject.NULL) else put("imageUri", log.imageUri)
                    put("dateEpochDay", log.dateEpochDay)
                    put("createdAtMillis", log.createdAtMillis)
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
                        imageUri = image,
                        dateEpochDay = o.getLong("dateEpochDay"),
                        createdAtMillis = o.getLong("createdAtMillis"),
                    ),
                )
            }
        }
    }
}
