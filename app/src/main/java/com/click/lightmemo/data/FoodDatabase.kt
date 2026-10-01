package com.click.lightmemo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FoodLogEntity::class, PersonalFoodMemoryEntity::class, RecommendationFeedbackEntity::class, LocalDiagnosticsEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class FoodDatabase : RoomDatabase() {
    abstract fun foodLogDao(): FoodLogDao
    abstract fun personalFoodMemoryDao(): PersonalFoodMemoryDao
    abstract fun recommendationFeedbackDao(): RecommendationFeedbackDao
    abstract fun localDiagnosticsDao(): LocalDiagnosticsDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("recognitionLlmCallCount", "reviewSaveCount", "modifiedReviewCount", "cacheReuseCount").forEach { column ->
                    db.execSQL("ALTER TABLE local_diagnostics ADD COLUMN $column INTEGER NOT NULL DEFAULT 0")
                }
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS personal_food_memories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, normalizedName TEXT NOT NULL, memoryJson TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_personal_food_memories_normalizedName ON personal_food_memories (normalizedName)")
                db.execSQL("CREATE TABLE IF NOT EXISTS recommendation_feedback (foodName TEXT NOT NULL PRIMARY KEY, likedCount INTEGER NOT NULL, dislikedCount INTEGER NOT NULL, acceptedCount INTEGER NOT NULL, skippedCount INTEGER NOT NULL, lastAcceptedAtMillis INTEGER NOT NULL, lastRejectedAtMillis INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS local_diagnostics (dateEpochDay INTEGER NOT NULL PRIMARY KEY, recognitionCount INTEGER NOT NULL, successCount INTEGER NOT NULL, failureCount INTEGER NOT NULL, totalDurationMs INTEGER NOT NULL, fullPathCount INTEGER NOT NULL, assistedPathCount INTEGER NOT NULL, fastPathCount INTEGER NOT NULL, llmCallCount INTEGER NOT NULL, usdaOfflineHits INTEGER NOT NULL, usdaOnlineHits INTEGER NOT NULL, chinaDbHits INTEGER NOT NULL, aiNutritionFallbackCount INTEGER NOT NULL, missingNutritionCount INTEGER NOT NULL, nameCorrectionCount INTEGER NOT NULL, weightCorrectionCount INTEGER NOT NULL, componentCorrectionCount INTEGER NOT NULL, foodReferenceCorrectionCount INTEGER NOT NULL, directAcceptanceCount INTEGER NOT NULL, recommendationOpenCount INTEGER NOT NULL, recommendationDrawCount INTEGER NOT NULL, recommendationLikeCount INTEGER NOT NULL, recommendationSkipCount INTEGER NOT NULL, recommendationDislikeCount INTEGER NOT NULL, recommendationAcceptCount INTEGER NOT NULL)")
            }
        }

        @Volatile
        private var instance: FoodDatabase? = null

        fun get(context: Context): FoodDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FoodDatabase::class.java,
                    "food_logs.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
        }

        fun createInMemory(context: Context): FoodDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                FoodDatabase::class.java,
            ).allowMainThreadQueries().build()
        }
    }
}
