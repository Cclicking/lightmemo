package com.click.lightmemo.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.domain.MealRecognition
import com.click.lightmemo.domain.RecognizedDish
import com.click.lightmemo.domain.RecognitionStage
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import com.click.lightmemo.ui.theme.FoodPaletteColors
import com.click.lightmemo.ui.utils.overScrollVertical
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.os4.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DishResultCard(
    dish: RecognizedDish,
    replacing: Boolean,
    selectedComponentId: String?,
    selected: Boolean,
    onWeightChange: (String, Double) -> Unit,
    onRemoveComponent: (String) -> Unit,
    onRemoveDish: (String) -> Unit,
    onDatabaseSearch: (FoodComponent) -> Unit,
    onReplaceComponent: (FoodComponent) -> Unit,
    onReplaceDish: (RecognizedDish) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember(dish.id) { mutableStateOf(true) }
    // ArrowRight 默认向右；展开时逆时针 90°（向上），收起再转 180°（向下）
    val expandIconRotation by animateFloatAsState(
        targetValue = if (expanded) -90f else 90f,
        animationSpec = tween(durationMillis = 200),
        label = "expandIconRotation",
    )

    Card(
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        dish.name,
                        style = MiuixTheme.textStyles.title4,
                        color = if (selected) MiuixTheme.colorScheme.onSurfaceVariantSummary
                        else MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(
                            interactionSource = null,
                            indication = null,
                            enabled = enabled,
                            onClickLabel = "更换菜品",
                            onClick = { onReplaceDish(dish) },
                        ),
                    )
                    Text(
                        text = "约 ${dish.nutrition.caloriesKcal.toInt()} kcal · ${dish.grams.toInt()}g",
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        MiuixIcons.Os4.Close,
                        contentDescription = "移除菜品",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(enabled = enabled) { onRemoveDish(dish.id) }
                            .padding(4.dp),
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { expanded = !expanded },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            MiuixIcons.Basic.ArrowRight,
                            contentDescription = if (expanded) "收起" else "展开",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .size(width = 10.dp, height = 16.dp)
                                .rotate(expandIconRotation),
                        )
                    }
                }
            }

            if (replacing) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LinearProgressIndicator(progress = null, modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "正在更换菜品…",
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    dish.components.forEach { component ->
                        ComponentResultRow(
                            component = component,
                            onWeightChange = onWeightChange,
                            onRemove = onRemoveComponent,
                            onMatch = onDatabaseSearch,
                            onNameClick = onReplaceComponent,
                            selected = selectedComponentId == component.id,
                            enabled = enabled,
                        )
                    }
                    dish.children.forEach { child ->
                        if (dish.components.isNotEmpty() || child != dish.children.first()) {
                            Spacer(Modifier.height(6.dp))
                        }
                        Text(
                            child.name,
                            style = MiuixTheme.textStyles.subtitle,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        child.components.forEach { component ->
                            ComponentResultRow(
                                component = component,
                                onWeightChange = onWeightChange,
                                onRemove = onRemoveComponent,
                                onMatch = onDatabaseSearch,
                                onNameClick = onReplaceComponent,
                                selected = selectedComponentId == component.id,
                                enabled = enabled,
                            )
                        }
                    }
                    if (dish.uncertaintyReason != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "主要误差：${dish.uncertaintyReason}",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun ComponentResultRow(
    component: FoodComponent,
    onWeightChange: (String, Double) -> Unit,
    onRemove: (String) -> Unit,
    onMatch: (FoodComponent) -> Unit,
    onNameClick: ((FoodComponent) -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    var input by remember(component.id) { mutableStateOf(component.estimatedWeightG.formatInput()) }
    var showEdit by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                component.name,
                style = MiuixTheme.textStyles.body1,
                color = if (selected) MiuixTheme.colorScheme.onSurfaceVariantSummary
                else MiuixTheme.colorScheme.onSurface,
                modifier = onNameClick?.let {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        enabled = enabled,
                        onClickLabel = "更换食物",
                        onClick = { it(component) },
                    )
                } ?: Modifier,
            )
            val ref = component.nutritionReference
            Text(
                text = ref?.let {
                    val source = if (it.dataType.contains("中国")) "中国食物成分表" else "USDA"
                    "来自 $source #${it.sourceId}"
                } ?: "未匹配到营养数据",
                style = MiuixTheme.textStyles.footnote2,
                color = if (ref == null) MiuixTheme.colorScheme.error
                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (ref == null) {
                Text(
                    text = "手动匹配数据库",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        enabled = enabled,
                        onClick = { onMatch(component) },
                    ),
                )
            }
        }

        // 克重
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.06f))
                .clickable { showEdit = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text("${input}g", style = MiuixTheme.textStyles.footnote1)
        }

        Text(
            text = "${component.nutrition.caloriesKcal.toInt()} kcal",
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )

        Icon(
            MiuixIcons.Os4.Close,
            contentDescription = "移除",
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable { onRemove(component.id) }
                .padding(2.dp),
        )
    }

    NumberInputDialog(
        show = showEdit,
        title = component.name,
        summary = "修改克重",
        initial = input,
        onDismiss = { showEdit = false },
        onConfirm = { draft ->
            input = draft
            draft.toDoubleOrNull()?.let { onWeightChange(component.id, it) }
            showEdit = false
        },
    )
}

