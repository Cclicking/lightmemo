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


private val ProteinTarget = AddFoodProteinTarget
private val CarbsTarget = AddFoodCarbsTarget
private val FatTarget = AddFoodFatTarget

@Composable
internal fun ReviewContent(
    padding: PaddingValues,
    result: MealRecognition,
    recognizing: Boolean,
    recognitionStage: RecognitionStage?,
    replacingDishId: String?,
    selectedComponentId: String?,
    mealType: MealType,
    onMealType: (MealType) -> Unit,
    onWeightChange: (String, Double) -> Unit,
    onRemoveComponent: (String) -> Unit,
    onRemoveDish: (String) -> Unit,
    onReplaceComponent: (FoodComponent) -> Unit,
    onReplaceDish: (String, String) -> Unit,
    error: String?,
    onSave: () -> Unit,
    listState: LazyListState,
    palette: FoodPaletteColors,
    canRetryRecognition: Boolean = false,
    onRetryRecognition: () -> Unit = {},
) {
    val total = result.nutrition
    val calorieColor = if (total.caloriesKcal > 1800.0) palette.overTarget else palette.calorie
    var replacingDish by remember { mutableStateOf<RecognizedDish?>(null) }
    var replacementName by remember { mutableStateOf("") }
    AnimatedOverlayDialog(
        show = replacingDish != null,
        title = "更换菜品",
        summary = "输入正确的菜品名称，按当前重量重新识别组成和营养",
        onDismissRequest = { replacingDish = null },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = replacementName,
                onValueChange = { replacementName = it },
                label = "菜品名称",
                singleLine = true,
                colors = dialogFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    replacingDish?.let { onReplaceDish(it.id, replacementName) }
                    replacingDish = null
                },
                enabled = replacementName.isNotBlank() && !recognizing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("确认更换") }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(AddFoodSheetHeight)
            .navigationBarsPadding()
            .overScrollVertical(),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 40.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { MealTypeSelector(mealType, onMealType) }

        if (recognizing) {
            item { RecognitionStatusCard(recognitionStage) }
        }

        // 总览卡片：热量 + 宏量环（进度条与圆环间距 27dp，圆环高度对齐文字）
        item {
            Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(27.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("热量", style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${total.caloriesKcal.toInt()} kcal",
                                style = MiuixTheme.textStyles.title4,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        LinearProgressIndicator(
                            progress = (total.caloriesKcal / 1800.0).toFloat().coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth(),
                            height = 8.dp,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = calorieColor),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        MacroRing("蛋白质", total.proteinG, ProteinTarget, palette.protein, Modifier.weight(1f))
                        MacroRing("碳水", total.carbsG, CarbsTarget, palette.carbs, Modifier.weight(1f))
                        MacroRing("脂肪", total.fatG, FatTarget, palette.fat, Modifier.weight(1f))
                    }
                }
            }
        }

        if (result.imageQualityIssues.isNotEmpty()) {
            item {
                Text(
                    text = "图片质量：${result.imageQualityIssues.joinToString("；")}",
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        // 每道菜一张可折叠卡片
        items(result.dishes, key = { it.id }) { dish ->
            DishResultCard(
                dish = dish,
                replacing = dish.id == replacingDishId,
                selectedComponentId = selectedComponentId,
                selected = replacingDish?.id == dish.id,
                onWeightChange = onWeightChange,
                onRemoveComponent = onRemoveComponent,
                onRemoveDish = onRemoveDish,
                onReplaceComponent = onReplaceComponent,
                onReplaceDish = {
                    replacingDish = it
                    replacementName = it.name
                },
                enabled = !recognizing,
            )
        }

        if (result.confirmationQuestions.isNotEmpty()) {
            item {
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("建议确认", style = MiuixTheme.textStyles.title4)
                        result.confirmationQuestions.forEach { question ->
                            Text(
                                text = "• $question",
                                style = MiuixTheme.textStyles.subtitle,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RecognitionErrorRow(
                    error = error,
                    canRetry = canRetryRecognition,
                    onRetry = onRetryRecognition,
                )
                Button(
                    onClick = onSave,
                    enabled = !recognizing && result.dishes.isNotEmpty() && result.dishes.all { dish ->
                        dish.allComponents.isNotEmpty()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("保存 ${result.dishes.size} 道菜")
                }
            }
        }
    }
}

@Composable
private fun MacroRing(label: String, value: Double, target: Float, color: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var ringSize by remember { mutableStateOf(32.dp) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            progress = (value / target).toFloat().coerceIn(0f, 1f),
            colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = color),
            size = ringSize,
            strokeWidth = (ringSize.value * 4f / 36f).dp.coerceAtLeast(3.dp),
        )
        Spacer(Modifier.width(6.dp))
        Column(
            Modifier.onSizeChanged { coords ->
                val textHeight = with(density) { coords.height.toDp() }
                if (textHeight > 0.dp && abs(textHeight.value - ringSize.value) > 0.5f) {
                    ringSize = textHeight
                }
            },
        ) {
            Text(label, style = MiuixTheme.textStyles.footnote2, maxLines = 1)
            Text("${value.formatInput()}g", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Bold)
        }
    }
}

