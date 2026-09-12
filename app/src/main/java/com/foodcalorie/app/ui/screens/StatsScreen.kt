package com.foodcalorie.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcalorie.app.domain.DayNutritionSummary
import com.foodcalorie.app.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.foodcalorie.app.ui.utils.overScrollVertical
import com.foodcalorie.app.viewmodel.StatsViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
) {
    val state by viewModel.uiState.collectAsState()
    val dayLabel = DateTimeFormatter.ofPattern("M/d")
    var selectedEpochDay by remember(state.rangeDays) {
        mutableLongStateOf(state.daily.lastOrNull()?.dateEpochDay ?: LocalDate.now().toEpochDay())
    }
    val selectedDay = state.daily.firstOrNull { it.dateEpochDay == selectedEpochDay }
        ?: state.daily.lastOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val selected = if (state.rangeDays == 7) 0 else 1
            TabRow(
                tabs = listOf("近 7 天", "近 30 天"),
                selectedTabIndex = selected,
                onTabSelected = { viewModel.setRange(if (it == 0) 7 else 30) },
            )
        }
        item { SummaryCard(state) }
        item {
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("饮食建议", style = MiuixTheme.textStyles.subtitle)
                    Text(recommendation(state), style = MiuixTheme.textStyles.body1)
                    Text(
                        "平均蛋白质 ${state.averageNutrition.proteinG.toInt()}g · 碳水 ${state.averageNutrition.carbsG.toInt()}g · 脂肪 ${state.averageNutrition.fatG.toInt()}g",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        item {
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column {
                    Text("每日热量", style = MiuixTheme.textStyles.subtitle)
                    Spacer(Modifier.height(10.dp))
                    if (!state.hasData) {
                        Text(
                            "暂无数据，记录几餐后这里会出现趋势",
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    } else {
                        DailyBarChart(
                            days = state.daily,
                            target = state.target,
                            maxKcal = maxOf(state.maxKcal, state.target.toDouble(), 1.0),
                            selectedEpochDay = selectedDay?.dateEpochDay,
                            dayLabel = dayLabel,
                            onSelect = { selectedEpochDay = it },
                        )
                        selectedDay?.let { DayDetailCard(it, state.target) }
                    }
                }
            }
        }
        item {
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("摄入日历", style = MiuixTheme.textStyles.subtitle)
                    Text(
                        "点击任意日期查看当天热量；横条达到右端表示达到目标",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    CalendarHeatmap(
                        days = state.daily,
                        target = state.target,
                        selectedEpochDay = selectedDay?.dateEpochDay,
                        onSelect = { selectedEpochDay = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: StatsViewModel.StatsUiState) {
    Card(
        cornerRadius = 20.dp,
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("记录概览", style = MiuixTheme.textStyles.subtitle)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${state.averageKcal.toInt()}", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("kcal / 记录日", style = MiuixTheme.textStyles.subtitle, modifier = Modifier.padding(bottom = 5.dp))
            }
            Text(
                "记录 ${state.loggedDays}/${state.rangeDays} 天 · 达标 ${state.daysHitTarget} 天 · 超标 ${state.daysOverTarget} 天",
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            LinearProgressIndicator(
                progress = (state.daysHitTarget.toFloat() / state.loggedDays.coerceAtLeast(1)).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
                colors = ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = MiuixTheme.colorScheme.primary,
                ),
                height = 8.dp,
            )
        }
    }
}

private fun recommendation(state: StatsViewModel.StatsUiState): String = when {
    !state.hasData -> "先记录 2–3 天，统计页会根据你的目标给出更具体的建议。"
    state.daysOverTarget > state.daysHitTarget -> "最近超出目标的天数偏多，可以先从减少高油、高糖饮品或零食开始。"
    state.daysHitTarget >= (state.loggedDays * 0.8f).toInt().coerceAtLeast(1) -> "节奏保持得不错，继续把每餐分散记录，避免把热量集中到晚餐。"
    state.averageKcal < state.target * 0.8 -> "平均摄入低于目标较多，注意补足正餐和蛋白质，避免长期摄入不足。"
    else -> "优先保证规律记录；当某天偏高时，用接下来一餐做温和调整即可。"
}

@Composable
private fun DailyBarChart(
    days: List<DayNutritionSummary>,
    target: Float,
    maxKcal: Double,
    selectedEpochDay: Long?,
    dayLabel: DateTimeFormatter,
    onSelect: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .height(158.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        days.forEach { day ->
            val ratio = (day.total.caloriesKcal / maxKcal).toFloat().coerceIn(0f, 1f)
            val selected = day.dateEpochDay == selectedEpochDay
            Column(
                modifier = Modifier
                    .width(if (days.size <= 7) 36.dp else 28.dp)
                    .height(158.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(day.dateEpochDay) }
                    .padding(horizontal = 3.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (selected) {
                    Text(
                        text = day.total.caloriesKcal.toInt().toString(),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                } else {
                    Spacer(Modifier.height(17.dp))
                }
                Box(
                    modifier = Modifier
                        .width(if (days.size <= 7) 24.dp else 18.dp)
                        .height((112f * ratio).dp.coerceAtLeast(if (day.total.caloriesKcal > 0) 3.dp else 0.dp))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(
                            if (selected) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.primary.copy(alpha = 0.55f),
                        ),
                )
                Text(
                    text = if (days.size <= 7) day.dateEpochDay.toLocalDate().format(dayLabel) else day.dateEpochDay.toLocalDate().dayOfMonth.toString(),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
    }
    Text(
        text = "目标 ${target.toInt()} kcal · 柱子越高表示当天摄入越多",
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun DayDetailCard(day: DayNutritionSummary, target: Float) {
    val calories = day.total.caloriesKcal
    val date = day.dateEpochDay.toLocalDate()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(date.format(DateTimeFormatter.ofPattern("M月d日")), style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.SemiBold)
            Text("${calories.toInt()} / ${target.toInt()} kcal", style = MiuixTheme.textStyles.body1)
        }
        LinearProgressIndicator(
            progress = (calories / target.coerceAtLeast(1f).toDouble()).toFloat().coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth(),
            height = 7.dp,
        )
        Text(
            if (calories <= 0.0) "当天没有记录" else if (calories <= target) "距离目标还差 ${(target - calories).toInt()} kcal" else "超过目标 ${(calories - target).toInt()} kcal",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun CalendarHeatmap(
    days: List<DayNutritionSummary>,
    target: Float,
    selectedEpochDay: Long?,
    onSelect: (Long) -> Unit,
) {
    val firstDate = days.firstOrNull()?.dateEpochDay?.toLocalDate() ?: LocalDate.now()
    val leading = firstDate.dayOfWeek.value % 7
    val cells: List<DayNutritionSummary?> = buildList {
        repeat(leading) { add(null) }
        addAll(days)
        while (size % 7 != 0) add(null)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("日", "一", "二", "三", "四", "五", "六").forEach { label ->
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    CalendarCell(day, target, day?.dateEpochDay == selectedEpochDay, onSelect, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CalendarCell(
    day: DayNutritionSummary?,
    target: Float,
    selected: Boolean,
    onSelect: (Long) -> Unit,
    modifier: Modifier,
) {
    val ratio = day?.let { (it.total.caloriesKcal / target.coerceAtLeast(1f).toDouble()).toFloat().coerceIn(0f, 1f) } ?: 0f
    Column(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.045f),
            )
            .then(if (day != null) Modifier.clickable { onSelect(day.dateEpochDay) } else Modifier)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = day?.dateEpochDay?.toLocalDate()?.dayOfMonth?.toString().orEmpty(),
            style = MiuixTheme.textStyles.footnote2,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
        )
        LinearProgressIndicator(
            progress = ratio,
            modifier = Modifier.fillMaxWidth(),
            height = 5.dp,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = if (ratio >= 1f) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.primary.copy(alpha = 0.7f),
            ),
        )
    }
}

private fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(this)
