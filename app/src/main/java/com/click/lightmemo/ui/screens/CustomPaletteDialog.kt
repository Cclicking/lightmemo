package com.click.lightmemo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.click.lightmemo.data.FoodPaletteGenerator
import com.click.lightmemo.data.HsvColor
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import com.click.lightmemo.ui.theme.FoodPaletteColors
import com.click.lightmemo.ui.theme.toComposeColors
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
internal fun CustomPaletteDialog(
    show: Boolean,
    selected: Long,
    selectedHarmony: Float,
    onSelect: (Long, Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = FoodPaletteGenerator.argbToHsv(selected)
    var hue by rememberSaveable(show, selected) { mutableFloatStateOf(initial.hue) }
    var saturation by rememberSaveable(show, selected) { mutableFloatStateOf(initial.saturation) }
    var brightness by rememberSaveable(show, selected) { mutableFloatStateOf(initial.value.coerceAtLeast(0.25f)) }
    var harmony by rememberSaveable(show, selected, selectedHarmony) { mutableFloatStateOf(selectedHarmony) }
    var colorEdited by rememberSaveable(show, selected) { mutableStateOf(false) }
    // Editing only the relationship should preserve the exact saved seed, without HSV rounding.
    val seed = if (colorEdited) FoodPaletteGenerator.hsvToArgb(HsvColor(hue, saturation, brightness)) else selected
    val palette = FoodPaletteGenerator.fromCustom(seed, harmony).toComposeColors()
    val screenHeight = LocalConfiguration.current.screenHeightDp

    AnimatedOverlayDialog(
        show = show,
        title = "自定义配色",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.heightIn(max = (screenHeight * 0.65f).dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SaturationBrightnessPalette(
                    hue = hue,
                    saturation = saturation,
                    brightness = brightness,
                    onChange = { s, v -> colorEdited = true; saturation = s; brightness = v },
                )
                PaletteSlider(
                    title = "色相", valueLabel = "${hue.roundToInt()}°",
                    value = hue, range = 0f..360f,
                    onChange = { colorEdited = true; hue = it },
                )
                PaletteSlider(
                    title = "配色差异", valueLabel = "${(harmony * 100).roundToInt()}%",
                    value = harmony, range = 0f..1f, onChange = { harmony = it },
                    trackColors = DifferenceTrackColors,
                )
                CustomPalettePreview(palette)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = { onSelect(seed, harmony); onDismiss() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("应用配色") }
            }
        }
    }
}

@Composable
private fun SaturationBrightnessPalette(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onChange: (Float, Float) -> Unit,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(156.dp).clip(RoundedCornerShape(12.dp))
                .semantics {
                    contentDescription = "调色盘，横向调整饱和度，纵向调整明亮度"
                    stateDescription = "饱和度 ${(saturation * 100).roundToInt()}%，明亮度 ${(brightness * 100).roundToInt()}%"
                    customActions = listOf(
                        CustomAccessibilityAction("增加饱和度") { currentOnChange((saturation + 0.1f).coerceAtMost(1f), brightness); true },
                        CustomAccessibilityAction("降低饱和度") { currentOnChange((saturation - 0.1f).coerceAtLeast(0f), brightness); true },
                        CustomAccessibilityAction("增加明亮度") { currentOnChange(saturation, (brightness + 0.1f).coerceAtMost(1f)); true },
                        CustomAccessibilityAction("降低明亮度") { currentOnChange(saturation, (brightness - 0.1f).coerceAtLeast(0.25f)); true },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        currentOnChange((offset.x / size.width).coerceIn(0f, 1f),
                            (1f - offset.y / size.height * 0.75f).coerceIn(0.25f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    fun update(offset: Offset) {
                        currentOnChange((offset.x / size.width).coerceIn(0f, 1f),
                            (1f - offset.y / size.height * 0.75f).coerceIn(0.25f, 1f))
                    }
                    detectDragGestures(onDragStart = ::update, onDrag = { change, _ ->
                        change.consume()
                        update(change.position)
                    })
                },
        ) {
            val hueColor = Color(FoodPaletteGenerator.hsvToArgb(HsvColor(hue, 1f, 1f)))
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            // Keep the darkest selectable tones usable in charts.
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            val inset = 10.dp.toPx()
            val center = Offset(
                (saturation * size.width).coerceIn(inset, size.width - inset),
                ((1f - brightness) / 0.75f * size.height).coerceIn(inset, size.height - inset),
            )
            drawCircle(Color.Black.copy(alpha = 0.5f), radius = 9.dp.toPx(), center = center)
            drawCircle(Color.White, radius = 7.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
        }
    }
}

private val HueTrackColors = listOf(
    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
)

private val DifferenceTrackColors = listOf(
    Color(0xFF6CA7F2), Color(0xFFB49AE8), Color(0xFFE2BE68), Color(0xFF82DDA5),
)

@Composable
private fun PaletteSlider(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    trackColors: List<Color> = HueTrackColors,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                title,
                modifier = Modifier.alignByBaseline(),
                style = MiuixTheme.textStyles.body2,
            )
            Text(
                valueLabel,
                modifier = Modifier.alignByBaseline(),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth().height(SliderDefaults.MinHeight)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(if (rtl) trackColors.reversed() else trackColors)),
            )
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                colors = SliderDefaults.sliderColors(
                    foregroundColor = Color.Transparent,
                    backgroundColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth()
                    .semantics { contentDescription = title },
            )
        }
    }
}

@Composable
private fun CustomPalettePreview(palette: FoodPaletteColors) {
    val surface = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.06f)
    val nutrients = listOf("热量" to palette.calorie, "蛋白质" to palette.protein,
        "碳水" to palette.carbs, "脂肪" to palette.fat)
    val meals = listOf("早餐", "午餐", "晚餐", "加餐").zip(palette.structure)
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(surface).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        nutrients.zip(meals).forEach { (nutrient, meal) ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PaletteLegendItem(nutrient.first, nutrient.second)
                PaletteLegendItem(meal.first, meal.second)
            }
        }
    }
}

@Composable
private fun PaletteLegendItem(label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
