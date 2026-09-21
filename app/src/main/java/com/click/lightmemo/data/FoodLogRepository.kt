package com.click.lightmemo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.click.lightmemo.domain.FoodLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Context.foodStore by preferencesDataStore(name = "food_logs")
private val legacyLogsKey = stringPreferencesKey("logs_json")
private val legacyNextIdKey = longPreferencesKey("next_id")

class FoodLogRepository(
    private val dao: FoodLogDao,
    private val legacyStore: DataStore<Preferences>?,
) {
    constructor(context: Context) : this(
        dao = FoodDatabase.get(context).foodLogDao(),
        legacyStore = context.foodStore,
    )

    private val migrationMutex = Mutex()
    private val migrated = CompletableDeferred<Unit>()

    val readError = MutableStateFlow<String?>(null)

    fun logsInRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<FoodLog>> =
        dao.observeRange(fromEpochDay, toEpochDay)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
            .retryWhen { cause, _ ->
                readError.value = "记录读取失败，原数据已保留，正在重试：${cause.message.orEmpty()}"
                delay(5_000)
                true
            }

    suspend fun ensureMigrated() {
        if (migrated.isCompleted) return migrated.await()
        migrationMutex.withLock {
            if (migrated.isCompleted) return migrated.await()
            try {
                migrateLegacyIfNeeded()
                migrated.complete(Unit)
            } catch (e: Exception) {
                migrated.completeExceptionally(e)
                throw e
            }
        }
    }

    suspend fun getById(id: Long): FoodLog? = withContext(Dispatchers.IO) {
        ensureMigrated()
        dao.getById(id)?.toDomain()
    }

    suspend fun insert(log: FoodLog): Long = insertAll(listOf(log)).single()

    suspend fun insertAll(logs: List<FoodLog>): List<Long> = withContext(Dispatchers.IO) {
        ensureMigrated()
        logs.forEach { it.validate() }
        // id=0 lets SQLite AUTOINCREMENT assign unique, non-reused ids.
        val entities = logs.map { it.copy(id = 0L).toEntity() }
        return@withContext dao.insertAll(entities)
    }

    suspend fun deleteById(id: Long): FoodLog? = withContext(Dispatchers.IO) {
        ensureMigrated()
        val existing = dao.getById(id)?.toDomain() ?: return@withContext null
        dao.deleteById(id)
        return@withContext existing
    }

    suspend fun restore(log: FoodLog) = withContext(Dispatchers.IO) {
        ensureMigrated()
        log.validate()
        require(dao.getById(log.id) == null) { "该记录已存在" }
        dao.insertAll(listOf(log.toEntity()))
    }

    suspend fun update(log: FoodLog) = withContext(Dispatchers.IO) {
        ensureMigrated()
        log.validate()
        val updated = dao.update(log.toEntity())
        require(updated > 0) { "该记录已删除，请刷新后重试" }
    }

    suspend fun updateAll(logs: Collection<FoodLog>) = withContext(Dispatchers.IO) {
        ensureMigrated()
        logs.forEach { log ->
            log.validate()
            val updated = dao.update(log.toEntity())
            require(updated > 0) { "该记录已删除，请刷新后重试" }
        }
    }

    suspend fun exportJson(): String {
        ensureMigrated()
        return JSONObject().apply {
            put("format", "food-calorie-logs")
            put("version", 1)
            put("logs", JSONArray(serializeFoodLogs(readAll().map { it.copy(imageUri = null) })))
        }.toString(2)
    }

    suspend fun importJson(raw: String): Int = withContext(Dispatchers.IO) {
        ensureMigrated()
        val root = JSONObject(raw)
        require(root.getString("format") == "food-calorie-logs" && root.getInt("version") == 1) {
            "不支持的备份格式或版本"
        }
        val incoming = parseFoodLogs(root.getJSONArray("logs").toString()).map { it.copy(imageUri = null) }
        incoming.forEach { it.validate() }
        val existing = dao.getAll().map { it.toDomain() }
        val fingerprints = existing.map { it.copy(id = 0, imageUri = null) }.toMutableSet()
        val additions = incoming.filter { fingerprints.add(it.copy(id = 0, imageUri = null)) }
            .map { it.copy(id = 0L).toEntity() }
        if (additions.isNotEmpty()) dao.insertAll(additions)
        return@withContext additions.size
    }

    suspend fun readAll(): List<FoodLog> = withContext(Dispatchers.IO) {
        ensureMigrated()
        dao.getAll().map { it.toDomain() }
    }

    suspend fun readRange(fromEpochDay: Long, toEpochDay: Long): List<FoodLog> = withContext(Dispatchers.IO) {
        ensureMigrated()
        dao.observeRange(fromEpochDay, toEpochDay).first().map { it.toDomain() }
    }

    private suspend fun migrateLegacyIfNeeded() {
        val store = legacyStore ?: return
        if (dao.count() > 0) {
            clearLegacy(store)
            return
        }
        val prefs = store.data.first()
        val raw = prefs[legacyLogsKey]
        if (raw.isNullOrBlank() || raw == "[]") {
            clearLegacy(store)
            return
        }
        val legacy = parseFoodLogs(raw)
        if (legacy.isNotEmpty()) {
            dao.insertAll(legacy.map { it.toEntity() })
        }
        clearLegacy(store)
    }

    private suspend fun clearLegacy(store: DataStore<Preferences>) {
        store.edit { prefs ->
            prefs.remove(legacyLogsKey)
            prefs.remove(legacyNextIdKey)
        }
    }
}
