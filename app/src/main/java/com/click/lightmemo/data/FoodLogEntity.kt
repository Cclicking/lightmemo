package com.click.lightmemo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition

@Entity(
    tableName = "food_logs",
    indices = [Index(value = ["dateEpochDay"])],
)
data class FoodLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val mealType: String,
    val grams: Double,
    @ColumnInfo(name = "caloriesKcal") val caloriesKcal: Double,
    @ColumnInfo(name = "proteinG") val proteinG: Double,
    @ColumnInfo(name = "carbsG") val carbsG: Double,
    @ColumnInfo(name = "fatG") val fatG: Double,
    val componentsJson: String,
    val imageUri: String?,
    val dateEpochDay: Long,
    val createdAtMillis: Long,
    val mealMinuteOfDay: Int?,
    val note: String?,
    val mealTagsJson: String,
)

fun FoodLog.toEntity(): FoodLogEntity = FoodLogEntity(
    id = id,
    name = name,
    mealType = mealType.name,
    grams = grams,
    caloriesKcal = nutrition.caloriesKcal,
    proteinG = nutrition.proteinG,
    carbsG = nutrition.carbsG,
    fatG = nutrition.fatG,
    componentsJson = serializeComponents(components),
    imageUri = imageUri,
    dateEpochDay = dateEpochDay,
    createdAtMillis = createdAtMillis,
    mealMinuteOfDay = mealMinuteOfDay,
    note = note,
    mealTagsJson = serializeMealTags(mealTags),
)

fun FoodLogEntity.toDomain(): FoodLog = FoodLog(
    id = id,
    name = name,
    mealType = MealType.valueOf(mealType),
    grams = grams,
    nutrition = Nutrition(
        caloriesKcal = caloriesKcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
    ),
    components = parseComponentsJson(componentsJson),
    imageUri = imageUri,
    dateEpochDay = dateEpochDay,
    createdAtMillis = createdAtMillis,
    mealMinuteOfDay = mealMinuteOfDay,
    note = note?.takeIf { it.isNotBlank() },
    mealTags = parseMealTagsJson(mealTagsJson),
)
