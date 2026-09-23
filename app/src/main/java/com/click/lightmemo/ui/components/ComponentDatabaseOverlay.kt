package com.click.lightmemo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.click.lightmemo.domain.NutritionReference
import com.click.lightmemo.viewmodel.NewComponentId
import com.click.lightmemo.viewmodel.DatabaseSearchState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared database picker used by both the recognition result and food-log editors. */
@Composable
fun ComponentDatabaseOverlay(
    searchState: DatabaseSearchState?,
    adding: Boolean,
    showAdd: Boolean = true,
    onDismiss: () -> Unit,
    onSearch: (String, String) -> Unit,
    onSelect: (String, String, String, NutritionReference) -> Unit,
    onEstimateNutrition: ((String, String, Double, (NutritionReference) -> Unit) -> Unit)? = null,
    onEstimateResolved: ((String, String, Double, NutritionReference) -> Unit)? = null,
    estimatingComponentId: String? = null,
) {
    var displayedState by remember { mutableStateOf<DatabaseSearchState?>(null) }
    LaunchedEffect(searchState) {
        if (searchState != null) displayedState = searchState
    }

    val current = displayedState ?: return
    val show = searchState != null && (!adding || showAdd)
    var query by remember(current.componentId) { mutableStateOf(current.query) }
    var grams by remember(current.componentId) { mutableStateOf("100") }

    AnimatedOverlayDialog(
        show = show,
        title = when {
            adding -> "添加食物成分"
            current.replaceComponentName -> "更换食物"
            else -> "匹配食物成分"
        },
        summary = when {
            adding -> "输入名称并从数据库选择营养数据"
            current.replaceComponentName -> "输入新的食物名称并选择正确的营养数据"
            else -> "选择正确的食物后会立即回填营养数据"
        },
        onDismissRequest = onDismiss,
        onDismissFinished = {
            displayedState = null
            onDismiss()
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = "搜索食物名称",
                singleLine = true,
                enabled = !current.loading,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
            if (adding) {
                TextField(
                    value = grams,
                    onValueChange = { grams = it },
                    label = "重量 g",
                    singleLine = true,
                    enabled = !current.loading,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            Button(
                onClick = { onSearch(current.componentId, query) },
                enabled = !current.loading && query.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(if (current.loading) "查询中…" else "查询数据库") }
            val canEstimate = onEstimateNutrition != null &&
                (current.allowAiEstimate || current.replaceComponentName ||
                    (adding && current.componentId == NewComponentId))
            if (canEstimate) {
                val estimateWeightG = if (adding) {
                    grams.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 100.0
                } else {
                    current.estimatedWeightG?.takeIf { it.isFinite() && it > 0.0 } ?: 100.0
                }
                Button(
                    onClick = {
                        onEstimateNutrition.invoke(
                            current.componentId,
                            query,
                            estimateWeightG,
                        ) { reference ->
                            onEstimateResolved?.invoke(
                                current.componentId,
                                query,
                                estimateWeightG,
                                reference,
                            )
                        }
                        onDismiss()
                    },
                    enabled = !current.loading &&
                        query.isNotBlank() && estimatingComponentId != current.componentId,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(
                        if (estimatingComponentId == current.componentId) {
                            "AI 估算中…"
                        } else {
                            "AI 估算营养（仅供参考）"
                        },
                    )
                }
            }
            if (current.loading) LinearProgressIndicator(progress = null, modifier = Modifier.fillMaxWidth())
            current.error?.let { message ->
                Text(message, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.error)
            }
            LazyColumn(
                modifier = Modifier.heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(current.results, key = { "${it.dataType}:${it.sourceId}" }) { reference ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        insideMargin = PaddingValues(12.dp),
                        onClick = { onSelect(current.componentId, query, grams, reference) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(reference.description, style = MiuixTheme.textStyles.body1)
                                Text(
                                    "每 100g · ${reference.dataType}",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${reference.per100g.caloriesKcal.toInt()} kcal",
                                style = MiuixTheme.textStyles.title4,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}
