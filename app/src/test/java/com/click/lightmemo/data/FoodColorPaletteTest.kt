package com.click.lightmemo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodColorPaletteTest {
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
