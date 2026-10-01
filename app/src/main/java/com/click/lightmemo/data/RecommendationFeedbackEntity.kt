package com.click.lightmemo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recommendation_feedback")
data class RecommendationFeedbackEntity(
    @PrimaryKey val foodName: String,
    val likedCount: Long = 0,
    val dislikedCount: Long = 0,
    val acceptedCount: Long = 0,
    val skippedCount: Long = 0,
    val lastAcceptedAtMillis: Long = 0,
    val lastRejectedAtMillis: Long = 0,
)
