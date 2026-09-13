package com.click.lightmemo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.lightmemo.FoodApp
import com.click.lightmemo.domain.NutritionReference
import com.click.lightmemo.network.FoodDataCentralClient
import com.click.lightmemo.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.click.lightmemo.ui.components.DropdownPref
import com.click.lightmemo.ui.utils.overScrollVertical
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val databaseSources = listOf("全部离线食物", "中国食物成分表", "USDA SR Legacy")

@Composable
fun FoodDatabaseScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember {
        (context.applicationContext as FoodApp).nutritionDatabase
    }
    var query by rememberSaveable { mutableStateOf("") }
    var sourceIndex by rememberSaveable { mutableIntStateOf(0) }
    var foods by remember { mutableStateOf<List<NutritionReference>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query, sourceIndex) {
        delay(180)
        loading = true
        error = null
        runCatching {
            database.browseOffline(
                query = query,
                source = when (sourceIndex) {
                    1 -> FoodDataCentralClient.DatabaseSource.CHINA
                    2 -> FoodDataCentralClient.DatabaseSource.USDA
                    else -> FoodDataCentralClient.DatabaseSource.ALL
                },
                limit = 120,
            )
        }.onSuccess { result -> foods = result }
            .onFailure { failure -> error = failure.message ?: "数据库读取失败" }
        loading = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("离线营养数据库", style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
                    Text(
                        "默认显示食物名称与热量，点击条目展开每 100g 的蛋白质、碳水和脂肪。",
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        label = "搜索食物",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownPref(
                        title = "数据源",
                        summary = "筛选本地数据库",
                        items = databaseSources,
                        selectedIndex = sourceIndex,
                        onSelectedIndexChange = { index -> sourceIndex = index },
                    )
                }
            }
        }
        item {
            Text(
                text = if (loading) "正在读取…" else "显示 ${foods.size} 条 · 点击查看营养素",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (loading && foods.isEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(size = 24.dp, strokeWidth = 2.dp)
                }
            }
        }
        error?.let { message ->
            item {
                Text(message, color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.subtitle)
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                insideMargin = PaddingValues(0.dp),
            ) {
                Column {
                    foods.forEach { reference ->
                        DatabaseFoodRow(reference)
                    }
                    if (!loading && foods.isEmpty() && error == null) {
                        Text(
                            "没有相近的食物，试试更短或更具体的名称。",
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DatabaseFoodRow(reference: NutritionReference) {
    var expanded by remember(reference.sourceId) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                reference.description,
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${reference.per100g.caloriesKcal.toInt()} kcal",
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                NutrientValue("蛋白质", reference.per100g.proteinG)
                NutrientValue("碳水", reference.per100g.carbsG)
                NutrientValue("脂肪", reference.per100g.fatG)
            }
            Text(
                "每 100g · ${reference.dataType} · ID ${reference.sourceId}",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NutrientValue(label: String, value: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text("${value.toInt()}g", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
