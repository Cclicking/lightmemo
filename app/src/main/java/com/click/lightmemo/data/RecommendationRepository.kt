package com.click.lightmemo.data

import androidx.room.withTransaction
import com.click.lightmemo.domain.normalizeFoodName
import com.click.lightmemo.domain.FoodLog
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class RecommendationFeedback { LIKED, DISLIKED, ACCEPTED, SKIPPED }

class RecommendationRepository(private val database: FoodDatabase) {
    private val dao = database.recommendationFeedbackDao()
    val feedback = dao.observeAll()

    suspend fun record(name: String, event: RecommendationFeedback, nowMillis: Long = System.currentTimeMillis()) {
        val key = normalizeFoodName(name)
        require(key.isNotEmpty())
        database.withTransaction {
            val old = dao.find(key) ?: RecommendationFeedbackEntity(key)
            dao.upsert(when (event) {
                RecommendationFeedback.LIKED -> old.copy(likedCount = old.likedCount + 1)
                RecommendationFeedback.DISLIKED -> old.copy(dislikedCount = old.dislikedCount + 1, lastRejectedAtMillis = nowMillis)
                RecommendationFeedback.ACCEPTED -> old.copy(acceptedCount = old.acceptedCount + 1, lastAcceptedAtMillis = nowMillis)
                RecommendationFeedback.SKIPPED -> old.copy(skippedCount = old.skippedCount + 1, lastRejectedAtMillis = nowMillis)
            })
        }
    }

    /** Toggle current preference atomically; unliking clears legacy repeated likes too. */
    suspend fun toggleLike(name: String): Boolean {
        val key = normalizeFoodName(name)
        require(key.isNotEmpty())
        return database.withTransaction {
            val old = dao.find(key) ?: RecommendationFeedbackEntity(key)
            val liked = old.likedCount == 0L
            dao.upsert(old.copy(likedCount = if (liked) 1 else 0))
            liked
        }
    }

    suspend fun clear() = dao.clear()

    /** A feedback failure must never turn a committed meal into a retryable save failure. */
    suspend fun saveAccepted(log: FoodLog, foodLogs: FoodLogRepository): AcceptedRecommendation = withContext(NonCancellable) {
        val id = foodLogs.insert(log)
        val feedbackSaved = runCatching { record(log.name, RecommendationFeedback.ACCEPTED) }.isSuccess
        AcceptedRecommendation(id, feedbackSaved)
    }
}

data class AcceptedRecommendation(val logId: Long, val feedbackSaved: Boolean)
