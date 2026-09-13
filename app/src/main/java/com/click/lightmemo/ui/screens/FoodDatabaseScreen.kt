package com.click.lightmemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.click.lightmemo.FoodApp
import com.click.lightmemo.domain.NutritionReference
import com.click.lightmemo.network.FoodDataCentralClient
import com.click.lightmemo.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.click.lightmemo.ui.components.DropdownPref
import com.click.lightmemo.ui.utils.overScrollVertical
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.ArrowPreference

private val databaseSources = listOf("全部离线食物", "中国食物成分表", "USDA SR Legacy")

@Composable
fun FoodDatabaseScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
    searchVisible: Boolean = false,
    onSearchVisibleChange: (Boolean) -> Unit = {},
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
            .then(
                if (scrollBehavior != null) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                },
            ),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (searchVisible) {
            item {
                Spacer(Modifier.height(8.dp))
            }
            item {
                SearchBar(
                    modifier = Modifier.fillMaxWidth(),
                    inputField = {
                        InputField(
                            query = query,
                            onQueryChange = { query = it },
                            onSearch = {},
                            expanded = searchVisible,
                            onExpandedChange = onSearchVisibleChange,
                            label = "搜索食物",
                        )
                    },
                    expanded = searchVisible,
                    onExpandedChange = onSearchVisibleChange,
                    content = {},
                )
            }
        }

        item {
            SmallTitle(
                text = "筛选数据库",
                modifier = Modifier.offset(x = (-16).dp),
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                insideMargin = PaddingValues(0.dp),
            ) {
                DropdownPref(
                    title = "数据源",
                    summary = "选择要浏览的本地数据库",
                    items = databaseSources,
                    selectedIndex = sourceIndex,
                    onSelectedIndexChange = { sourceIndex = it },
                )
            }
        }

        item {
            Text(
                text = if (loading) "正在读取…" else "显示 ${foods.size} 条 · 点击条目查看营养素",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        if (loading && foods.isEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(size = 24.dp, strokeWidth = 2.dp)
                }
            }
        }

        error?.let { message ->
            item {
                Text(
                    text = message,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.subtitle,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        item {
            SmallTitle(
                text = "食物列表",
                modifier = Modifier.offset(x = (-16).dp),
            )
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
                            text = "没有相近的食物，试试更短或更具体的名称。",
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
    Column(modifier = Modifier.fillMaxWidth()) {
        ArrowPreference(
            title = reference.description,
            summary = "${reference.per100g.caloriesKcal.toInt()} kcal / 100g",
            onClick = { expanded = !expanded },
        )
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    NutrientValue("蛋白质", reference.per100g.proteinG)
                    NutrientValue("碳水", reference.per100g.carbsG)
                    NutrientValue("脂肪", reference.per100g.fatG)
                }
                Text(
                    text = "每 100g · ${reference.dataType} · ID ${reference.sourceId}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NutrientValue(label: String, value: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${value.toInt()}g",
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
