package com.click.lightmemo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodColorPaletteTest {
    @Test
    fun differenceControlsHueDistanceWithoutChangingCalorieColor() {
        val seed = 0xFF3478E5L
        val similar = FoodPaletteGenerator.fromCustom(seed, 0f)
        val colorful = FoodPaletteGenerator.fromCustom(seed, 1f)
        assertEquals(similar.calorie, colorful.calorie)
        val seedHue = FoodPaletteGenerator.argbToHsv(seed).hue
        (similar.structure + listOf(similar.protein, similar.carbs, similar.fat)).forEach { color ->
            val distance = kotlin.math.abs(FoodPaletteGenerator.argbToHsv(color).hue - seedHue)
            assertTrue(minOf(distance, 360f - distance) < 1f)
        }
        assertTrue(colorful.structure.map { FoodPaletteGenerator.argbToHsv(it).hue.toInt() }.distinct().size == 4)
    }

    @Test
    fun customPaletteRespondsToSaturationAndBrightness() {
        fun palette(saturation: Float, value: Float) = FoodPaletteGenerator.fromCustom(
            FoodPaletteGenerator.hsvToArgb(HsvColor(210f, saturation, value)), 0.6f)
        val vivid = FoodPaletteGenerator.argbToHsv(palette(0.9f, 0.9f).calorie)
        val soft = FoodPaletteGenerator.argbToHsv(palette(0.2f, 0.9f).calorie)
        val dark = FoodPaletteGenerator.argbToHsv(palette(0.9f, 0.3f).calorie)
        assertTrue(soft.saturation < vivid.saturation)
        assertTrue(dark.value < vivid.value)
        assertTrue(palette(0.2f, 0.3f).structure.all { it ushr 24 == 0xFFL })
    }

    @Test
    fun generatedPaletteKeepsOpaqueDistinctApplicationColors() {
        val palette = FoodPaletteGenerator.fromSeed(0xFF3478E5L)

        assertTrue(palette.structure.all { it ushr 24 == 0xFFL })
        assertEquals(4, palette.structure.distinct().size)
        assertNotEquals(palette.protein, palette.carbs)
        assertNotEquals(palette.carbs, palette.fat)
        assertNotEquals(palette.calorie, palette.overTarget)
    }

    @Test
    fun multicolorPresetRemainsStable() {
        assertEquals(FoodColorPalette.Multicolor, FoodColorPalette.forPreset(ColorThemePreset.MULTICOLOR))
    }

    @Test
    fun changingSeedChangesGeneratedPalette() {
        val blue = FoodColorPalette.forPreset(ColorThemePreset.CUSTOM, 0xFF3478E5L)
        val orange = FoodColorPalette.forPreset(ColorThemePreset.CUSTOM, 0xFFF28B3CL)

        assertNotEquals(blue, orange)
        assertNotEquals(blue.calorie, orange.calorie)
    }
}
