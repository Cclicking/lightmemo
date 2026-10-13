package com.click.lightmemo.mcp

import com.click.lightmemo.data.FoodLogRepository
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.Nutrition
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

fun interface NutritionProvider {
    suspend fun today(): JSONObject
}

class LocalNutritionProvider(private val repository: FoodLogRepository) : NutritionProvider {
    override suspend fun today(): JSONObject {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        return nutritionResult(repository.readRange(date.toEpochDay(), date.toEpochDay()), date, zone)
    }
}

internal fun nutritionResult(logs: List<FoodLog>, date: LocalDate, zone: ZoneId): JSONObject {
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
        put("missing_fields", JSONArray())
        put("message", if (logs.isEmpty()) "今天尚未记录饮食，不代表没有摄入。" else "仅包含轻食记今天已记录的饮食摄入。")
    }
}
