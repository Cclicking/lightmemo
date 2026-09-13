package com.foodcalorie.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.core.okio.OkioStorage
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import com.foodcalorie.app.domain.FoodLog
import com.foodcalorie.app.domain.MealType
import com.foodcalorie.app.domain.Nutrition
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class FoodLogRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private fun log(name: String = "米饭") = FoodLog(name = name, mealType = MealType.LUNCH,
        grams = 100.0, nutrition = Nutrition(116.0, 2.6, 25.9, 0.3), createdAtMillis = 1000)

    private fun withRepository(block: suspend (FoodLogRepository) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = folder.newFolder().resolve("logs.preferences_pb")
        // Android File.renameTo does not replace an existing file on Windows.
        // Use the real DataStore with Okio's atomic move for host-side persistence tests.
        val store = PreferenceDataStoreFactory.create(scope = scope,
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.toOkioPath() })
        try { block(FoodLogRepository(store)) } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun concurrentInsertsKeepEveryRecordAndUniqueIds() = withRepository { repo ->
        coroutineScope { (1..50).map { async(Dispatchers.Default) { repo.insert(log("食物$it")) } }.awaitAll() }
        val saved = repo.readAll()
        assertEquals(50, saved.size)
        assertEquals(50, saved.map { it.id }.toSet().size)
    }

    @Test fun batchValidationFailureWritesNothing() = withRepository { repo ->
        repo.insert(log())
        try {
            repo.insertAll(listOf(log("正常"), log("错误").copy(grams = Double.NaN)))
            fail("must reject invalid batch")
        } catch (_: IllegalArgumentException) { }
        assertEquals(1, repo.readAll().size)
    }

    @Test fun deletedIdIsNotReusedAndUndoPreservesNewRecords() = withRepository { repo ->
        val id = repo.insert(log())
        val deleted = repo.deleteById(id)!!
        val next = repo.insert(log("鸡蛋"))
        assertNotEquals(id, next)
        repo.restore(deleted)
        assertEquals(setOf(id, next), repo.readAll().map { it.id }.toSet())
    }

    @Test fun editsPersistWithoutChangingIdentity() = withRepository { repo ->
        repo.insert(log())
        val saved = repo.readAll().single()
        repo.update(saved.copy(name = "晚餐米饭", mealType = MealType.DINNER, note = "补记"))
        assertEquals(saved.id, repo.readAll().single().id)
        assertEquals("补记", repo.readAll().single().note)
    }

    @Test fun exportImportIsIdempotentAndOmitsLocalImageUris() = withRepository { repo ->
        repo.insert(log().copy(imageUri = "content://local/image"))
        val exported = repo.exportJson()
        assertFalse(exported.contains("content://"))
        assertEquals(0, repo.importJson(exported))
        repo.deleteById(repo.readAll().single().id)
        assertEquals(1, repo.importJson(exported))
        assertEquals(0, repo.importJson(exported))
        assertNull(repo.readAll().single().imageUri)
    }

    @Test fun invalidImportPreservesExistingData() = withRepository { repo ->
        repo.insert(log())
        val before = repo.readAll()
        try { repo.importJson(repo.exportJson().replace("116", "-116")); fail("invalid import") }
        catch (_: IllegalArgumentException) { }
        assertEquals(before, repo.readAll())
    }
}
