package com.click.lightmemo.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetDataChangesTest {
    @Test fun widgetSignalsStillFollowFoodEditsAndRecommendationFeedback(): Unit = runBlocking {
        val db = FoodDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        val changes = Channel<Unit>(Channel.UNLIMITED)
        val observer = launch(Dispatchers.IO) { db.widgetDataChanges().collect { changes.send(it) } }
        suspend fun nextChange() { withTimeout(5_000) { changes.receive() } }
        try {
            nextChange() // Initial refresh remains available even without a history query.
            val repo = FoodLogRepository(db.foodLogDao(), null)
            val food = FoodLog(name = "米饭", mealType = MealType.LUNCH, grams = 100.0,
                nutrition = Nutrition(116.0, 2.6, 25.9, 0.3), createdAtMillis = 1000)
            val id = repo.insert(food)
            nextChange()
            repo.update(food.copy(id = id, grams = 200.0))
            nextChange()
            repo.deleteById(id)
            nextChange()
            RecommendationRepository(db).record("米饭", RecommendationFeedback.LIKED)
            nextChange()
        } finally { observer.cancelAndJoin(); db.close(); changes.close() }
    }
}
