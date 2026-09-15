package com.click.lightmemo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FoodLogEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FoodDatabase : RoomDatabase() {
    abstract fun foodLogDao(): FoodLogDao

    companion object {
        @Volatile
        private var instance: FoodDatabase? = null

        fun get(context: Context): FoodDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FoodDatabase::class.java,
                    "food_logs.db",
                ).build().also { instance = it }
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
