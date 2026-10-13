package com.click.lightmemo.mcp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.click.lightmemo.data.FoodDatabase
import com.click.lightmemo.data.AppSettings
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
            var settings = AppSettings(dailyCalorieTarget = 2000f, proteinTargetG = 100f,
                carbsTargetG = 250f, fatTargetG = 60f)
            val provider = LocalNutritionProvider(repo) { settings }
            val today = LocalDate.now().toEpochDay()
            val food = FoodLog(name = "米饭", mealType = MealType.LUNCH, grams = 100.0,
                nutrition = Nutrition(116.0, 2.6, 25.9, 0.3), dateEpochDay = today)
            repo.insert(food.copy(dateEpochDay = today - 1))
            assertEquals("no_records", provider.today().getString("status"))
            val targets = provider.today().getJSONObject("targets")
            assertEquals(2000.0, targets.getDouble("calories_kcal"), 0.0)
            assertEquals(100.0, targets.getDouble("protein_g"), 0.0)
            assertEquals(250.0, targets.getDouble("carbohydrates_g"), 0.0)
            assertEquals(60.0, targets.getDouble("fat_g"), 0.0)
            settings = settings.copy(dailyCalorieTarget = 2200f)
            assertEquals(2200.0, provider.today().getJSONObject("targets").getDouble("calories_kcal"), 0.0)
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

    @Test fun missingTargetsAreNullAndRecommendedTargetsMatchAppSettings() {
        val date = LocalDate.now()
        val zone = java.time.ZoneId.systemDefault()
        val empty = nutritionResult(emptyList(), date, zone, AppSettings())
        assertTrue(empty.getJSONObject("targets").isNull("protein_g"))
        assertTrue(empty.getJSONArray("missing_fields").toString().contains("targets.protein_g"))
        val settings = AppSettings(weightKg = 65f, heightCm = 170f, ageYears = 30)
        val targets = nutritionResult(emptyList(), date, zone, settings).getJSONObject("targets")
        assertTrue(settings.effectiveProteinG > 0f)
        assertEquals(settings.effectiveProteinG.toDouble(), targets.getDouble("protein_g"), 0.0)
        assertEquals(settings.effectiveCarbsG.toDouble(), targets.getDouble("carbohydrates_g"), 0.0)
        assertEquals(settings.effectiveFatG.toDouble(), targets.getDouble("fat_g"), 0.0)
    }
}
