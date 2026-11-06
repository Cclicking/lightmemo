package com.click.lightmemo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.click.lightmemo.domain.RecognizedDish
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun NutritionLabelResultCard(
    dish: RecognizedDish,
    enabled: Boolean,
    onWeightChange: (String, Double) -> Unit,
) {
    val label = dish.nutritionLabel ?: return
    var weight by remember(dish.id) { mutableStateOf(label.weightG.takeIf { it > 0.0 }?.formatInput().orEmpty()) }
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val per100g = label.per100g
    Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("营养成分表", style = MiuixTheme.textStyles.footnote2, color = secondary)
                    Text(dish.name, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "已识别",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.background(MiuixTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("营养素", style = MiuixTheme.textStyles.footnote2, color = secondary)
                    Text("每 100 克", style = MiuixTheme.textStyles.footnote2, color = secondary)
                }
                LabelDivider()
                LabelNutrientRow("热量", "${per100g.caloriesKcal.formatInput()} kcal")
                LabelNutrientRow("蛋白质", "${per100g.proteinG.formatInput()} g")
                LabelNutrientRow("碳水化合物", "${per100g.carbsG.formatInput()} g")
                LabelNutrientRow("脂肪", "${per100g.fatG.formatInput()} g")
            }
            LabelDivider()
            TextField(
                value = weight,
                onValueChange = { input ->
                    weight = input
                    onWeightChange(dish.id, input.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0)
                },
                label = "食用重量",
                trailingIcon = {
                    Text("克", style = MiuixTheme.textStyles.body1, color = secondary, modifier = Modifier.padding(end = 16.dp))
                },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LabelNutrientRow(name: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, style = MiuixTheme.textStyles.body2)
        Text(value, style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LabelDivider() {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(MiuixTheme.colorScheme.dividerLine))
}
