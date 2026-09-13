package com.click.lightmemo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.click.lightmemo.domain.DayNutritionSummary
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.click.lightmemo.ui.theme.FoodPaletteColors
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.StatsViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DetailDateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.SIMPLIFIED_CHINESE)

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
    calendarExpanded: Boolean,
    palette: FoodPaletteColors = FoodPaletteColors.Default,
) {
    val state by viewModel.uiState.collectAsState()
    val readError by viewModel.readError.collectAsState()
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var anchorEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val anchor = LocalDate.ofEpochDay(anchorEpochDay)
    val selectedDay = state.daily.firstOrNull { it.dateEpochDay == selectedEpochDay }
    val periodStart = if (calendarExpanded) anchor.withDayOfMonth(1)
        else anchor.minusDays(anchor.dayOfWeek.value.toLong() - 1)
    val periodEnd = if (calendarExpanded) YearMonth.from(anchor).atEndOfMonth()
        else periodStart.plusDays(6)

    LaunchedEffect(calendarExpanded) {
        if (!calendarExpanded) anchorEpochDay = selectedEpochDay
    }
    LaunchedEffect(periodStart, periodEnd) {
        viewModel.setPeriod(periodStart, periodEnd)
    }
    fun movePeriod(direction: Int) {
        val next = if (calendarExpanded) anchor.plusMonths(direction.toLong())
            else anchor.plusWeeks(direction.toLong())
        anchorEpochDay = next.toEpochDay()
        selectedEpochDay = next.toEpochDay()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier),
        state = listState,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        readError?.let { message ->
            item {
                Text(
                    text = message,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item {
            // DateCalendar owns its 16dp side margins so week/month pages can pull from the screen edge.
            DateCalendar(
                days = state.calendarDays,
                target = state.target,
                selectedEpochDay = selectedDay?.dateEpochDay,
                expanded = calendarExpanded,
                anchor = anchor,
                onMove = ::movePeriod,
                onSelect = { selectedEpochDay = it },
                canMoveNext = if (calendarExpanded) YearMonth.from(anchor) < YearMonth.now()
                    else anchor.plusDays(7 - anchor.dayOfWeek.value.toLong()) < LocalDate.now(),
                title = { pageDate ->
                    Text(
                        text = pageDate.format(
                            if (calendarExpanded) DateTimeFormatter.ofPattern("yyyy年M月", Locale.SIMPLIFIED_CHINESE)
                            else DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
                        ),
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 10.dp),
                    )
                },
            )
        }
        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                SummaryCard(state, palette)
            }
        }
        item {
            MealStructureCard(
                mealCalories = state.mealCalories,
                averageCalories = state.averageKcal,
                structureColors = palette.structure,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        item { InsightsCard(state, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                NutritionIntakeCard(
                    nutrition = state.averageNutrition,
                    proteinTarget = state.proteinTarget,
                    carbsTarget = state.carbsTarget,
                    fatTarget = state.fatTarget,
                    proteinColor = palette.protein,
                    carbsColor = palette.carbs,
                    fatColor = palette.fat,
                )
            }
        }
        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                DailyCaloriesCard(
                    state = state,
                    selectedDay = selectedDay,
                    palette = palette,
                    onSelect = { selectedEpochDay = it },
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(state: StatsViewModel.StatsUiState, palette: FoodPaletteColors) {
    val targetRate = (state.daysHitTarget.toFloat() / state.loggedDays.coerceAtLeast(1)).coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.CornerRadius,
        insideMargin = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text("日均摄入", style = MiuixTheme.textStyles.headline1)
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "${state.averageKcal.toInt()}",
                style = MiuixTheme.textStyles.title1,
                modifier = Modifier.alignByBaseline(),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "kcal / 记录日",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "记录 ${state.loggedDays}/${state.rangeDays} 天 · 未超目标 ${state.daysHitTarget} 天 · 超标 ${state.daysOverTarget} 天",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = targetRate,
            modifier = Modifier.fillMaxWidth(),
            height = 7.dp,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = palette.calorie,
            ),
        )
    }
}

@Composable
private fun MealStructureCard(
    mealCalories: Map<MealType, Double>,
    averageCalories: Double,
    structureColors: List<Color>,
    modifier: Modifier,
) {
    // These are chart-series colors, not semantic status colors; each meal keeps a stable hue
    // so the same category remains recognizable when the theme or selected date changes.
    val mealColors = structureColors.take(4).ifEmpty {
        listOf(MiuixTheme.colorScheme.primary, MiuixTheme.colorScheme.secondary)
    }
    val slices = listOf(
        "早餐" to ((mealCalories[MealType.BREAKFAST] ?: 0.0) to mealColors[0]),
        "午餐" to ((mealCalories[MealType.LUNCH] ?: 0.0) to mealColors[1]),
        "晚餐" to ((mealCalories[MealType.DINNER] ?: 0.0) to mealColors.getOrElse(2) { mealColors.last() }),
        "加餐" to ((mealCalories[MealType.SNACK] ?: 0.0) to mealColors.getOrElse(3) { mealColors.last() }),
    )
    val total = slices.sumOf { it.second.first }
    Card(
        modifier = modifier,
        cornerRadius = CardDefaults.CornerRadius,
        insideMargin = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text("饮食结构", style = MiuixTheme.textStyles.headline1)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                NutritionDonut(
                    slices = slices,
                    total = total,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = averageCalories.toInt().toString(),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "kcal",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                slices.forEach { (label, valueAndColor) ->
                    val percentage = if (total <= 0.0) 0 else (valueAndColor.first / total * 100).toInt()
                    StructureLegend(label = label, percentage = percentage, color = valueAndColor.second)
                }
            }
        }
    }
}

@Composable
private fun NutritionDonut(
    slices: List<Pair<String, Pair<Double, Color>>>,
    total: Double,
    modifier: Modifier,
) {
    val emptyColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.12f)
    Canvas(modifier.padding(2.dp)) {
        val stroke = 11.dp.toPx()
        var startAngle = -90f
        if (total <= 0.0) {
            drawArc(
                color = emptyColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(stroke),
            )
        } else {
            slices.forEach { (_, valueAndColor) ->
                val sweep = (valueAndColor.first / total * 360.0).toFloat()
                drawArc(valueAndColor.second, startAngle, sweep, false, style = Stroke(stroke))
                startAngle += sweep
            }
        }
    }
}

@Composable
private fun StructureLegend(label: String, percentage: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(label, style = MiuixTheme.textStyles.body2, modifier = Modifier.weight(1f))
        Text(
            "$percentage%",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun InsightsCard(
    state: StatsViewModel.StatsUiState,
    modifier: Modifier,
) {
    val insights = buildInsights(state)
    Card(
        modifier = modifier,
        cornerRadius = CardDefaults.CornerRadius,
        insideMargin = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text("统计与建议", style = MiuixTheme.textStyles.headline1)
        Spacer(Modifier.height(16.dp))
        insights.forEachIndexed { index, insight ->
            InsightRow(insight)
            if (index < insights.lastIndex) Spacer(Modifier.height(16.dp))
        }
    }
}

private data class Insight(
    val title: String,
    val detail: String,
    val mark: String,
    val color: Color,
)

@Composable
private fun buildInsights(state: StatsViewModel.StatsUiState): List<Insight> {
    val positive = MiuixTheme.colorScheme.primary
    val caution = MiuixTheme.colorScheme.error
    val neutral = MiuixTheme.colorScheme.onSurfaceVariantActions
    if (!state.hasData) {
        return listOf(
            Insight("开始记录", "先记录一餐，逐步了解饮食结构和营养摄入。", "＋", positive),
        )
    }

    val insights = buildList {
        val missingDays = state.elapsedDays - state.loggedDays
        if (missingDays > 0) {
            add(Insight("补充记录", "还有 $missingDays 天未记录，日均值仅按已记录日期计算。", "!", neutral))
        }
        if (state.daysOverTarget > 0) {
            add(Insight("热量偏高", "有 ${state.daysOverTarget} 天超过目标，建议查看每日记录，留意高热量食物的份量。", "↑", caution))
        } else if (state.daysHitTarget == state.loggedDays) {
            add(Insight("热量记录", "已记录的 ${state.loggedDays} 天均未超过设定目标，可结合营养摄入一起查看。", "✓", positive))
        }

        val averageRatio = state.averageKcal / state.target.coerceAtLeast(1f)
        when {
            averageRatio > 1.1 -> add(Insight("日均摄入偏高", "日均 ${state.averageKcal.toInt()} kcal，高于目标 ${state.target.toInt()} kcal。", "↑", caution))
            averageRatio < 0.8 -> add(Insight("日均摄入偏低", "日均 ${state.averageKcal.toInt()} kcal，低于目标较多，请注意摄入充足。", "↓", neutral))
        }

        val proteinRatio = state.averageNutrition.proteinG / state.proteinTarget.coerceAtLeast(1f)
        if (proteinRatio < 0.75) {
            add(Insight("蛋白质偏低", "日均 ${state.averageNutrition.proteinG.toInt()}g，可增加蛋、奶、豆制品或瘦肉。", "↓", neutral))
        }
    }
    return insights.ifEmpty {
        listOf(Insight("保持节奏", "当前记录数据稳定，继续保持规律记录。", "✓", positive))
    }
}

@Composable
private fun InsightRow(insight: Insight) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(insight.color.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(insight.mark, style = MiuixTheme.textStyles.footnote1, color = insight.color, fontWeight = FontWeight.SemiBold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(insight.title, style = MiuixTheme.textStyles.headline2)
            Spacer(Modifier.height(4.dp))
            Text(insight.detail, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun NutritionIntakeCard(
    nutrition: Nutrition,
    proteinTarget: Float,
    carbsTarget: Float,
    fatTarget: Float,
    proteinColor: Color,
    carbsColor: Color,
    fatColor: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.CornerRadius,
        insideMargin = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text("营养摄入", style = MiuixTheme.textStyles.headline1)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MacroProgress("蛋白质", nutrition.proteinG, proteinTarget, proteinColor, Modifier.weight(1f))
            MacroProgress("碳水", nutrition.carbsG, carbsTarget, carbsColor, Modifier.weight(1f))
            MacroProgress("脂肪", nutrition.fatG, fatTarget, fatColor, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroProgress(
    label: String,
    value: Double,
    target: Float,
    color: Color,
    modifier: Modifier,
) {
    val safeTarget = target.coerceAtLeast(1f)
    val progress = (value / safeTarget).toFloat().coerceIn(0f, 1f)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = progress,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = color),
                size = 60.dp,
                strokeWidth = 6.dp,
            )
            Text("${(progress * 100).toInt()}%", style = MiuixTheme.textStyles.body1)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            "${value.formatMacro()} g",
            style = MiuixTheme.textStyles.body1,
        )
        Text(
            "目标 ${safeTarget.formatMacro()} g",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DailyCaloriesCard(
    state: StatsViewModel.StatsUiState,
    selectedDay: DayNutritionSummary?,
    palette: FoodPaletteColors,
    onSelect: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.CornerRadius,
        insideMargin = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("每日热量", style = MiuixTheme.textStyles.headline1, modifier = Modifier.weight(1f))
            Text(
                "目标 ${state.target.toInt()} kcal",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(8.dp))
        DailyCaloriesChart(
            days = state.daily,
            target = state.target,
            maxKcal = if (state.maxKcal > 0.0) state.maxKcal else state.target.toDouble().coerceAtLeast(1.0),
            selectedEpochDay = selectedDay?.dateEpochDay,
            calorieColor = palette.calorie,
            overTargetColor = palette.overTarget,
            onSelect = onSelect,
        )
        selectedDay?.let { day ->
            DayDetailSummary(
                day = day,
                target = state.target,
                calorieColor = if (day.total.caloriesKcal > state.target) palette.overTarget else palette.calorie,
            )
        }
    }
}

@Composable
private fun DailyCaloriesChart(
    days: List<DayNutritionSummary>,
    target: Float,
    maxKcal: Double,
    selectedEpochDay: Long?,
    calorieColor: Color,
    overTargetColor: Color,
    onSelect: (Long) -> Unit,
) {
    val scrollState = rememberScrollState()
    // Keep the target line visible and reserve 15% headroom above both data and target.
    val chartMax = maxOf(maxKcal, target.toDouble(), 1.0) * 1.15
    val plotHeight = 156.dp
    val labelStyle = MiuixTheme.textStyles.footnote2
    val labelHeight = with(LocalDensity.current) { labelStyle.fontSize.toDp() * 1.5f }
    val labelSpace = labelHeight + 4.dp
    val gridColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.06f)
    val targetColor = calorieColor.copy(alpha = 0.20f)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val minItemWidth = with(LocalDensity.current) { (labelStyle.fontSize.toDp() * 4.0f).coerceAtLeast(36.dp) }
        // Seven-day charts should fit the card so the last date/bar is not clipped.
        val itemWidth = if (days.size <= 7) {
            maxWidth / days.size.coerceAtLeast(1)
        } else {
            maxOf(minItemWidth, maxWidth / days.size.coerceAtLeast(1))
        }
        val chartWidth = itemWidth * days.size.coerceAtLeast(1)
        Box(
            modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.width(chartWidth)) {
                Box(Modifier.fillMaxWidth().height(plotHeight + labelSpace)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val bottom = size.height
                        val height = plotHeight.toPx()
                        // Interior guides and baseline only: no line at the chart ceiling.
                        repeat(3) { index ->
                            val y = bottom - height * index / 3f
                            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5.dp.toPx())
                        }
                        if (target > 0f) {
                            val targetY = bottom - height * (target / chartMax).toFloat().coerceIn(0f, 1f)
                            drawLine(
                                color = targetColor,
                                start = Offset(0f, targetY),
                                end = Offset(size.width, targetY),
                                strokeWidth = 0.75.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                            )
                        }
                    }
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                        days.forEach { day ->
                            val calories = day.total.caloriesKcal
                            val ratio = (calories / chartMax).toFloat().coerceIn(0f, 1f)
                            val selected = day.dateEpochDay == selectedEpochDay
                            val barColor = if (calories > target) overTargetColor else calorieColor
                            Box(
                                modifier = Modifier
                                    .width(itemWidth)
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = null,
                                        indication = null,
                                        role = Role.Button,
                                    ) { onSelect(day.dateEpochDay) },
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = calories.toInt().toString(),
                                        style = labelStyle,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        maxLines = 1,
                                        modifier = Modifier.height(labelHeight),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        Modifier
                                            .width(if (days.size <= 7) 18.dp else 14.dp)
                                            .height(plotHeight * ratio)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(barColor.copy(alpha = if (selected) 1f else 0.65f)),
                                    )
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    days.forEach { day ->
                        Text(
                            text = if (days.size <= 7) day.dateEpochDay.toLocalDate().format(DateTimeFormatter.ofPattern("M/d"))
                                else day.dateEpochDay.toLocalDate().dayOfMonth.toString(),
                            style = labelStyle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.width(itemWidth),
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun DayDetailSummary(day: DayNutritionSummary, target: Float, calorieColor: Color) {
    val calories = day.total.caloriesKcal
    val safeTarget = target.coerceAtLeast(1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(calorieColor.copy(alpha = 0.07f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(day.dateEpochDay.toLocalDate().format(DetailDateFormatter), style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.SemiBold)
            Text("${calories.toInt()} / ${target.toInt()} kcal", style = MiuixTheme.textStyles.footnote1)
        }
        LinearProgressIndicator(
            progress = (calories / safeTarget.toDouble()).toFloat().coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth(),
            height = 5.dp,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = calorieColor),
        )
    }
}

private fun Double.formatMacro(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(Locale.US, this)

private fun Float.formatMacro(): String =
    if (this % 1f == 0f) toInt().toString() else "%.1f".format(Locale.US, this)

private fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(this)
