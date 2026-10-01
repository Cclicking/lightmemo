package com.click.lightmemo.domain

import java.util.Locale
import kotlinx.serialization.Serializable

/** Conservative normalization: keep preparation words so different dishes stay distinct. */
fun normalizeFoodName(name: String): String = name.trim().lowercase(Locale.ROOT)
    .filterNot { it.isWhitespace() || it == '\u3000' }

@Serializable
data class PersonalFoodMemory(
    val canonicalName: String,
    val aliases: Set<String> = emptySet(),
    val typicalGrams: Double,
    val minGrams: Double,
    val maxGrams: Double,
    val nutrition: Nutrition,
    val nutritionGrams: Double = typicalGrams,
    val components: List<FoodComponent> = emptyList(),
    val preferredFoodReference: NutritionReference? = null,
    val preferredComponentReferences: Map<String, NutritionReference> = emptyMap(),
    val correctionCount: Int = 0,
    val useCount: Int = 0,
    val lastUsedAtMillis: Long = 0,
    val lastCorrectedAtMillis: Long = 0,
    val confidence: Double = 0.0,
    val recentStableCount: Int = 0,
    val lastConfirmedGrams: Double = 0.0,
    val lastCompositionKey: String = "",
)
