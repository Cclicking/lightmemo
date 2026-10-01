package com.click.lightmemo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecommendationFeedbackDao {
    @Query("SELECT * FROM recommendation_feedback ORDER BY foodName")
    fun observeAll(): Flow<List<RecommendationFeedbackEntity>>

    @Query("SELECT * FROM recommendation_feedback WHERE foodName = :name")
    suspend fun find(name: String): RecommendationFeedbackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(feedback: RecommendationFeedbackEntity)

    @Query("DELETE FROM recommendation_feedback")
    suspend fun clear()
}
