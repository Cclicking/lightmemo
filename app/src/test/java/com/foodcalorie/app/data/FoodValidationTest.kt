package com.foodcalorie.app.data

import com.foodcalorie.app.domain.FoodLog
import com.foodcalorie.app.domain.MealType
import com.foodcalorie.app.domain.Nutrition
import org.junit.Assert.*
import org.junit.Test

class FoodValidationTest {
    private val valid = FoodLog(name = "食物", mealType = MealType.LUNCH, grams = 100.0, nutrition = Nutrition())

    @Test fun rejectsInvalidWeightsAndNutrients() {
        val invalid = listOf(
            valid.copy(grams = 0.0), valid.copy(grams = -1.0), valid.copy(grams = Double.NaN),
            valid.copy(grams = Double.POSITIVE_INFINITY), valid.copy(nutrition = Nutrition(-1.0)),
            valid.copy(nutrition = Nutrition(proteinG = Double.NaN)),
            valid.copy(nutrition = Nutrition(fatG = Double.POSITIVE_INFINITY)),
        )
        invalid.forEach { log ->
            assertThrows(IllegalArgumentException::class.java) { log.validate() }
        }
        valid.validate() // Zero-calorie food is valid; missing/invalid data is not.
    }

    @Test fun rejectsInvalidDatesNamesAndMealTimes() {
        listOf(valid.copy(name = " "), valid.copy(dateEpochDay = Long.MAX_VALUE),
            valid.copy(mealMinuteOfDay = -1), valid.copy(mealMinuteOfDay = 1440)).forEach { log ->
            assertThrows(IllegalArgumentException::class.java) { log.validate() }
        }
    }
}
