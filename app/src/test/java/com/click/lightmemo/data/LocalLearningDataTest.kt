package com.click.lightmemo.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalLearningDataTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun stabilityTracksRecentCompositionAndWeightAndResetsOnChanges() = withDatabase { db ->
        val repo = PersonalFoodMemoryRepository(db)
        val reference = com.click.lightmemo.domain.NutritionReference("china:1", "米饭", "china", Nutrition(116.0))
        val component = com.click.lightmemo.domain.FoodComponent("c", "米饭", "rice", source = com.click.lightmemo.domain.ComponentSource.VISIBLE,
            estimatedWeightG = 200.0, weightMinG = 200.0, weightMaxG = 200.0, confidence = 0.95, nutritionReference = reference)
        val food = log(name = "米饭").copy(components = listOf(component))
        repeat(5) { repo.recordConfirmed(food, false, preferredReference = reference) }
        assertEquals(5, repo.findExact("米饭")!!.recentStableCount)
        repo.recordConfirmed(food.copy(grams = 300.0, components = listOf(component.copy(estimatedWeightG = 300.0, weightMinG = 300.0, weightMaxG = 300.0))), true)
        assertEquals(1, repo.findExact("米饭")!!.recentStableCount)
        repo.recordConfirmed(food.copy(grams = 300.0, components = listOf(component.copy(name = "糙米饭", estimatedWeightG = 300.0, weightMinG = 300.0, weightMaxG = 300.0))), true)
        assertEquals(1, repo.findExact("米饭")!!.recentStableCount)
    }

    @Test fun confirmedReferencesAreNotQueriedOrOverwrittenDuringEnrichment() = runBlocking {
        val reference = com.click.lightmemo.domain.NutritionReference("user-fixed", "已确认条目", "china", Nutrition(116.0))
        val component = com.click.lightmemo.domain.FoodComponent("c", "米饭", "rice", source = com.click.lightmemo.domain.ComponentSource.VISIBLE,
            estimatedWeightG = 200.0, weightMinG = 200.0, weightMaxG = 200.0, confidence = 0.95, nutritionReference = reference)
        val dish = com.click.lightmemo.domain.RecognizedDish("d", "米饭", com.click.lightmemo.domain.DishType.SINGLE_FOOD, 0.95, listOf(component))
        val meal = com.click.lightmemo.domain.MealRecognition(true, "午餐", listOf(dish), 0.95, emptyList())
        val database = com.click.lightmemo.network.FoodDataCentralClient(context)
        assertEquals(reference, database.enrich(meal, "").dishes.single().components.single().nutritionReference)
    }
    private fun log(grams: Double = 200.0, name: String = "番茄炒蛋") =
        FoodLog(name = name, mealType = MealType.LUNCH, grams = grams, nutrition = Nutrition(200.0, 10.0, 12.0, 10.0))

    private fun withDatabase(block: suspend (FoodDatabase) -> Unit) = runBlocking {
        val db = FoodDatabase.createInMemory(context)
        try { block(db) } finally { db.close() }
    }

    @Test fun migrationFromV2PreservesExistingDiagnosticsAndDefaultsNewCounters() = runBlocking {
        val name = "migration-v2-diagnostics.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File(System.getProperty("food.schemas"), "com.click.lightmemo.data.FoodDatabase/2.json").readText())
            .getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { index ->
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                repeat(indices.length()) { sqlite.execSQL(indices.getJSONObject(it).getString("createSql").replace("\${TABLE_NAME}", table)) }
                if (table == "local_diagnostics") {
                    val fields = entity.getJSONArray("fields")
                    val columns = (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
                    val values = columns.map { when (it) { "dateEpochDay" -> 20000; "recognitionCount" -> 7; "llmCallCount" -> 9; else -> 0 } }
                    sqlite.execSQL("INSERT INTO local_diagnostics (${columns.joinToString(",")}) VALUES (${values.joinToString(",")})")
                }
            }
            sqlite.version = 2
        }
        val upgraded = Room.databaseBuilder(context, FoodDatabase::class.java, name)
            .addMigrations(FoodDatabase.MIGRATION_2_3).build()
        try {
            val previous = upgraded.localDiagnosticsDao().find(20000)!!
            assertEquals(7L, previous.recognitionCount)
            assertEquals(9L, previous.llmCallCount)
            assertEquals(0L, previous.recognitionLlmCallCount)
            assertEquals(0L, previous.reviewSaveCount)
            DiagnosticsRepository(upgraded).increment(LocalDiagnosticsEntity(20000, reviewSaveCount = 1, modifiedReviewCount = 1))
            assertEquals(1L, upgraded.localDiagnosticsDao().find(20000)!!.modifiedReviewCount)
            assertEquals(3, upgraded.openHelper.readableDatabase.version)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }

    @Test fun optionalDiagnosticsFailureDoesNotUndoCommittedFoodOrTouchOtherTables() = withDatabase { db ->
        val logs = FoodLogRepository(db.foodLogDao(), null)
        val id = logs.insert(log())
        PersonalFoodMemoryRepository(db).recordConfirmed(log(), true)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_diagnostics BEFORE INSERT ON local_diagnostics BEGIN SELECT RAISE(FAIL, 'unavailable'); END")
        DiagnosticsRepository(db).recordSafely(LocalDiagnosticsEntity(1, recognitionCount = 1))
        assertNotNull(db.foodLogDao().getById(id))
        assertEquals(1, db.personalFoodMemoryDao().getAll().size)
        assertNull(db.localDiagnosticsDao().find(1))
    }

    @Test fun diagnosticsClearPreservesFoodMemoryAndRecommendationFeedback() = withDatabase { db ->
        val logs = FoodLogRepository(db.foodLogDao(), null)
        val id = logs.insert(log())
        PersonalFoodMemoryRepository(db).recordConfirmed(log(), true)
        RecommendationRepository(db).record("米饭", RecommendationFeedback.LIKED)
        val diagnostics = DiagnosticsRepository(db)
        diagnostics.increment(LocalDiagnosticsEntity(LocalDate.now().toEpochDay(), recognitionCount = 1, reviewSaveCount = 1))
        diagnostics.clear()
        assertTrue(diagnostics.recentDays().first().isEmpty())
        assertNotNull(db.foodLogDao().getById(id))
        assertEquals(1, db.personalFoodMemoryDao().getAll().size)
        assertEquals(1, db.recommendationFeedbackDao().observeAll().first().size)
    }

    @Test fun likeCanBeCancelledAndRestoredWithoutChangingOtherFeedback() = withDatabase { db ->
        val repo = RecommendationRepository(db)
        repo.record("米饭", RecommendationFeedback.ACCEPTED, 123)
        repo.record("米饭", RecommendationFeedback.SKIPPED, 456)
        assertTrue(repo.toggleLike(" 米 饭 "))
        assertEquals(1L, repo.feedback.first().single().likedCount)
        assertFalse(repo.toggleLike("米饭"))
        val cancelled = repo.feedback.first().single()
        assertEquals(0L, cancelled.likedCount)
        assertEquals(1L, cancelled.acceptedCount)
        assertEquals(1L, cancelled.skippedCount)
        assertEquals(123L, cancelled.lastAcceptedAtMillis)
        assertEquals(456L, cancelled.lastRejectedAtMillis)
        assertTrue(repo.toggleLike("米饭"))
    }

    @Test fun cancellingLegacyRepeatedLikesClearsTheActivePreference() = withDatabase { db ->
        val repo = RecommendationRepository(db)
        repeat(5) { repo.record("米饭", RecommendationFeedback.LIKED) }
        assertFalse(repo.toggleLike("米饭"))
        assertEquals(0L, repo.feedback.first().single().likedCount)
    }

    @Test fun concurrentLikeTogglesAreAtomic() = withDatabase { db ->
        val repo = RecommendationRepository(db)
        coroutineScope { (1..20).map { async(Dispatchers.Default) { repo.toggleLike("米饭") } }.awaitAll() }
        assertEquals(0L, repo.feedback.first().single().likedCount)
    }

    @Test fun correctionsHaveMoreWeightThanObservationsAndPreserveAliases() = withDatabase { db ->
        val repo = PersonalFoodMemoryRepository(db)
        repo.recordConfirmed(log(), corrected = false)
        repo.recordConfirmed(log(100.0), corrected = true, originalName = "西红柿炒蛋", nowMillis = 123)
        val corrected = repo.findExact(" 番茄 炒蛋 ")!!
        assertEquals(160.0, corrected.typicalGrams, 0.001)
        assertEquals(setOf("西红柿炒蛋"), corrected.aliases)
        assertEquals(1, corrected.correctionCount)
        assertEquals(123L, corrected.lastCorrectedAtMillis)
        repo.clear()
        repo.recordConfirmed(log(), corrected = false)
        repo.recordConfirmed(log(100.0), corrected = false)
        assertEquals(190.0, repo.findExact("番茄炒蛋")!!.typicalGrams, 0.001)
    }

    @Test fun repeatedConfirmationsAreStableAndNamesStayDistinct() = withDatabase { db ->
        val repo = PersonalFoodMemoryRepository(db)
        repeat(20) { repo.recordConfirmed(log(), corrected = false) }
        assertEquals(200.0, repo.findExact("番茄炒蛋")!!.typicalGrams, 0.001)
        assertEquals(20, repo.findExact("番茄炒蛋")!!.useCount)
        assertNull(repo.findExact("番茄炒肉"))
        assertNull(repo.findExact("水煮番茄炒蛋"))
        repo.delete(repo.memories.first().single().first)
        assertNull(repo.findExact("番茄炒蛋"))
    }

    @Test fun preferredReferenceAndCorrectedCompositionSurviveObservation() = withDatabase { db ->
        val repo = PersonalFoodMemoryRepository(db)
        val reference = com.click.lightmemo.domain.NutritionReference("china:1", "鸡蛋", "china", Nutrition(140.0))
        repo.recordConfirmed(log(), true, preferredReference = reference)
        repo.recordConfirmed(log().copy(nutrition = Nutrition(999.0)), false)
        val memory = repo.findExact("番茄炒蛋")!!
        assertEquals(reference, memory.preferredFoodReference)
        assertEquals(200.0, memory.nutrition.caloriesKcal, 0.001)
    }

    @Test fun concurrentMemoryUpdatesDoNotLoseConfirmations() = withDatabase { db ->
        val repo = PersonalFoodMemoryRepository(db)
        coroutineScope { (1..25).map { async(Dispatchers.Default) { repo.recordConfirmed(log(), false) } }.awaitAll() }
        assertEquals(25, repo.findExact("番茄炒蛋")!!.useCount)
        assertEquals(1, repo.memories.first().size)
    }

    @Test fun feedbackIsNormalizedAndConcurrentCountsAreAtomic() = withDatabase { db ->
        val repo = RecommendationRepository(db)
        coroutineScope { (1..30).map { async(Dispatchers.Default) { repo.record(" 番茄 炒蛋 ", RecommendationFeedback.LIKED) } }.awaitAll() }
        repo.record("番茄炒蛋", RecommendationFeedback.DISLIKED, 111)
        repo.record("番茄炒蛋", RecommendationFeedback.SKIPPED, 222)
        repo.record("番茄炒蛋", RecommendationFeedback.ACCEPTED, 333)
        val feedback = repo.feedback.first().single()
        assertEquals(30L, feedback.likedCount)
        assertEquals(1L, feedback.dislikedCount)
        assertEquals(1L, feedback.skippedCount)
        assertEquals(1L, feedback.acceptedCount)
        assertEquals(222L, feedback.lastRejectedAtMillis)
        assertEquals(333L, feedback.lastAcceptedAtMillis)
        repo.clear()
        assertTrue(repo.feedback.first().isEmpty())
    }

    @Test fun diagnosticsAccumulateConcurrentlyAndExportOnlyNumericAllowlist() = withDatabase { db ->
        val repo = DiagnosticsRepository(db)
        val today = LocalDate.of(2026, 10, 1)
        coroutineScope { (1..40).map { async(Dispatchers.Default) {
            repo.increment(LocalDiagnosticsEntity(today.toEpochDay(), recognitionCount = 1, successCount = 1, totalDurationMs = 100, llmCallCount = 2))
        } }.awaitAll() }
        val row = repo.recentDays(today = today).first().single()
        assertEquals(40L, row.recognitionCount)
        assertEquals(4000L, row.totalDurationMs)
        assertEquals(80L, row.llmCallCount)
        PersonalFoodMemoryRepository(db).recordConfirmed(log(name = "private-food"), true)
        val exported = org.json.JSONArray(repo.exportJson(today = today))
        val item = exported.getJSONObject(0)
        assertTrue(item.keys().asSequence().all { item.get(it) is Number })
        assertFalse(exported.toString().contains("private-food"))
        assertFalse(exported.toString().contains("apiKey", ignoreCase = true))
        repo.clear()
        assertEquals("[]", repo.exportJson(today = today).trim())
    }

    @Test fun diagnosticWindowIncludesExactlyThirtyCalendarDays() = withDatabase { db ->
        val repo = DiagnosticsRepository(db)
        val today = LocalDate.of(2026, 10, 1)
        listOf(-30L, -29L, 0L, 1L).forEach {
            repo.increment(LocalDiagnosticsEntity(today.plusDays(it).toEpochDay(), recognitionCount = 1))
        }
        assertEquals(listOf(today.minusDays(29).toEpochDay(), today.toEpochDay()), repo.recentDays(today = today).first().map { it.dateEpochDay })
    }

    @Test fun invalidDeltasAndMemoryLeaveTablesUntouched() = withDatabase { db ->
        try {
            DiagnosticsRepository(db).increment(LocalDiagnosticsEntity(1, recognitionCount = -1))
            fail("must reject negative metrics")
        } catch (_: IllegalArgumentException) { }
        try {
            PersonalFoodMemoryRepository(db).recordConfirmed(log(Double.NaN), true)
            fail("must reject invalid weight")
        } catch (_: IllegalArgumentException) { }
        assertNull(db.localDiagnosticsDao().find(1))
        assertTrue(db.personalFoodMemoryDao().observeAll().first().isEmpty())
    }

    @Test fun migrationFromExportedV1SchemaPreservesLogsAndValidatesNewTables() = runBlocking {
        val name = "migration-v15-test.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File(System.getProperty("food.schemas"), "com.click.lightmemo.data.FoodDatabase/1.json").readText())
            .getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { index ->
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                repeat(indices.length()) { sqlite.execSQL(indices.getJSONObject(it).getString("createSql").replace("\${TABLE_NAME}", table)) }
            }
            sqlite.execSQL("""INSERT INTO food_logs VALUES (7, '旧米饭', 'LUNCH', 100, 116, 2.6, 25.9, 0.3, '[]', NULL, 20000, 1000, NULL, NULL, '[]')""")
            sqlite.version = 1
        }
        val upgraded = Room.databaseBuilder(context, FoodDatabase::class.java, name)
            .addMigrations(FoodDatabase.MIGRATION_1_2, FoodDatabase.MIGRATION_2_3).build()
        try {
            assertEquals("旧米饭", upgraded.foodLogDao().getById(7)!!.name)
            val repo = FoodLogRepository(upgraded.foodLogDao(), null)
            assertEquals(8L, repo.insert(log()))
            PersonalFoodMemoryRepository(upgraded).recordConfirmed(log(), true)
            RecommendationRepository(upgraded).record("米饭", RecommendationFeedback.LIKED)
            DiagnosticsRepository(upgraded).increment(LocalDiagnosticsEntity(20000, successCount = 1))
            assertEquals(3, upgraded.openHelper.readableDatabase.version)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun savedCorrectionIsAvailableOnNextRecognitionAndDeletionRestoresMiss() = withDatabase { db ->
        val repo = PersonalFoodMemoryRepository(db)
        val initial = log(320.0)
        assertTrue(repo.learnAfterSave(log(230.0), initial))
        val next = repo.match("番茄炒蛋")!!
        assertEquals(230.0, next.memory.typicalGrams, 0.001)
        assertTrue(next.canApply)
        repo.clear()
        assertNull(repo.match("番茄炒蛋"))
    }

    @Test fun renameUpdatesExistingCanonicalMemoryAndAliasContinuesSameHistory() = withDatabase { db ->
        val repo = PersonalFoodMemoryRepository(db)
        repo.recordConfirmed(log(name = "西红柿炒蛋"), false)
        assertTrue(repo.learnAfterSave(log(name = "番茄炒蛋"), log(name = "西红柿炒蛋")))
        assertEquals(1, repo.memories.first().size)
        assertEquals("番茄炒蛋", repo.match("西红柿炒蛋")!!.memory.canonicalName)
        repo.learnAfterSave(log(name = "西红柿炒蛋"), log(name = "西红柿炒蛋"))
        assertEquals(1, repo.memories.first().size)
        assertEquals(3, repo.match("番茄炒蛋")!!.memory.useCount)
    }

    @Test fun onlyUserChangedReferencesBecomePreferred() = withDatabase { db ->
        val reference = com.click.lightmemo.domain.NutritionReference("old", "鸡蛋", "china", Nutrition(140.0))
        val component = com.click.lightmemo.domain.FoodComponent("id", "鸡蛋", "egg", source = com.click.lightmemo.domain.ComponentSource.VISIBLE,
            estimatedWeightG = 200.0, weightMinG = 200.0, weightMaxG = 200.0, confidence = 0.9, nutritionReference = reference)
        val initial = log().copy(components = listOf(component))
        val repo = PersonalFoodMemoryRepository(db)
        repo.learnAfterSave(initial, initial)
        assertTrue(repo.match("番茄炒蛋")!!.memory.preferredComponentReferences.isEmpty())
        val preferred = reference.copy(sourceId = "user-choice")
        repo.learnAfterSave(initial.copy(components = listOf(component.copy(nutritionReference = preferred))), initial)
        assertEquals(preferred, repo.match("番茄炒蛋")!!.memory.preferredComponentReferences["鸡蛋"])
    }

    @Test fun optionalLearningFailureDoesNotInvalidateCommittedLog() = withDatabase { db ->
        val logs = FoodLogRepository(db.foodLogDao(), null)
        val saved = log()
        val id = logs.insert(saved)
        db.personalFoodMemoryDao().upsert(PersonalFoodMemoryEntity(normalizedName = "番茄炒蛋", memoryJson = "invalid-json"))
        assertFalse(PersonalFoodMemoryRepository(db).learnAfterSave(saved, saved))
        assertEquals(saved.copy(id = id), logs.getById(id))
        assertNull(PersonalFoodMemoryRepository(db).match("番茄炒蛋"))
    }

    @Test fun acceptedRecommendationSavesMealAndFeedbackTogetherFromUserPerspective() = withDatabase { db ->
        val logs = FoodLogRepository(db.foodLogDao(), null)
        val recommendations = RecommendationRepository(db)
        val saved = recommendations.saveAccepted(log(), logs)
        assertTrue(saved.feedbackSaved)
        assertEquals(log().copy(id = saved.logId, createdAtMillis = logs.getById(saved.logId)!!.createdAtMillis), logs.getById(saved.logId))
        assertEquals(1L, recommendations.feedback.first().single().acceptedCount)
    }

    @Test fun failedMealSaveDoesNotRecordAcceptance() = withDatabase { db ->
        val recommendations = RecommendationRepository(db)
        try {
            recommendations.saveAccepted(log(0.0), FoodLogRepository(db.foodLogDao(), null))
            fail("Invalid meal must fail")
        } catch (_: IllegalArgumentException) { }
        assertTrue(recommendations.feedback.first().isEmpty())
        assertTrue(db.foodLogDao().getAll().isEmpty())
    }

    @Test fun feedbackFailurePreservesCommittedMealAndReportsSuccessfulSave() = withDatabase { db ->
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_feedback BEFORE INSERT ON recommendation_feedback BEGIN SELECT RAISE(FAIL, 'feedback unavailable'); END")
        val logs = FoodLogRepository(db.foodLogDao(), null)
        val saved = RecommendationRepository(db).saveAccepted(log(), logs)
        assertFalse(saved.feedbackSaved)
        assertNotNull(logs.getById(saved.logId))
        assertEquals(1, logs.readAll().size)
    }
}
