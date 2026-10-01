package com.click.lightmemo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalFoodMemoryDao {
    @Query("SELECT * FROM personal_food_memories ORDER BY id DESC")
    fun observeAll(): Flow<List<PersonalFoodMemoryEntity>>

    @Query("SELECT * FROM personal_food_memories ORDER BY id DESC")
    suspend fun getAll(): List<PersonalFoodMemoryEntity>

    @Query("SELECT * FROM personal_food_memories WHERE normalizedName = :name LIMIT 1")
    suspend fun find(name: String): PersonalFoodMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: PersonalFoodMemoryEntity)

    @Query("DELETE FROM personal_food_memories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM personal_food_memories")
    suspend fun clear()
}
