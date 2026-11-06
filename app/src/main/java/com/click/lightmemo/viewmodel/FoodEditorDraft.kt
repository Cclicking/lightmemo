package com.click.lightmemo.viewmodel

import com.click.lightmemo.data.validate
import com.click.lightmemo.domain.FoodLog
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** Raw field values survive recreation, including incomplete or invalid input. */
data class FoodEditorDraft(
    val food: FoodLog,
    val grams: String,
    val date: String,
    val time: String,
) {
    fun validatedFood(): FoodLog? = runCatching {
        food.copy(
            name = food.name.trim(),
            grams = grams.toDouble(),
            dateEpochDay = LocalDate.parse(date).toEpochDay(),
            mealMinuteOfDay = time.takeIf { it.isNotBlank() }?.let {
                LocalTime.parse(it).let { value -> value.hour * 60 + value.minute }
            },
            note = food.note?.trim()?.ifBlank { null },
        ).also { it.validate() }
    }.getOrNull()

    companion object {
        fun from(food: FoodLog) = FoodEditorDraft(
            food = food,
            grams = food.grams.toString().removeSuffix(".0"),
            date = runCatching { LocalDate.ofEpochDay(food.dateEpochDay) }.getOrDefault(LocalDate.now()).toString(),
            time = food.mealMinuteOfDay?.let { "%02d:%02d".format(Locale.ROOT, it / 60, it % 60) }.orEmpty(),
        )
    }
}
