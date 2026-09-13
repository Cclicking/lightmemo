package com.foodcalorie.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private val Context.foodStore by preferencesDataStore(name = "food_logs")

class FoodLogRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.foodStore)
    private val key = stringPreferencesKey("logs_json")
    private val nextIdKey = longPreferencesKey("next_id")
    val readError = MutableStateFlow<String?>(null)

    val logs: Flow<List<FoodLog>> = store.data.map { prefs ->
        parse(prefs[key] ?: "[]").also { readError.value = null }
    }.flowOn(Dispatchers.IO).retryWhen { cause, _ ->
        readError.value = "记录读取失败，原数据已保留，正在重试：${cause.message.orEmpty()}"
        delay(5_000)
        true
    }

    suspend fun insert(log: FoodLog): Long = insertAll(listOf(log)).single()

    suspend fun insertAll(logs: List<FoodLog>): List<Long> = withContext(Dispatchers.IO) {
        logs.forEach { it.validate() }
        var ids = emptyList<Long>()
        store.edit { prefs ->
            val existing = parse(prefs[key] ?: "[]")
            var nextId = maxOf(prefs[nextIdKey] ?: 1L, (existing.maxOfOrNull { it.id } ?: 0L) + 1L)
            val additions = logs.map { it.copy(id = nextId++) }
            ids = additions.map { it.id }
            prefs[key] = serialize(existing + additions)
            prefs[nextIdKey] = nextId
        }
        return@withContext ids
    }

    suspend fun deleteById(id: Long): FoodLog? = withContext(Dispatchers.IO) {
        var deleted: FoodLog? = null
        store.edit { prefs ->
            val existing = parse(prefs[key] ?: "[]")
            deleted = existing.firstOrNull { it.id == id }
            prefs[key] = serialize(existing.filterNot { it.id == id })
        }
        return@withContext deleted
    }

    suspend fun restore(log: FoodLog) = withContext(Dispatchers.IO) {
        log.validate()
        store.edit { prefs ->
            val existing = parse(prefs[key] ?: "[]")
            require(existing.none { it.id == log.id }) { "该记录已存在" }
            prefs[key] = serialize(existing + log)
        }
    }

    suspend fun update(log: FoodLog) = withContext(Dispatchers.IO) {
        log.validate()
        store.edit { prefs ->
            val existing = parse(prefs[key] ?: "[]")
            require(existing.any { it.id == log.id }) { "该记录已删除，请刷新后重试" }
            prefs[key] = serialize(existing.map { if (it.id == log.id) log else it })
        }
    }

    suspend fun exportJson(): String = JSONObject().apply {
        put("format", "food-calorie-logs")
        put("version", 1)
        put("logs", JSONArray(serialize(readAll().map { it.copy(imageUri = null) })))
    }.toString(2)

    suspend fun importJson(raw: String): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(raw)
        require(root.getString("format") == "food-calorie-logs" && root.getInt("version") == 1) {
            "不支持的备份格式或版本"
        }
        val incoming = parse(root.getJSONArray("logs").toString()).map { it.copy(imageUri = null) }
        incoming.forEach { it.validate() }
        var count = 0
        store.edit { prefs ->
            val existing = parse(prefs[key] ?: "[]")
            val fingerprints = existing.map { it.copy(id = 0, imageUri = null) }.toMutableSet()
            var nextId = maxOf(prefs[nextIdKey] ?: 1L, (existing.maxOfOrNull { it.id } ?: 0L) + 1L)
            val additions = incoming.filter { fingerprints.add(it.copy(id = 0)) }
                .map { it.copy(id = nextId++) }
            count = additions.size
            prefs[key] = serialize(existing + additions)
            prefs[nextIdKey] = nextId
        }
        return@withContext count
    }

    suspend fun readAll(): List<FoodLog> = withContext(Dispatchers.IO) {
        val prefs = store.data.first()
        return@withContext parse(prefs[key] ?: "[]")
    }

    private fun serialize(list: List<FoodLog>): String {
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
        return json.toString()
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
