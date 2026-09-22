package com.click.lightmemo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.click.lightmemo.domain.DayNutritionSummary
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.click.lightmemo.ui.theme.FoodPaletteColors
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.MealTimingDay
import com.click.lightmemo.viewmodel.StatsViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.os4.Add
import top.yukonga.miuix.kmp.icon.os4.Back
import top.yukonga.miuix.kmp.icon.os4.Info
import top.yukonga.miuix.kmp.icon.os4.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DetailDateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.SIMPLIFIED_CHINESE)

private enum class DailyHeatmapMode(val title: String) {
    CALORIES("热量"),
    PROTEIN("蛋白质"),
    CARBS("碳水"),
    FAT("脂肪");

    fun next(): DailyHeatmapMode = entries[(ordinal + 1) % entries.size]
}

private fun monthHeatmapDays(
    days: List<DayNutritionSummary>,
    anchor: LocalDate,
): List<DayNutritionSummary> {
    val month = YearMonth.from(anchor)
    val summaries = days.associateBy { it.dateEpochDay }
    return (1..month.lengthOfMonth()).map { dayOfMonth ->
        val epochDay = month.atDay(dayOfMonth).toEpochDay()
        summaries[epochDay] ?: DayNutritionSummary(epochDay, Nutrition())
    }
}

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
    var dailyHeatmapMode by rememberSaveable { mutableStateOf(DailyHeatmapMode.CALORIES) }
    val anchor = LocalDate.ofEpochDay(anchorEpochDay)
    val selectedDay = state.daily.firstOrNull { it.dateEpochDay == selectedEpochDay }
    val monthlyHeatmapDays = monthHeatmapDays(state.calendarDays, anchor)
    val selectedHeatmapDay = monthlyHeatmapDays.firstOrNull { it.dateEpochDay == selectedEpochDay }
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
                SummaryCard(
                    state = state,
                    selectedDay = selectedHeatmapDay,
                    heatmapDays = monthlyHeatmapDays,
                    palette = palette,
                    mode = dailyHeatmapMode,
                    onModeChange = { dailyHeatmapMode = it },
                    onSelect = { selectedEpochDay = it },
                )
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
        item {
            MealTimingCard(
                state = state,
                structureColors = palette.structure,
                selectedEpochDay = selectedDay?.dateEpochDay,
                onSelect = { selectedEpochDay = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        item { AdviceSection(state, Modifier.fillMaxWidth()) }
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
    }
}

@Composable
private fun SummaryCard(
    state: StatsViewModel.StatsUiState,
    selectedDay: DayNutritionSummary?,
    heatmapDays: List<DayNutritionSummary>,
    palette: FoodPaletteColors,
    mode: DailyHeatmapMode,
    onModeChange: (DailyHeatmapMode) -> Unit,
    onSelect: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.CornerRadius,
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DailyMetricTitle(mode, onModeChange)
                val average = dailyAverageValue(mode, state)
                val unit = if (mode == DailyHeatmapMode.CALORIES) "kcal" else "g"
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        average.formatMacro(),
                        style = MiuixTheme.textStyles.title1,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "$unit / 记录日",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                Text(
                    "记录 ${state.loggedDays}/${state.rangeDays} 天 · 未超目标 ${state.daysHitTarget} 天 · 超标 ${state.daysOverTarget} 天",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Column(
                modifier = Modifier.padding(top = 9.dp),
                horizontalAlignment = Alignment.End,
            ) {
                DailyHeatmap(
                    days = heatmapDays,
                    mode = mode,
                    calorieTarget = state.target,
                    proteinTarget = state.proteinTarget,
                    carbsTarget = state.carbsTarget,
                    fatTarget = state.fatTarget,
                    selectedEpochDay = selectedDay?.dateEpochDay,
                    calorieColor = palette.calorie,
                    proteinColor = palette.protein,
                    carbsColor = palette.carbs,
                    fatColor = palette.fat,
                    onSelect = onSelect,
                    modifier = Modifier.width(88.dp),
                )
            }
        }
        selectedDay?.let { day ->
            DayMetricDetailSummary(
                day = day,
                mode = mode,
                target = dailyHeatmapTarget(mode, state),
                color = dailyHeatmapColor(mode, palette),
            )
        }
    }
}

@Composable
private fun DailyMetricTitle(mode: DailyHeatmapMode, onModeChange: (DailyHeatmapMode) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val textColor = MiuixTheme.colorScheme.onSurface.copy(alpha = if (pressed) 0.45f else 1f)
    Row(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = { onModeChange(mode.next()) },
            )
            .semantics {
                contentDescription = "日均摄入，当前为${mode.title}，点击切换到${mode.next().title}"
            }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("日均摄入", style = MiuixTheme.textStyles.headline1, color = textColor)
        Text(" · ${mode.title}", style = MiuixTheme.textStyles.headline1, color = textColor)
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
            Spacer(Modifier.width(74.dp))
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
private fun MealTimingCard(
    state: StatsViewModel.StatsUiState,
    structureColors: List<Color>,
    selectedEpochDay: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier,
) {
    val mealColors = structureColors.take(4).ifEmpty {
        listOf(MiuixTheme.colorScheme.primary, MiuixTheme.colorScheme.secondary)
    }
    val selectedTiming = state.mealTiming.firstOrNull { it.dateEpochDay == selectedEpochDay }
    val hasTiming = state.mealTiming.any { it.points.isNotEmpty() }

    Card(
        modifier = modifier,
        cornerRadius = CardDefaults.CornerRadius,
        insideMargin = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text("饮食规律", style = MiuixTheme.textStyles.headline1)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimingMetric(
                label = "规律度",
                value = state.regularityScore?.let { "$it%" } ?: "—",
                detail = if (state.timedDays >= 2) "时间稳定性" else "至少记录 2 天",
                modifier = Modifier.weight(1f),
            )
            TimingMetric(
                label = "日均摄入",
                value = if (state.averageIntakesPerDay > 0.0) {
                    state.averageIntakesPerDay.formatCount()
                } else "—",
                detail = "次 / 有时间记录日",
                modifier = Modifier.weight(1f),
            )
            TimingMetric(
                label = "进食窗口",
                value = state.averageEatingWindowMinutes?.formatDuration() ?: "—",
                detail = if (state.averageFirstMealMinute != null && state.averageLastMealMinute != null) {
                    "${state.averageFirstMealMinute.formatMinuteOfDay()}–${state.averageLastMealMinute.formatMinuteOfDay()}"
                } else "首餐至末餐",
                modifier = Modifier.weight(1f),
            )
        }
        if (!hasTiming) {
            Spacer(Modifier.height(18.dp))
            Text(
                if (state.hasData) "已有食物记录，但没有可用的用餐时间。编辑记录补充时间后，这里会显示规律性。"
                else "记录用餐时间后，这里会显示每天的摄入时间段。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        } else {
            Spacer(Modifier.height(18.dp))
            MealTimingChart(
                days = state.mealTiming,
                structureColors = mealColors,
                selectedEpochDay = selectedEpochDay,
                onSelect = onSelect,
            )
            selectedTiming?.takeIf { it.points.isNotEmpty() }?.let { day ->
                MealTimingDayDetail(day)
            }
        }
    }
}

@Composable
private fun TimingMetric(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(3.dp))
        Text(value, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
        Text(
            detail,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 2,
        )
    }
}

@Composable
private fun MealTimingChart(
    days: List<MealTimingDay>,
    structureColors: List<Color>,
    selectedEpochDay: Long?,
    onSelect: (Long) -> Unit,
) {
    val scrollState = rememberScrollState()
    val plotHeight = 99.dp
    val labelStyle = MiuixTheme.textStyles.footnote2
    val labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val chartViewportWidth = maxWidth.coerceAtLeast(1.dp)
        val itemWidth = if (days.size <= 7) {
            chartViewportWidth / days.size.coerceAtLeast(1)
        } else {
            maxOf(42.dp, chartViewportWidth / days.size.coerceAtLeast(1))
        }
        val chartWidth = itemWidth * days.size.coerceAtLeast(1)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        ) {
            Column(Modifier.width(chartWidth)) {
                Box(Modifier.fillMaxWidth().height(plotHeight)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val itemWidthPx = itemWidth.toPx()
                        days.forEachIndexed { index, day ->
                            if (day.points.isEmpty()) return@forEachIndexed
                            val x = itemWidthPx * (index + 0.5f)
                            fun yFor(minuteOfDay: Int): Float =
                                size.height * (minuteOfDay / 1440f).coerceIn(0f, 1f)
                            val firstY = yFor(day.firstMinute ?: 0)
                            val lastY = yFor(day.lastMinute ?: 0)
                            val selected = day.dateEpochDay == selectedEpochDay
                            val barStartY = minOf(firstY, lastY)
                            val barEndY = maxOf(lastY, barStartY + 1f)
                            val barAlpha = if (selected) 0.92f else 0.48f
                            val recordedColors = day.points
                                .sortedBy { it.minuteOfDay }
                                .map { point -> mealChartColor(point.mealType, structureColors) }
                            val gradientColors = if (recordedColors.size == 1) {
                                recordedColors + recordedColors
                            } else {
                                recordedColors
                            }
                            drawLine(
                                brush = Brush.verticalGradient(
                                    colors = gradientColors.map { it.copy(alpha = barAlpha) },
                                    startY = barStartY,
                                    endY = barEndY,
                                ),
                                start = Offset(x, firstY),
                                end = Offset(x, lastY),
                                strokeWidth = 6.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                            day.points.forEach { point ->
                                drawCircle(
                                    color = mealChartColor(point.mealType, structureColors)
                                        .copy(alpha = if (selected) 1f else 0.86f),
                                    radius = 3.dp.toPx(),
                                    center = Offset(x, yFor(point.minuteOfDay)),
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxSize()) {
                        days.forEach { day ->
                            Box(
                                modifier = Modifier
                                    .width(itemWidth)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = null,
                                        indication = null,
                                        role = Role.Button,
                                    ) { onSelect(day.dateEpochDay) }
                                    .semantics {
                                        contentDescription = timingDescription(day)
                                    },
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    days.forEach { day ->
                        Text(
                            text = if (days.size <= 7) {
                                day.dateEpochDay.toLocalDate().format(DateTimeFormatter.ofPattern("M/d"))
                            } else {
                                day.dateEpochDay.toLocalDate().dayOfMonth.toString()
                            },
                            style = labelStyle,
                            color = labelColor,
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
private fun MealTimingDayDetail(day: MealTimingDay) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.07f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                day.dateEpochDay.toLocalDate().format(DetailDateFormatter),
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.SemiBold,
            )
            Text("${day.points.size} 次摄入", style = MiuixTheme.textStyles.footnote1)
        }
        Text(
            day.points.joinToString(" · ") { "${it.minuteOfDay.formatMinuteOfDay()} ${it.mealType.label}" },
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private fun mealChartColor(mealType: MealType, colors: List<Color>): Color =
    colors.getOrElse(mealType.ordinal) { colors.lastOrNull() ?: Color.Gray }

private fun timingDescription(day: MealTimingDay): String = if (day.points.isEmpty()) {
    "${day.dateEpochDay.toLocalDate().format(DetailDateFormatter)}，没有时间记录"
} else {
    "${day.dateEpochDay.toLocalDate().format(DetailDateFormatter)}，${day.points.size} 次摄入，${day.firstMinute?.formatMinuteOfDay()} 至 ${day.lastMinute?.formatMinuteOfDay()}"
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
        Text(
            label,
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.weight(1f),
        )
        Text(
            "$percentage%",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
internal fun AdviceSection(
    state: StatsViewModel.StatsUiState,
    modifier: Modifier,
) {
    val advice = buildAdvice(state)
    BoxWithConstraints(
        modifier = modifier,
    ) {
        val contentWidth = (maxWidth - 32.dp).coerceAtLeast(0.dp)
        val cardWidth = (contentWidth * 0.45f).coerceAtLeast(160.dp)
        val cardEdgeSpacer = 6.dp
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
            .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.width(cardEdgeSpacer))
            advice.forEach { item ->
                AdviceCard(item, Modifier.width(cardWidth).fillMaxHeight())
            }
            Spacer(Modifier.width(cardEdgeSpacer))
        }
    }
}

private enum class AdviceGlyph {
    ADD,
    UP,
    DOWN,
    ALERT,
    CHECK,
    DOT,
}

private data class Advice(
    val title: String,
    val detail: String,
    val glyph: AdviceGlyph,
    val color: Color,
    val priority: Int,
)

@Composable
private fun buildAdvice(state: StatsViewModel.StatsUiState): List<Advice> {
    val positive = MiuixTheme.colorScheme.primary
    val caution = MiuixTheme.colorScheme.error
    val neutral = MiuixTheme.colorScheme.onSurfaceVariantActions
    if (!state.hasData) {
        return listOf(Advice("开始记录", "先记录一餐，逐步了解饮食结构和营养摄入。", AdviceGlyph.ADD, positive, 0))
    }

    val advice = buildList {
        val missingDays = state.elapsedDays - state.loggedDays
        if (missingDays > 0) {
            add(Advice("补充记录", "还有 $missingDays 天未记录，日均值仅按已记录日期计算。", AdviceGlyph.ALERT, neutral, 1))
        }
        if (state.daysOverTarget > 0) {
            add(Advice("热量偏高", "有 ${state.daysOverTarget} 天超过目标，建议查看每日记录，留意高热量食物的份量。", AdviceGlyph.UP, caution, 0))
        } else if (state.daysHitTarget == state.loggedDays) {
            add(Advice("热量记录稳定", "已记录的 ${state.loggedDays} 天均未超过设定目标，可继续保持当前节奏。", AdviceGlyph.CHECK, positive, 3))
        }

        val averageRatio = state.averageKcal / state.target.coerceAtLeast(1f)
        when {
            averageRatio > 1.1 -> add(Advice("日均摄入偏高", "日均 ${state.averageKcal.toInt()} kcal，高于目标 ${state.target.toInt()} kcal。", AdviceGlyph.UP, caution, 0))
            averageRatio < 0.8 -> add(Advice("日均摄入偏低", "日均 ${state.averageKcal.toInt()} kcal，低于目标较多，请注意摄入充足。", AdviceGlyph.DOWN, neutral, 1))
        }

        val nutrition = state.averageNutrition
        val proteinRatio = nutrition.proteinG / state.proteinTarget.coerceAtLeast(1f)
        val carbsRatio = nutrition.carbsG / state.carbsTarget.coerceAtLeast(1f)
        val fatRatio = nutrition.fatG / state.fatTarget.coerceAtLeast(1f)
        when {
            proteinRatio < 0.75 -> add(Advice("蛋白质不足", "日均约 ${nutrition.proteinG.toInt()}g，可增加蛋、奶、豆制品、鱼虾或瘦肉。", AdviceGlyph.DOWN, caution, 0))
            proteinRatio > 1.2 -> add(Advice("蛋白质偏高", "日均约 ${nutrition.proteinG.toInt()}g，已经超过目标较多，注意整体热量平衡。", AdviceGlyph.UP, neutral, 1))
        }
        when {
            carbsRatio < 0.75 -> add(Advice("碳水不足", "日均约 ${nutrition.carbsG.toInt()}g，可适量补充米饭、燕麦、玉米或薯类。", AdviceGlyph.DOWN, neutral, 1))
            carbsRatio > 1.2 -> add(Advice("碳水偏高", "日均约 ${nutrition.carbsG.toInt()}g，可减少精制主食和含糖食物。", AdviceGlyph.UP, caution, 0))
        }
        when {
            fatRatio < 0.75 -> add(Advice("脂肪不足", "日均约 ${nutrition.fatG.toInt()}g，可适量增加鱼类、坚果或牛油果。", AdviceGlyph.DOWN, neutral, 1))
            fatRatio > 1.2 -> add(Advice("脂肪超量", "日均约 ${nutrition.fatG.toInt()}g，建议减少油炸、肥肉、奶油和高油烹饪。", AdviceGlyph.UP, caution, 0))
        }

        val structureTotal = state.mealCalories.values.sum()
        if (structureTotal > 0.0) {
            val breakfastRatio = (state.mealCalories[MealType.BREAKFAST] ?: 0.0) / structureTotal
            val dinnerRatio = (state.mealCalories[MealType.DINNER] ?: 0.0) / structureTotal
            if (breakfastRatio < 0.2) {
                add(Advice("早餐偏少", "早餐约占总摄入 ${(breakfastRatio * 100).toInt()}%，建议把部分摄入提前到上午。", AdviceGlyph.DOT, neutral, 1))
            }
            if (dinnerRatio > 0.45) {
                add(Advice("晚餐偏重", "晚餐约占总摄入 ${(dinnerRatio * 100).toInt()}%，建议将部分热量分配到早餐和午餐。", AdviceGlyph.UP, caution, 0))
            }
        }

        if (state.timedDays == 0) {
            add(Advice("补充用餐时间", "记录用餐时间后，才能判断进食窗口和饮食规律。", AdviceGlyph.ALERT, neutral, 1))
        } else {
            state.regularityScore?.takeIf { it < 60 }?.let { score ->
                add(Advice("饮食不规律", "当前规律度 $score%，首餐、末餐或进食间隔波动较大。", AdviceGlyph.ALERT, caution, 0))
            }
            state.averageFirstMealMinute?.takeIf { it >= 10 * 60 }?.let {
                add(Advice("首餐偏晚", "平均首餐时间为 ${it.formatMinuteOfDay()}，建议尽量保持稳定并适当提前。", AdviceGlyph.DOT, neutral, 1))
            }
            state.averageLastMealMinute?.takeIf { it >= 21 * 60 }?.let {
                add(Advice("晚餐偏晚", "平均末餐时间为 ${it.formatMinuteOfDay()}，建议避免太晚进食。", AdviceGlyph.DOT, neutral, 1))
            }
            state.averageEatingWindowMinutes?.takeIf { it >= 14 * 60 }?.let {
                add(Advice("进食窗口偏长", "一天的进食时间跨度约 ${it.formatDuration()}，建议减少无计划加餐。", AdviceGlyph.DOT, neutral, 1))
            }
        }

        val proteinGap = (state.proteinTarget - nutrition.proteinG).coerceAtLeast(0.0)
        val carbsGap = (state.carbsTarget - nutrition.carbsG).coerceAtLeast(0.0)
        val fatGap = (state.fatTarget - nutrition.fatG).coerceAtLeast(0.0)
        when {
            proteinGap >= 20.0 -> add(Advice("补充蛋白质", "当前平均还缺约 ${proteinGap.toInt()}g，优先选择鸡蛋、牛奶、豆腐、鱼虾或瘦肉。", AdviceGlyph.ADD, positive, 2))
            carbsGap >= 30.0 -> add(Advice("补充主食", "当前平均还缺约 ${carbsGap.toInt()}g碳水，可选择燕麦、玉米、红薯或全谷物。", AdviceGlyph.ADD, positive, 2))
            fatGap >= 10.0 -> add(Advice("补充健康脂肪", "当前平均还缺约 ${fatGap.toInt()}g脂肪，可选择少量坚果、牛油果或鱼类。", AdviceGlyph.ADD, positive, 2))
        }
    }

    return advice
        .ifEmpty { listOf(Advice("保持节奏", "当前记录数据稳定，继续保持规律记录。", AdviceGlyph.CHECK, positive, 3)) }
        .sortedBy { it.priority }
        .take(8)
}

@Composable
private fun AdviceCard(advice: Advice, modifier: Modifier) {
    Card(
        modifier = modifier,
        cornerRadius = CardDefaults.CornerRadius,
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        when (advice.priority) {
                            0 -> "优先处理"
                            1 -> "注意"
                            2 -> "可执行"
                            else -> "保持"
                        },
                        style = MiuixTheme.textStyles.footnote2,
                        color = advice.color,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        advice.title,
                        style = MiuixTheme.textStyles.headline2,
                        maxLines = 2,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(advice.color.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AdviceGlyphIcon(advice.glyph, advice.color)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                advice.detail,
                modifier = Modifier.fillMaxWidth(),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 4,
            )
        }
    }
}

@Composable
private fun AdviceGlyphIcon(glyph: AdviceGlyph, color: Color) {
    val rotation = when (glyph) {
        AdviceGlyph.UP -> 90f
        AdviceGlyph.DOWN -> -90f
        else -> 0f
    }
    Icon(
        imageVector = when (glyph) {
            AdviceGlyph.ADD -> MiuixIcons.Os4.Add
            AdviceGlyph.UP, AdviceGlyph.DOWN -> MiuixIcons.Os4.Back
            AdviceGlyph.ALERT -> MiuixIcons.Os4.Info
            AdviceGlyph.CHECK -> MiuixIcons.Os4.Ok
            AdviceGlyph.DOT -> MiuixIcons.Os4.Info
        },
        contentDescription = null,
        modifier = Modifier.size(18.dp).rotate(rotation),
        tint = color,
    )
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
private fun DailyHeatmap(
    days: List<DayNutritionSummary>,
    mode: DailyHeatmapMode,
    calorieTarget: Float,
    proteinTarget: Float,
    carbsTarget: Float,
    fatTarget: Float,
    selectedEpochDay: Long?,
    calorieColor: Color,
    proteinColor: Color,
    carbsColor: Color,
    fatColor: Color,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return

    val dayMap = days.associateBy { it.dateEpochDay }
    val first = days.minOf { it.dateEpochDay }.toLocalDate()
    val last = days.maxOf { it.dateEpochDay }.toLocalDate()
    val gridStart = first.minusDays(first.dayOfWeek.value.toLong() - 1)
    val gridEnd = last.plusDays(7L - last.dayOfWeek.value)
    val rowCount = ((gridEnd.toEpochDay() - gridStart.toEpochDay()) / 7L + 1L).toInt()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(rowCount) { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(7) { column ->
                    val epochDay = gridStart.plusDays((row * 7 + column).toLong()).toEpochDay()
                    val day = dayMap[epochDay]
                    HeatmapCell(
                        day = day,
                        mode = mode,
                        calorieTarget = calorieTarget,
                        proteinTarget = proteinTarget,
                        carbsTarget = carbsTarget,
                        fatTarget = fatTarget,
                        selected = day?.dateEpochDay == selectedEpochDay,
                        calorieColor = calorieColor,
                        proteinColor = proteinColor,
                        carbsColor = carbsColor,
                        fatColor = fatColor,
                        onSelect = onSelect,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    day: DayNutritionSummary?,
    mode: DailyHeatmapMode,
    calorieTarget: Float,
    proteinTarget: Float,
    carbsTarget: Float,
    fatTarget: Float,
    selected: Boolean,
    calorieColor: Color,
    proteinColor: Color,
    carbsColor: Color,
    fatColor: Color,
    onSelect: (Long) -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(3.dp)
    val date = day?.dateEpochDay?.toLocalDate()
    val isFuture = date != null && date.isAfter(LocalDate.now())
    val emptyColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.08f)
    val selectable = day != null && !isFuture
    val target = when (mode) {
        DailyHeatmapMode.CALORIES -> calorieTarget
        DailyHeatmapMode.PROTEIN -> proteinTarget
        DailyHeatmapMode.CARBS -> carbsTarget
        DailyHeatmapMode.FAT -> fatTarget
    }
    val cellModifier = modifier
        .clip(shape)
        .background(if (day == null || isFuture) emptyColor else Color.Transparent)
        .then(
            if (selected) {
                Modifier.border(1.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = 0.16f), shape)
            } else {
                Modifier
            },
        )
                .then(
            if (selectable) {
                Modifier
                    .clickable(role = Role.Button) { onSelect(day.dateEpochDay) }
                    .semantics {
                        contentDescription = heatmapContentDescription(
                            date = date,
                            day = day,
                            mode = mode,
                            target = target,
                        )
                    }
            } else {
                Modifier
            },
        )

    Box(cellModifier) {
        if (day != null && !isFuture) {
            val value = dailyHeatmapValue(mode, day.total)
            val color = when (mode) {
                DailyHeatmapMode.CALORIES -> calorieColor
                DailyHeatmapMode.PROTEIN -> proteinColor
                DailyHeatmapMode.CARBS -> carbsColor
                DailyHeatmapMode.FAT -> fatColor
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color.copy(alpha = heatmapIntensity(value, target))),
            )
        }
    }
}

private fun heatmapIntensity(value: Double, target: Float): Float =
    com.click.lightmemo.domain.RecordingHeatmap.intensity(value, target)

private fun dailyHeatmapValue(mode: DailyHeatmapMode, nutrition: Nutrition): Double = when (mode) {
    DailyHeatmapMode.CALORIES -> nutrition.caloriesKcal
    DailyHeatmapMode.PROTEIN -> nutrition.proteinG
    DailyHeatmapMode.CARBS -> nutrition.carbsG
    DailyHeatmapMode.FAT -> nutrition.fatG
}

private fun dailyAverageValue(mode: DailyHeatmapMode, state: StatsViewModel.StatsUiState): Double = when (mode) {
    DailyHeatmapMode.CALORIES -> state.averageKcal
    else -> dailyHeatmapValue(mode, state.averageNutrition)
}

private fun dailyHeatmapTarget(mode: DailyHeatmapMode, state: StatsViewModel.StatsUiState): Float = when (mode) {
    DailyHeatmapMode.CALORIES -> state.target
    DailyHeatmapMode.PROTEIN -> state.proteinTarget
    DailyHeatmapMode.CARBS -> state.carbsTarget
    DailyHeatmapMode.FAT -> state.fatTarget
}

private fun dailyHeatmapColor(mode: DailyHeatmapMode, palette: FoodPaletteColors): Color = when (mode) {
    DailyHeatmapMode.CALORIES -> palette.calorie
    DailyHeatmapMode.PROTEIN -> palette.protein
    DailyHeatmapMode.CARBS -> palette.carbs
    DailyHeatmapMode.FAT -> palette.fat
}

private fun heatmapContentDescription(
    date: LocalDate?,
    day: DayNutritionSummary,
    mode: DailyHeatmapMode,
    target: Float,
): String {
    val dateText = date?.format(DetailDateFormatter) ?: "当天"
    val value = dailyHeatmapValue(mode, day.total)
    val safeTarget = target.coerceAtLeast(1f)
    val unit = if (mode == DailyHeatmapMode.CALORIES) "千卡" else "克"
    return "$dateText，${value.formatMacro()}$unit，目标完成${(value / safeTarget * 100).toInt()}%"
}
@Composable
private fun DayMetricDetailSummary(
    day: DayNutritionSummary,
    mode: DailyHeatmapMode,
    target: Float,
    color: Color,
) {
    val value = dailyHeatmapValue(mode, day.total)
    val safeTarget = target.coerceAtLeast(1f)
    val unit = if (mode == DailyHeatmapMode.CALORIES) "kcal" else "g"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 7.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.07f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                day.dateEpochDay.toLocalDate().format(DetailDateFormatter),
                style = MiuixTheme.textStyles.footnote2,
            )
            Text(
                "${value.formatMacro()} / ${safeTarget.formatMacro()} $unit",
                style = MiuixTheme.textStyles.footnote2,
            )
        }
    }
}

private fun Double.formatMacro(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(Locale.US, this)

private fun Float.formatMacro(): String =
    if (this % 1f == 0f) toInt().toString() else "%.1f".format(Locale.US, this)

private fun Double.formatCount(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(Locale.US, this)

private fun Int.formatDuration(): String {
    val hours = this / 60
    val minutes = this % 60
    return when {
        hours == 0 -> "${minutes}分"
        minutes == 0 -> "${hours}小时"
        else -> "${hours}小时${minutes}分"
    }
}

private fun Int.formatMinuteOfDay(): String = "%02d:%02d".format(Locale.ROOT, this / 60, this % 60)

private fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(this)
