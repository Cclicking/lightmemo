package com.click.lightmemo.mcp

import com.click.lightmemo.data.FoodLogRepository
import com.click.lightmemo.data.AppSettings
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.Nutrition
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

fun interface NutritionProvider {
    suspend fun today(): JSONObject
}

class LocalNutritionProvider(private val repository: FoodLogRepository,
    private val settings: suspend () -> AppSettings) : NutritionProvider {
    override suspend fun today(): JSONObject {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        return nutritionResult(repository.readRange(date.toEpochDay(), date.toEpochDay()), date, zone, settings())
    }
}

internal fun nutritionResult(logs: List<FoodLog>, date: LocalDate, zone: ZoneId,
    settings: AppSettings = AppSettings()): JSONObject {
    // The stored nutrition already accounts for the portion, just as on the Today page.
    val total = logs.fold(Nutrition()) { sum, log -> sum + log.nutrition }
    return JSONObject().apply {
        put("schema_version", 1)
        put("date", date.toString())
        put("timezone", zone.id)
        put("source", "qingshiji")
        put("is_mock", false)
        put("status", if (logs.isEmpty()) "no_records" else "ok")
        put("calories_kcal", total.caloriesKcal)
        put("protein_g", total.proteinG)
        put("carbohydrates_g", total.carbsG)
        put("fat_g", total.fatG)
        // Use target settings: manual macro targets fall back to profile recommendations.
        val targets = JSONObject()
        val missing = JSONArray()
        fun target(name: String, value: Float) {
            if (value.isFinite() && value > 0f) targets.put(name, value.toDouble())
            else { targets.put(name, JSONObject.NULL); missing.put("targets.$name") }
        }
        target("calories_kcal", settings.dailyCalorieTarget)
        target("protein_g", settings.effectiveProteinG)
        target("carbohydrates_g", settings.effectiveCarbsG)
        target("fat_g", settings.effectiveFatG)
        put("targets", targets)
        put("missing_fields", missing)
        put("message", if (logs.isEmpty()) "今天尚未记录饮食，不代表没有摄入。" else "仅包含轻食记今天已记录的饮食摄入。")
    }
}
