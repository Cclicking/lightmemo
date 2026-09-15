package com.click.lightmemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.data.PresetFood
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.os4.Edit
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType


@Composable
internal fun PresetListContent(
    padding: PaddingValues,
    presets: List<PresetFood>,
    onSelect: (PresetFood) -> Unit,
    onEdit: (PresetFood) -> Unit,
    onCreate: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(AddFoodSheetHeight)
            .overScrollVertical()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            // 与上级页边距一致，标题下再多留一点
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 40.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(presets, key = { it.id }) { preset ->
            Card(
                cornerRadius = 14.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                pressFeedbackType = PressFeedbackType.Sink,
                onClick = { onSelect(preset) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(preset.name, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium)
                        val nutritionText = preset.nutrition?.let {
                            "${it.caloriesKcal.toInt()} kcal · 蛋 ${it.proteinG.formatInput()}g · 碳 ${it.carbsG.formatInput()}g · 脂 ${it.fatG.formatInput()}g"
                        } ?: completeComponentNutrition(preset.components)?.let {
                            "${it.caloriesKcal.toInt()} kcal · 蛋 ${it.proteinG.formatInput()}g · 碳 ${it.carbsG.formatInput()}g · 脂 ${it.fatG.formatInput()}g"
                        } ?: "未设置营养"
                        val portion = buildString {
                            if (preset.portionLabel.isNotBlank()) append(preset.portionLabel).append(" · ")
                            append("约 ${preset.defaultGrams.toInt()}g")
                            if (preset.components.isNotEmpty()) {
                                append(" · ").append("${preset.components.size} 种成分")
                            }
                        }
                        Text(
                            portion,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            nutritionText,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Button(
                        onClick = { onEdit(preset) },
                        minWidth = 48.dp,
                        minHeight = 28.dp,
                        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        // 浅灰底，与列表内小操作按钮一致
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Text("编辑", style = MiuixTheme.textStyles.footnote1)
                    }
                }
            }
        }

        // 列表末尾：添加预设（与外层录入方式按钮一致）
        item {
            MethodButton(
                icon = { Icon(MiuixIcons.Os4.Edit, contentDescription = null) },
                label = "添加预设食物",
                enabled = true,
                onClick = onCreate,
            )
        }
    }
}

