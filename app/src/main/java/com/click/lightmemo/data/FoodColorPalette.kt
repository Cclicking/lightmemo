package com.click.lightmemo.data

import kotlin.math.max

/** Preset palettes for the nutrition and meal-structure visualizations. */
enum class ColorThemePreset(val label: String, val seedColor: Long) {
    MULTICOLOR("多彩", 0xFF4F86E8),
    BLUE("蓝色", 0xFF3478E5),
    ORANGE("橙色", 0xFFF28B3C),
    CUSTOM("自定义", 0xFF4F86E8),
}

/**
 * One source of truth for every product-owned chart color.
 *
 * The Miuix theme still owns semantic UI colors. These colors are intentionally kept separate
 * because a chart series must remain recognizable even when the system theme changes.
 */
data class FoodColorPalette(
    val calorie: Long,
    val overTarget: Long,
    val protein: Long,
    val carbs: Long,
    val fat: Long,
    val breakfast: Long,
    val lunch: Long,
    val dinner: Long,
    val snack: Long,
) {
    val structure: List<Long>
        get() = listOf(breakfast, lunch, dinner, snack)

    companion object {
        val Multicolor = FoodColorPalette(
            calorie = 0xFF6CA7F2,
            overTarget = 0xFFF39A38,
            protein = 0xFFF3A17C,
            carbs = 0xFF2F7D2B,
            fat = 0xFFFFB300,
            breakfast = 0xFFFFC857,
            lunch = 0xFF62C7A5,
            dinner = 0xFF6CA7F2,
            snack = 0xFFB49AE8,
        )

        fun forPreset(preset: ColorThemePreset, seedColor: Long = preset.seedColor): FoodColorPalette =
            when (preset) {
                ColorThemePreset.MULTICOLOR -> Multicolor
                ColorThemePreset.BLUE,
                ColorThemePreset.ORANGE,
                ColorThemePreset.CUSTOM,
                -> FoodPaletteGenerator.fromSeed(seedColor)
            }
    }
}

data class HsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

/** Pure color math so palette generation remains deterministic and easy to test. */
object FoodPaletteGenerator {
    fun fromSeed(seedColor: Long): FoodColorPalette {
        val seed = argbToHsv(seedColor)
        val hue = seed.hue
        val saturation = max(seed.saturation, 0.58f)
        val value = max(seed.value, 0.72f)

        fun tone(offset: Float, saturationScale: Float, valueScale: Float): Long = hsvToArgb(
            HsvColor(
                hue = (hue + offset).mod(360f),
                saturation = (saturation * saturationScale).coerceIn(0.48f, 0.92f),
                value = (value * valueScale).coerceIn(0.68f, 0.98f),
            ),
        )

        return FoodColorPalette(
            calorie = tone(0f, 0.82f, 0.92f),
            overTarget = tone(24f, 0.78f, 0.98f),
            protein = tone(330f, 0.74f, 0.98f),
            carbs = tone(52f, 0.72f, 0.88f),
            fat = tone(178f, 0.84f, 0.96f),
            breakfast = tone(0f, 0.68f, 0.98f),
            lunch = tone(58f, 0.66f, 0.92f),
            dinner = tone(184f, 0.76f, 0.96f),
            snack = tone(282f, 0.62f, 0.98f),
        )
    }

    fun argbToHsv(argb: Long): HsvColor {
        val red = ((argb shr 16) and 0xFF).toFloat() / 255f
        val green = ((argb shr 8) and 0xFF).toFloat() / 255f
        val blue = (argb and 0xFF).toFloat() / 255f
        val max = maxOf(red, green, blue)
        val min = minOf(red, green, blue)
        val delta = max - min
        val hue = when {
            delta == 0f -> 0f
            max == red -> (60f * ((green - blue) / delta)).mod(360f)
            max == green -> 60f * ((blue - red) / delta + 2f)
            else -> 60f * ((red - green) / delta + 4f)
        }
        return HsvColor(
            hue = if (hue < 0f) hue + 360f else hue,
            saturation = if (max == 0f) 0f else delta / max,
            value = max,
        )
    }

    fun hsvToArgb(hsv: HsvColor): Long {
        val h = hsv.hue.mod(360f) / 60f
        val s = hsv.saturation.coerceIn(0f, 1f)
        val v = hsv.value.coerceIn(0f, 1f)
        val chroma = v * s
        val x = chroma * (1f - kotlin.math.abs(h.mod(2f) - 1f))
        val match = v - chroma
        val (red, green, blue) = when (h.toInt()) {
            0 -> Triple(chroma, x, 0f)
            1 -> Triple(x, chroma, 0f)
            2 -> Triple(0f, chroma, x)
            3 -> Triple(0f, x, chroma)
            4 -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }
        val r = ((red + match) * 255f).toInt().coerceIn(0, 255)
        val g = ((green + match) * 255f).toInt().coerceIn(0, 255)
        val b = ((blue + match) * 255f).toInt().coerceIn(0, 255)
        return 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }
}
