package com.click.lightmemo.domain

import org.junit.Assert.*
import org.junit.Test

class CalorieBalanceTest {
    @Test
    fun missingIntakeAndMissingExpenditureDoNotProduceFalseBalances() {
        val days = calorieBalanceDays(
            listOf(DayNutritionSummary(1, Nutrition()), DayNutritionSummary(2, Nutrition(caloriesKcal = 1800.0))),
            setOf(2), mapOf(1L to 2200.0),
        )
        assertNull(days[0].intakeKcal)
        assertNull(days[0].balanceKcal)
        assertNull(days[1].expenditureKcal)
        assertNull(days[1].balanceKcal)
    }

    @Test
    fun balanceUsesExpenditureMinusIntakeAndMatchesDates() {
        val days = calorieBalanceDays(
            listOf(DayNutritionSummary(1, Nutrition(caloriesKcal = 1800.0)),
                DayNutritionSummary(2, Nutrition(caloriesKcal = 2500.0))),
            setOf(1, 2), mapOf(2L to 2100.0, 1L to 2200.0, 3L to 3000.0),
        )
        assertEquals(400.0, days[0].balanceKcal!!, 0.0)
        assertEquals(-400.0, days[1].balanceKcal!!, 0.0)
        assertEquals(2, days.size)
    }

    @Test
    fun explicitZeroIsPreservedAsRecordedData() {
        val day = calorieBalanceDays(listOf(DayNutritionSummary(1, Nutrition())), setOf(1), mapOf(1L to 0.0)).single()
        assertEquals(0.0, day.intakeKcal!!, 0.0)
        assertEquals(0.0, day.expenditureKcal!!, 0.0)
        assertEquals(0.0, day.balanceKcal!!, 0.0)
    }
}
