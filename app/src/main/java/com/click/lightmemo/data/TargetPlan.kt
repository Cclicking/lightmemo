package com.click.lightmemo.data

import kotlinx.serialization.Serializable
import kotlin.math.abs

/** Product presets; recommendations remain editable before applying. */
enum class FitnessGoal(val label: String, val calorieFactor: Float, val proteinPerKg: Float, val summary: String) {
    MAINTAIN("维持体重", 1f, 1.2f, "以估算的每日消耗为热量参考"),
    FAT_LOSS("减脂", 0.85f, 1.6f, "热量参考为每日消耗的 85%，蛋白质参考为 1.6 g/kg"),
    MUSCLE_GAIN("增肌", 1.1f, 1.6f, "热量参考为每日消耗的 110%，蛋白质参考为 1.6 g/kg"),
}

@Serializable
data class DailyTargets(val calories: Float, val proteinG: Float, val fatG: Float, val carbsG: Float) {
    fun errors(): List<String> = buildList {
        if (!calories.isFinite() || calories !in 500f..10000f) add("热量需为 500–10000 kcal")
        if (!proteinG.isFinite() || proteinG !in 1f..500f) add("蛋白质需为 1–500 g")
        if (!fatG.isFinite() || fatG !in 1f..300f) add("脂肪需为 1–300 g")
        if (!carbsG.isFinite() || carbsG !in 1f..800f) add("碳水需为 1–800 g")
    }

    fun warnings(): List<String> = buildList {
        if (errors().isEmpty()) {
            val macroCalories = proteinG * 4f + fatG * 9f + carbsG * 4f
            if (abs(macroCalories - calories) / calories > 0.1f) {
                add("营养素折算热量约为 ${macroCalories.toInt()} kcal，与热量目标相差超过 10%；如为有意调整，仍可保存。")
            }
        }
    }
}

/** Each entry preserves targets through this inclusive epoch day. */
@Serializable
data class TargetHistoryEntry(val throughEpochDay: Long, val targets: DailyTargets)
