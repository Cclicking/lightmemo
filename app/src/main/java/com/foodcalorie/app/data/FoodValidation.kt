package com.foodcalorie.app.data

import com.foodcalorie.app.domain.FoodLog
import com.foodcalorie.app.domain.Nutrition

fun Nutrition.isValid(): Boolean =
    listOf(caloriesKcal, proteinG, carbsG, fatG).all { it.isFinite() && it >= 0.0 }

fun FoodLog.validate() {
    require(name.isNotBlank()) { "请输入食物名称" }
    require(grams.isFinite() && grams > 0.0) { "重量必须是大于 0 的有效数字" }
    require(nutrition.isValid()) { "热量和营养素必须是非负有效数字" }
    require(dateEpochDay in java.time.LocalDate.MIN.toEpochDay()..java.time.LocalDate.MAX.toEpochDay()) { "记录日期无效" }
    require(mealMinuteOfDay == null || mealMinuteOfDay in 0..1439) { "用餐时间无效" }
    require(components.all { component ->
        component.estimatedWeightG.isFinite() && component.estimatedWeightG >= 0.0 &&
            component.weightMinG.isFinite() && component.weightMinG >= 0.0 &&
            component.weightMaxG.isFinite() && component.weightMaxG >= component.estimatedWeightG &&
            component.weightMinG <= component.estimatedWeightG &&
            component.confidence.isFinite() && component.confidence in 0.0..1.0 &&
            component.nutritionReference?.per100g?.isValid() != false
    }) { "食物组成的重量或营养数据无效" }
}
