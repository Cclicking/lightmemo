package com.foodcalorie.app.data

import com.foodcalorie.app.domain.Nutrition
import kotlinx.serialization.Serializable

@Serializable
data class PresetFood(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val defaultGrams: Double,
    val portionLabel: String,
    val nutrition: Nutrition? = null,
)

val DefaultPresetFoods = listOf(
    PresetFood(id = "rice", name = "米饭", defaultGrams = 150.0, portionLabel = "1 小碗", nutrition = Nutrition(174.0, 3.9, 38.9, 0.5)),
    PresetFood(id = "mantou", name = "馒头", defaultGrams = 100.0, portionLabel = "1 个", nutrition = Nutrition(223.0, 7.0, 47.0, 1.1)),
    PresetFood(id = "egg", name = "鸡蛋", defaultGrams = 50.0, portionLabel = "1 个", nutrition = Nutrition(72.0, 6.3, 0.4, 4.8)),
    PresetFood(id = "milk", name = "牛奶", defaultGrams = 250.0, portionLabel = "1 盒", nutrition = Nutrition(162.0, 8.0, 12.0, 8.8)),
    PresetFood(id = "chicken_breast", name = "鸡胸肉", defaultGrams = 120.0, portionLabel = "1 块", nutrition = Nutrition(198.0, 37.1, 0.0, 4.3)),
    PresetFood(id = "apple", name = "苹果", defaultGrams = 200.0, portionLabel = "1 个", nutrition = Nutrition(104.0, 0.6, 27.6, 0.3)),
    PresetFood(id = "banana", name = "香蕉", defaultGrams = 120.0, portionLabel = "1 根", nutrition = Nutrition(107.0, 1.3, 27.4, 0.4)),
    PresetFood(id = "bread", name = "全麦面包", defaultGrams = 60.0, portionLabel = "2 片", nutrition = Nutrition(154.0, 7.2, 25.8, 2.4)),
    PresetFood(id = "yogurt", name = "酸奶", defaultGrams = 150.0, portionLabel = "1 杯", nutrition = Nutrition(93.0, 5.3, 11.9, 3.3)),
    PresetFood(id = "oats", name = "燕麦片", defaultGrams = 40.0, portionLabel = "1 份", nutrition = Nutrition(150.0, 5.3, 26.4, 2.7)),
    PresetFood(id = "beef", name = "牛肉", defaultGrams = 100.0, portionLabel = "1 份", nutrition = Nutrition(250.0, 26.0, 0.0, 15.0)),
    PresetFood(id = "salmon", name = "三文鱼", defaultGrams = 120.0, portionLabel = "1 份", nutrition = Nutrition(250.0, 27.0, 0.0, 15.0)),
)
