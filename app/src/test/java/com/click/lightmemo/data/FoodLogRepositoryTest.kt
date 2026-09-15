package com.click.lightmemo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoodLogRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private fun log(name: String = "米饭") = FoodLog(
        name = name,
        mealType = MealType.LUNCH,
        grams = 100.0,
        nutrition = Nutrition(116.0, 2.6, 25.9, 0.3),
        createdAtMillis = 1000,
    )

    private fun withRepository(
        legacyStore: DataStore<Preferences>? = null,
        block: suspend (FoodLogRepository) -> Unit,
    ) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = FoodDatabase.createInMemory(context)
        val repo = FoodLogRepository(db.foodLogDao(), legacyStore)
        try {
            repo.ensureMigrated()
            block(repo)
        } finally {
            db.close()
        }
    }

    @Test
    fun concurrentInsertsKeepEveryRecordAndUniqueIds() = withRepository { repo ->
        coroutineScope {
            (1..50).map {
                async(Dispatchers.Default) { repo.insert(log("食物$it")) }
            }.awaitAll()
        }
        val saved = repo.readAll()
        assertEquals(50, saved.size)
        assertEquals(50, saved.map { it.id }.toSet().size)
    }

    @Test
    fun batchValidationFailureWritesNothing() = withRepository { repo ->
        repo.insert(log())
        try {
            repo.insertAll(listOf(log("正常"), log("错误").copy(grams = Double.NaN)))
            fail("must reject invalid batch")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(1, repo.readAll().size)
    }

    @Test
    fun deletedIdIsNotReusedAndUndoPreservesNewRecords() = withRepository { repo ->
        val id = repo.insert(log())
        val deleted = repo.deleteById(id)!!
        val next = repo.insert(log("鸡蛋"))
        assertNotEquals(id, next)
        repo.restore(deleted)
        assertEquals(setOf(id, next), repo.readAll().map { it.id }.toSet())
    }

    @Test
    fun editsPersistWithoutChangingIdentity() = withRepository { repo ->
        repo.insert(log())
        val saved = repo.readAll().single()
        repo.update(saved.copy(name = "晚餐米饭", mealType = MealType.DINNER, note = "补记"))
        assertEquals(saved.id, repo.readAll().single().id)
        assertEquals("补记", repo.readAll().single().note)
    }

    @Test
    fun exportImportIsIdempotentAndOmitsLocalImageUris() = withRepository { repo ->
        repo.insert(log().copy(imageUri = "content://local/image"))
        val exported = repo.exportJson()
        assertFalse(exported.contains("content://"))
        assertEquals(0, repo.importJson(exported))
        repo.deleteById(repo.readAll().single().id)
        assertEquals(1, repo.importJson(exported))
        assertEquals(0, repo.importJson(exported))
        assertNull(repo.readAll().single().imageUri)
    }

    @Test
    fun invalidImportPreservesExistingData() = withRepository { repo ->
        repo.insert(log())
        val before = repo.readAll()
        try {
            repo.importJson(repo.exportJson().replace("116", "-116"))
            fail("invalid import")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(before, repo.readAll())
    }

    @Test
    fun rangeQueryOnlyReturnsRequestedDays() = withRepository { repo ->
        val base = log().dateEpochDay
        repo.insertAll(
            listOf(
                log("早").copy(dateEpochDay = base - 1),
                log("中").copy(dateEpochDay = base),
                log("晚").copy(dateEpochDay = base + 1),
            ),
        )
        val ranged = repo.readRange(base, base)
        assertEquals(listOf("中"), ranged.map { it.name })
    }

    @Test
    fun migratesLegacyDataStorePayloadOnceAndPreservesIds() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = folder.newFolder().resolve("logs.preferences_pb")
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.toOkioPath() },
        )
        val logsKey = stringPreferencesKey("logs_json")
        val nextIdKey = longPreferencesKey("next_id")
        val legacy = listOf(
            log("旧米饭").copy(id = 7),
            log("旧鸡蛋").copy(id = 9),
        )
        store.edit { prefs ->
            prefs[logsKey] = serializeFoodLogs(legacy)
            prefs[nextIdKey] = 10L
        }
        try {
            withRepository(legacyStore = store) { repo ->
                val migrated = repo.readAll()
                assertEquals(listOf(7L, 9L), migrated.map { it.id })
                assertEquals(listOf("旧米饭", "旧鸡蛋"), migrated.map { it.name })
                val next = repo.insert(log("新记录"))
                assertEquals(10L, next)
                val prefs = store.data.first()
                assertNull(prefs[logsKey])
                assertNull(prefs[nextIdKey])
            }
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
}
