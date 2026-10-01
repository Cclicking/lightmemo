package com.click.lightmemo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalDiagnosticsDao {
    @Query("SELECT * FROM local_diagnostics WHERE dateEpochDay BETWEEN :from AND :to ORDER BY dateEpochDay")
    fun observeRange(from: Long, to: Long): Flow<List<LocalDiagnosticsEntity>>

    @Query("SELECT * FROM local_diagnostics WHERE dateEpochDay = :day")
    suspend fun find(day: Long): LocalDiagnosticsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metrics: LocalDiagnosticsEntity)

    @Query("DELETE FROM local_diagnostics")
    suspend fun clear()
}
