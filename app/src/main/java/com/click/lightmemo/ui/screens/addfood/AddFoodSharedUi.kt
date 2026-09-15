package com.click.lightmemo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import com.click.lightmemo.viewmodel.DefaultMealTags
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextFieldColors
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme


internal val AddFoodSheetHeight = 780.dp
internal val timeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
internal val clockFormatter = DateTimeFormatter.ofPattern("HH:mm")
internal val AddFoodProteinTarget = 120f
internal val AddFoodCarbsTarget = 250f
internal val AddFoodFatTarget = 60f

internal fun Double.formatInput(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(java.util.Locale.ROOT, this)

internal fun Int.toLocalTime(): LocalTime = LocalTime.of(this / 60, this % 60)

@Composable
internal fun isLightTheme(): Boolean {
    val bg = MiuixTheme.colorScheme.background
    return (0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue) > 0.5f
}

/** bottomsheet 内次要按钮：浅色白底，深色 surfaceContainer（与 MethodButton 一致） */
@Composable
internal fun sheetSecondaryButtonColors(enabled: Boolean = true): ButtonColors {
    val isLight = isLightTheme()
    val container = when {
        !enabled && isLight -> MiuixTheme.colorScheme.secondaryVariant
        !enabled -> MiuixTheme.colorScheme.disabledSecondaryVariant
        isLight -> Color.White
        else -> MiuixTheme.colorScheme.surfaceContainer
    }
    val content = if (enabled) {
        MiuixTheme.colorScheme.onSurface
    } else {
        MiuixTheme.colorScheme.disabledOnSurface
    }
    return ButtonDefaults.buttonColors(
        color = container,
        disabledColor = if (isLight) {
            MiuixTheme.colorScheme.secondaryVariant
        } else {
            MiuixTheme.colorScheme.disabledSecondaryVariant
        },
        contentColor = content,
        disabledContentColor = MiuixTheme.colorScheme.disabledOnSurface,
    )
}

@Composable
internal fun LeaveConfirmDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AnimatedOverlayDialog(
        show = show,
        title = "放弃当前内容？",
        summary = "返回后未保存的填写将丢失",
        onDismissRequest = onDismiss,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("放弃并返回")
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text("继续填写")
            }
        }
    }
}

@Composable
internal fun sheetFieldColors(): TextFieldColors {
    return if (isLightTheme()) {
        TextFieldDefaults.textFieldColors(backgroundColor = Color.White)
    } else {
        TextFieldDefaults.textFieldColors()
    }
}

@Composable
internal fun dialogFieldColors(): TextFieldColors {
    return if (isLightTheme()) {
        TextFieldDefaults.textFieldColors(backgroundColor = MiuixTheme.colorScheme.secondaryContainer)
    } else {
        TextFieldDefaults.textFieldColors()
    }
}

@Composable
internal fun MealTagRow(selectedTags: Set<String>, onToggle: (String) -> Unit) {
    val chunked = DefaultMealTags.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chunked.forEach { rowTags ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowTags.forEach { tag ->
                    MealTagChip(
                        label = tag,
                        selected = tag in selectedTags,
                        onClick = { onToggle(tag) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowTags.size < 4) {
                    repeat(4 - rowTags.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun MealTagChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = isLightTheme()
    val bg = if (selected) {
        if (isLight) MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
        else MiuixTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        if (isLight) MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.06f)
        else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.14f)
    }
    val fg = if (selected) {
        MiuixTheme.colorScheme.primary.copy(alpha = if (isLight) 0.72f else 0.92f)
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = if (isLight) 0.45f else 0.72f)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
internal fun TimePill(text: String, onClick: () -> Unit) {
    val bg = if (isLightTheme()) {
        MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.06f)
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.14f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface.copy(
                alpha = if (isLightTheme()) 0.45f else 0.72f,
            ),
        )
    }
}

@Composable
internal fun MethodButton(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    highlightIcon: Boolean = false,
) {
    val isLight = isLightTheme()
    val contentColor = if (enabled) {
        MiuixTheme.colorScheme.onSurface
    } else {
        MiuixTheme.colorScheme.disabledOnSurface
    }
    val container = when {
        !enabled && isLight -> MiuixTheme.colorScheme.secondaryVariant
        !enabled -> MiuixTheme.colorScheme.disabledSecondaryVariant
        isLight -> Color.White
        else -> MiuixTheme.colorScheme.surfaceContainer
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            icon()
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = contentColor,
        )
    }
}

@Composable
fun MealTypeSelector(
    selected: MealType,
    onSelect: (MealType) -> Unit,
) {
    val types = MealType.entries
    TabRowWithContour(
        tabs = types.map { it.label },
        selectedTabIndex = types.indexOf(selected).coerceAtLeast(0),
        onTabSelected = { index -> onSelect(types[index]) },
    )
}
