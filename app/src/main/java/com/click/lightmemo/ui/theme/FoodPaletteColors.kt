package com.click.lightmemo.ui.theme

import androidx.compose.ui.graphics.Color
import com.click.lightmemo.data.FoodColorPalette

/** Compose-facing form of the product chart palette. */
data class FoodPaletteColors(
    val calorie: Color,
    val overTarget: Color,
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val structure: List<Color>,
) {
    companion object {
        val Default = FoodColorPalette.Multicolor.toComposeColors()
    }
}

fun FoodColorPalette.toComposeColors(): FoodPaletteColors = FoodPaletteColors(
    calorie = Color(calorie),
    overTarget = Color(overTarget),
    protein = Color(protein),
    carbs = Color(carbs),
    fat = Color(fat),
    structure = structure.map(::Color),
)
