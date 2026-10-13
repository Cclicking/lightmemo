package com.click.lightmemo.mcp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.click.lightmemo.data.FoodDatabase
import com.click.lightmemo.data.FoodLogRepository
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalNutritionProviderTest {
    @Test fun readsOnlyTodayAndReflectsInsertUpdateAndDelete() = runBlocking {
        val db = FoodDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        try {
            val repo = FoodLogRepository(db.foodLogDao(), null)
            val provider = LocalNutritionProvider(repo)
            val today = LocalDate.now().toEpochDay()
            val food = FoodLog(name = "米饭", mealType = MealType.LUNCH, grams = 100.0,
                nutrition = Nutrition(116.0, 2.6, 25.9, 0.3), dateEpochDay = today)
            repo.insert(food.copy(dateEpochDay = today - 1))
            assertEquals("no_records", provider.today().getString("status"))
            val id = repo.insert(food)
            assertEquals(116.0, provider.today().getDouble("calories_kcal"), 0.0)
            repo.update(food.copy(id = id, grams = 200.0, nutrition = food.nutrition * 2.0))
            assertEquals(232.0, provider.today().getDouble("calories_kcal"), 0.0)
            repo.deleteById(id)
            val empty = provider.today()
            assertEquals("no_records", empty.getString("status"))
            assertEquals(0.0, empty.getDouble("calories_kcal"), 0.0)
            assertFalse(empty.has("water_ml"))
        } finally { db.close() }
    }
}
