package com.foodcalorie.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foodcalorie.app.domain.DayNutritionSummary
import com.foodcalorie.app.domain.MealType
import com.foodcalorie.app.domain.Nutrition
import com.foodcalorie.app.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.foodcalorie.app.ui.utils.overScrollVertical
import com.foodcalorie.app.viewmodel.StatsViewModel
import java.time.YearMonth
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DefaultProteinColor = Color(0xFFF3A17C)
private val DefaultCarbsColor = Color(0xFF2F7D2B)
private val DefaultFatColor = Color(0xFFFFB300)
private val DetailDateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.SIMPLIFIED_CHINESE)
private val WeekdayLabels = listOf("日", "一", "二", "三", "四", "五", "六")

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
    calendarExpanded: Boolean,
    proteinRingColor: Color = DefaultProteinColor,
    carbsRingColor: Color = DefaultCarbsColor,
    fatRingColor: Color = DefaultFatColor,
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
            start = 12.dp,
            end = 12.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        readError?.let { message ->
            item {
                Text(
                    text = message,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
        item {
            StatsCalendar(
                days = state.daily,
                target = state.target,
                selectedEpochDay = selectedDay?.dateEpochDay,
                expanded = calendarExpanded,
                anchor = anchor,
                onMove = ::movePeriod,
                onSelect = { selectedEpochDay = it },
            )
        }
        item { SummaryCard(state) }
        item {
            MealStructureCard(
                mealCalories = state.mealCalories,
                averageCalories = state.averageKcal,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { InsightsCard(state, Modifier.fillMaxWidth()) }
        item {
            NutritionIntakeCard(
                nutrition = state.averageNutrition,
                proteinTarget = state.proteinTarget,
                carbsTarget = state.carbsTarget,
                fatTarget = state.fatTarget,
                proteinColor = proteinRingColor,
                carbsColor = carbsRingColor,
                fatColor = fatRingColor,
            )
        }
        item {
            DailyCaloriesCard(
                state = state,
                selectedDay = selectedDay,
                onSelect = { selectedEpochDay = it },
            )
        }
    }
}

@Composable
private fun WeekDateSelector(
    days: List<DayNutritionSummary?>,
    target: Float,
    selectedEpochDay: Long?,
    onSelect: (Long) -> Unit,
    month: YearMonth? = null,
    expansion: Float = 0f,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        days.forEach { day ->
            val outsideMonth = day != null && month != null && YearMonth.from(day.dateEpochDay.toLocalDate()) != month
            DayProgressItem(
                day = day,
                target = target,
                selected = day?.dateEpochDay == selectedEpochDay,
                compact = false,
                onSelect = onSelect,
                modifier = Modifier.weight(1f).graphicsLayer {
                    alpha = if (outsideMonth) 1f - expansion else 1f
                }.then(if (outsideMonth && expansion == 1f) Modifier.clearAndSetSemantics { } else Modifier),
                enabled = !outsideMonth || expansion < 1f,
            )
        }
    }
}

@Composable
private fun StatsCalendar(
    days: List<DayNutritionSummary>,
    target: Float,
    selectedEpochDay: Long?,
    expanded: Boolean,
    anchor: LocalDate,
    onMove: (Int) -> Unit,
    onSelect: (Long) -> Unit,
) {
    val expansion by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(350),
        label = "calendarExpansion",
    )
    val summaries = remember(days) { days.associateBy { it.dateEpochDay } }
    val pageStart = if (expanded) anchor.withDayOfMonth(1)
        else anchor.minusDays(anchor.dayOfWeek.value.toLong() - 1)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = if (expanded) "${anchor.year}年${anchor.monthValue}月"
                else "${pageStart.monthValue}月${pageStart.dayOfMonth}日 — ${pageStart.plusDays(6).monthValue}月${pageStart.plusDays(6).dayOfMonth}日",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
        )
        val gestureModifier = Modifier.fillMaxWidth().clipToBounds()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(if (expanded) "上个月" else "上一周") { onMove(-1); true },
                    CustomAccessibilityAction(if (expanded) "下个月" else "下一周") { onMove(1); true },
                )
            }
            .pointerInput(expanded, anchor) {
                var distance = 0f
                detectHorizontalDragGestures(
                    onDragStart = { distance = 0f },
                    onDragCancel = { distance = 0f },
                    onDragEnd = {
                        if (kotlin.math.abs(distance) > 48.dp.toPx()) onMove(if (distance < 0) 1 else -1)
                    },
                ) { change, amount ->
                    change.consume()
                    distance += amount
                }
            }
        // Expansion is a measured layout transition so the following cards move with it.
        CalendarRows(
            modifier = gestureModifier,
            anchor = anchor,
            selectedEpochDay = selectedEpochDay,
            summaries = summaries,
            target = target,
            expansion = expansion,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun CalendarRows(
    modifier: Modifier,
    anchor: LocalDate,
    selectedEpochDay: Long?,
    summaries: Map<Long, DayNutritionSummary>,
    target: Float,
    expansion: Float,
    onSelect: (Long) -> Unit,
) {
    val calendar = remember(anchor) { StatsCalendarLayout(anchor) }
    val month = calendar.month
    val gridStart = calendar.gridStart
    val weekIndex = calendar.anchorRow
    val rowCount = calendar.rowCount
    Layout(
        modifier = modifier,
        content = {
            repeat(rowCount) { row ->
                val week = (0L..6L).map { day ->
                    val epoch = gridStart.plusDays(row * 7L + day).toEpochDay()
                    summaries[epoch] ?: DayNutritionSummary(epoch, Nutrition())
                }
                Box(Modifier.graphicsLayer { alpha = if (row < weekIndex) expansion else 1f }) {
                    WeekDateSelector(week, target, selectedEpochDay, onSelect, month, expansion)
                }
            }
        },
    ) { measurables, constraints ->
        val rows = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val rowHeight = rows.maxOf { it.height }
        val stride = rowHeight + 14.dp.roundToPx()
        val height = rowHeight + ((rowCount - 1) * stride * expansion).toInt()
        layout(constraints.maxWidth, height) {
            rows.forEachIndexed { index, row ->
                val y = (calendar.rowOffset(index, expansion) * stride).toInt()
                row.placeRelative(0, y)
            }
        }
    }
}

@Composable
private fun DayProgressItem(
    day: DayNutritionSummary?,
    target: Float,
    selected: Boolean,
    compact: Boolean,
    onSelect: (Long) -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
) {
    if (day == null) {
        Spacer(modifier.height(if (compact) 45.dp else 68.dp))
        return
    }

    val date = day.dateEpochDay.toLocalDate()
    val progress = (day.total.caloriesKcal / target.coerceAtLeast(1f).toDouble()).toFloat().coerceIn(0f, 1f)
    val surface = MiuixTheme.colorScheme.surfaceContainer
    val isToday = date == LocalDate.now()
    val selectable = enabled && date <= LocalDate.now()
    val contentColor = if (selected) MiuixTheme.colorScheme.onSurfaceContainer else MiuixTheme.colorScheme.onSurfaceVariantSummary

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(if (compact) 14.dp else 17.dp))
            .background(if (selected) surface else Color.Transparent)
            .clickable(
                role = Role.Button,
                enabled = selectable,
                onClick = { onSelect(day.dateEpochDay) },
            )
            .semantics {
                contentDescription = "${date.monthValue}月${date.dayOfMonth}日，已摄入${day.total.caloriesKcal.toInt()}千卡，目标完成${(progress * 100).toInt()}%"
            }
            .padding(vertical = if (compact) 4.dp else 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp),
    ) {
        Text(
            text = WeekdayLabels[date.dayOfWeek.value % 7],
            style = MiuixTheme.textStyles.footnote2,
            color = if (selected) contentColor else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.55f),
            fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
        Box(
            modifier = Modifier.size(if (compact) 31.dp else 42.dp),
            contentAlignment = Alignment.Center,
        ) {
            DayProgressRing(
                progress = progress,
                selected = selected,
                compact = compact,
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                text = date.dayOfMonth.toString(),
                style = if (compact) MiuixTheme.textStyles.footnote2 else MiuixTheme.textStyles.body1,
                color = if (selected) MiuixTheme.colorScheme.onSurfaceContainer else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = if (selectable) 0.7f else 0.3f),
                fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DayProgressRing(
    progress: Float,
    selected: Boolean,
    compact: Boolean,
    modifier: Modifier,
) {
    val trackColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = if (selected) 0.14f else 0.09f)
    val foregroundColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = if (selected) 0.45f else 0.22f)
    Canvas(modifier) {
        val stroke = (if (compact) 5.dp else 6.dp).toPx()
        val inset = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(trackColor, -90f, 360f, false, topLeft = inset, size = arcSize, style = Stroke(stroke))
        if (progress > 0f) {
            drawArc(foregroundColor, -90f, progress * 360f, false, topLeft = inset, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun SummaryCard(state: StatsViewModel.StatsUiState) {
    val targetRate = (state.daysHitTarget.toFloat() / state.loggedDays.coerceAtLeast(1)).coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.CornerRadius,
        insideMargin = PaddingValues(16.dp),
    ) {
        Text("日均摄入", style = MiuixTheme.textStyles.subtitle)
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "${state.averageKcal.toInt()}",
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "kcal / 记录日",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "记录 ${state.loggedDays}/${state.rangeDays} 天 · 未超目标 ${state.daysHitTarget} 天 · 超标 ${state.daysOverTarget} 天",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = targetRate,
            modifier = Modifier.fillMaxWidth(),
            height = 7.dp,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = MiuixTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun MealStructureCard(
    mealCalories: Map<MealType, Double>,
    averageCalories: Double,
    modifier: Modifier,
) {
    // These are chart-series colors, not semantic status colors; each meal keeps a stable hue
    // so the same category remains recognizable when the theme or selected date changes.
    val mealColors = listOf(
        Color(0xFFFFC857),
        Color(0xFF62C7A5),
        Color(0xFF6CA7F2),
        Color(0xFFB49AE8),
    )
    val slices = listOf(
        "早餐" to ((mealCalories[MealType.BREAKFAST] ?: 0.0) to mealColors[0]),
        "午餐" to ((mealCalories[MealType.LUNCH] ?: 0.0) to mealColors[1]),
        "晚餐" to ((mealCalories[MealType.DINNER] ?: 0.0) to mealColors[2]),
        "加餐" to ((mealCalories[MealType.SNACK] ?: 0.0) to mealColors[3]),
    )
    val total = slices.sumOf { it.second.first }
    Card(
        modifier = modifier,
        cornerRadius = CardDefaults.CornerRadius,
        insideMargin = PaddingValues(16.dp),
    ) {
        Text("饮食结构", style = MiuixTheme.textStyles.subtitle)
        Spacer(Modifier.height(7.dp))
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
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(label, style = MiuixTheme.textStyles.footnote2, maxLines = 1)
        Text(
            "$percentage%",
            style = MiuixTheme.textStyles.footnote2,
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
        insideMargin = PaddingValues(16.dp),
    ) {
        Text("统计与建议", style = MiuixTheme.textStyles.subtitle)
        Spacer(Modifier.height(7.dp))
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
            Text(insight.mark, style = MiuixTheme.textStyles.footnote2, color = insight.color, fontWeight = FontWeight.SemiBold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(insight.title, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(insight.detail, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
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
        insideMargin = PaddingValues(16.dp),
    ) {
        Text("营养摄入", style = MiuixTheme.textStyles.subtitle)
        Spacer(Modifier.height(10.dp))
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
            Text(label, style = MiuixTheme.textStyles.footnote2, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${value.formatMacro()} / ${safeTarget.formatMacro()}g",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${(progress * 100).toInt()}%",
            style = MiuixTheme.textStyles.footnote2,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DailyCaloriesCard(
    state: StatsViewModel.StatsUiState,
    selectedDay: DayNutritionSummary?,
    onSelect: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.CornerRadius,
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("每日热量", style = MiuixTheme.textStyles.subtitle)
            Text(
                "目标 ${state.target.toInt()} kcal",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(8.dp))
        DailyCaloriesChart(
            days = state.daily,
            target = state.target,
            maxKcal = maxOf(state.maxKcal, state.target.toDouble(), 1.0),
            selectedEpochDay = selectedDay?.dateEpochDay,
            onSelect = onSelect,
        )
        selectedDay?.let { day ->
            DayDetailSummary(day = day, target = state.target)
        }
    }
}

@Composable
private fun DailyCaloriesChart(
    days: List<DayNutritionSummary>,
    target: Float,
    maxKcal: Double,
    selectedEpochDay: Long?,
    onSelect: (Long) -> Unit,
) {
    val scrollState = rememberScrollState()
    val itemWidth = if (days.size <= 7) 42.dp else 30.dp
    val chartWidth = itemWidth * days.size + 8.dp
    val plotTop = 19.dp
    val plotBottom = 25.dp
    val plotHeight = 126.dp
    val gridColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.12f)
    val targetColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.42f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
    ) {
        Box(
            modifier = Modifier
                .width(chartWidth)
                .height(170.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val top = plotTop.toPx()
                val bottom = size.height - plotBottom.toPx()
                val height = bottom - top
                repeat(4) { index ->
                    val y = top + height * index / 3f
                    drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                val targetY = bottom - height * (target / maxKcal.toFloat()).coerceIn(0f, 1f)
                drawLine(
                    color = targetColor,
                    start = androidx.compose.ui.geometry.Offset(0f, targetY),
                    end = androidx.compose.ui.geometry.Offset(size.width, targetY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                )
            }
            Text(
                text = maxKcal.toInt().toString(),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 2.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { day ->
                    val ratio = (day.total.caloriesKcal / maxKcal).toFloat().coerceIn(0f, 1f)
                    val selected = day.dateEpochDay == selectedEpochDay
                    Column(
                        modifier = Modifier
                            .width(itemWidth)
                            .fillMaxSize()
                            .clickable(role = Role.Button) { onSelect(day.dateEpochDay) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(plotTop),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            if (selected && day.total.caloriesKcal > 0.0) {
                                Text(
                                    text = day.total.caloriesKcal.toInt().toString(),
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.primary,
                                    maxLines = 1,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(if (days.size <= 7) 22.dp else 16.dp)
                                    .height((plotHeight.value * ratio).dp.coerceAtLeast(if (ratio > 0f) 3.dp else 0.dp))
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.primary.copy(alpha = 0.46f)),
                            )
                        }
                        Text(
                            text = if (days.size <= 7) day.dateEpochDay.toLocalDate().format(DateTimeFormatter.ofPattern("M/d")) else day.dateEpochDay.toLocalDate().dayOfMonth.toString(),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
    Text(
        text = "点击柱子查看当天记录",
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun DayDetailSummary(day: DayNutritionSummary, target: Float) {
    val calories = day.total.caloriesKcal
    val safeTarget = target.coerceAtLeast(1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.07f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(day.dateEpochDay.toLocalDate().format(DetailDateFormatter), style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.SemiBold)
            Text("${calories.toInt()} / ${target.toInt()} kcal", style = MiuixTheme.textStyles.footnote2)
        }
        LinearProgressIndicator(
            progress = (calories / safeTarget.toDouble()).toFloat().coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth(),
            height = 5.dp,
        )
    }
}

private fun Double.formatMacro(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(Locale.US, this)

private fun Float.formatMacro(): String =
    if (this % 1f == 0f) toInt().toString() else "%.1f".format(Locale.US, this)

private fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(this)
