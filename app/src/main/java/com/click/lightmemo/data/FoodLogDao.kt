package com.click.lightmemo.data

import androidx.room.Dao
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodLogDao {
    @Query("SELECT * FROM food_logs WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay ORDER BY id ASC")
    fun observeRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<FoodLogEntity>>

    @Query("SELECT * FROM food_logs ORDER BY id ASC")
    fun observeAll(): Flow<List<FoodLogEntity>>

    @Query("SELECT * FROM food_logs ORDER BY id ASC")
    suspend fun getAll(): List<FoodLogEntity>

    @Query("SELECT * FROM food_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FoodLogEntity?

    @Query("DELETE FROM food_logs WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT COUNT(*) FROM food_logs")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<FoodLogEntity>): List<Long>

    @Update
    suspend fun update(entity: FoodLogEntity): Int
}
