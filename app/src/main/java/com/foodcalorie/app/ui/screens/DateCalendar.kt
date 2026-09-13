package com.foodcalorie.app.ui.screens

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodcalorie.app.domain.DayNutritionSummary
import com.foodcalorie.app.domain.Nutrition
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val WeekdayLabels = listOf("日", "一", "二", "三", "四", "五", "六")

@Composable
private fun WeekDateSelector(
    days: List<DayNutritionSummary?>,
    target: Float,
    selectedEpochDay: Long?,
    onSelect: (Long) -> Unit,
    month: YearMonth? = null,
    expansion: Float = 0f,
    selectionWeight: (Long) -> Float,
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
                selectionWeight = day?.dateEpochDay?.let(selectionWeight) ?: 0f,
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
internal fun DateCalendar(
    days: List<DayNutritionSummary>,
    target: Float,
    selectedEpochDay: Long?,
    expanded: Boolean,
    anchor: LocalDate,
    onMove: (Int) -> Unit,
    onSelect: (Long) -> Unit,
    daySwipeProgress: Float = 0f,
    previewEpochDay: Long? = null,
    canMoveNext: Boolean = true,
    title: (@Composable (LocalDate) -> Unit)? = null,
) {
    val expansion by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(350),
        label = "calendarExpansion",
    )
    val summaries = remember(days) { days.associateBy { it.dateEpochDay } }
    var offset by remember(expanded, anchor) { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val sidePaddingPx = with(LocalDensity.current) { 16.dp.roundToPx() }
    val flingThreshold = with(LocalDensity.current) { 400.dp.toPx() }
    DisposableEffect(expanded, anchor) {
        onDispose { settling?.cancel() }
    }
    fun settle(requestedDirection: Int) {
        val direction = if (requestedDirection > 0 && !canMoveNext) 0 else requestedDirection
        settling?.cancel()
        settling = scope.launch {
            animate(offset, -direction * width.toFloat(), animationSpec = tween(250)) { value, _ ->
                offset = value
            }
            offset = 0f
            if (direction != 0) onMove(direction)
        }
    }
    val previewDate = previewEpochDay?.let(LocalDate::ofEpochDay)
    val crossesPeriod = previewDate != null && if (expanded) {
        YearMonth.from(previewDate) != YearMonth.from(anchor)
    } else {
        previewDate.minusDays(previewDate.dayOfWeek.value.toLong() - 1) !=
            anchor.minusDays(anchor.dayOfWeek.value.toLong() - 1)
    }
    val linkedOffset = if (crossesPeriod) -daySwipeProgress * width else 0f
    val displayOffset = offset + linkedOffset
    val selectionWeight: (Long) -> Float = { epoch ->
        when (epoch) {
            selectedEpochDay -> 1f - abs(daySwipeProgress)
            previewEpochDay -> abs(daySwipeProgress)
            else -> 0f
        }
    }
    // Measure all three pages so months with different row counts interpolate in height.
    Layout(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clipToBounds()
            .onSizeChanged { size ->
                // Pitch = screen - one side margin, matching DaySwipePages: next page
                // starts at the screen edge with a 16dp gap.
                width = (size.width - sidePaddingPx).coerceAtLeast(0)
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(if (expanded) "上个月" else "上一周") { settle(-1); true },
                    CustomAccessibilityAction(if (expanded) "下个月" else "下一周") { settle(1); true },
                )
            }
            .draggable(
                state = rememberDraggableState { amount ->
                    offset = (offset + amount).coerceIn(-width.toFloat(), width.toFloat())
                },
                orientation = Orientation.Horizontal,
                enabled = daySwipeProgress == 0f,
                startDragImmediately = settling?.isActive == true,
                onDragStarted = { settling?.cancel() },
                onDragStopped = { velocity ->
                    val direction = when {
                        abs(velocity) > flingThreshold -> if (velocity < 0) 1 else -1
                        abs(offset) > width * 0.25f -> if (offset < 0) 1 else -1
                        else -> 0
                    }
                    settle(direction)
                },
            ),
        content = {
            for (direction in -1..1) {
                val pageAnchor = if (expanded) anchor.plusMonths(direction.toLong())
                    else anchor.plusWeeks(direction.toLong())
                val pageStart = pageAnchor.minusDays(pageAnchor.dayOfWeek.value.toLong() - 1)
                Column(
                    Modifier.fillMaxWidth().then(
                        if (direction != 0) Modifier.clearAndSetSemantics { } else Modifier,
                    ),
                ) {
                    if (title != null) title(pageAnchor) else Text(
                        text = if (expanded) "${pageAnchor.year}年${pageAnchor.monthValue}月"
                            else "${pageStart.monthValue}月${pageStart.dayOfMonth}日 — ${pageStart.plusDays(6).monthValue}月${pageStart.plusDays(6).dayOfMonth}日",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
                    )
                    CalendarRows(
                        modifier = Modifier.fillMaxWidth().clipToBounds(),
                        anchor = pageAnchor,
                        selectedEpochDay = selectedEpochDay,
                        summaries = summaries,
                        target = target,
                        expansion = expansion,
                        onSelect = { if (direction == 0 && displayOffset == 0f) onSelect(it) },
                        selectionWeight = selectionWeight,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val pageWidth = (constraints.maxWidth - sidePaddingPx * 2).coerceAtLeast(0)
        val pitch = pageWidth + sidePaddingPx
        val pages = measurables.map {
            it.measure(constraints.copy(minWidth = pageWidth, maxWidth = pageWidth, minHeight = 0))
        }
        val progress = if (pitch > 0) (abs(displayOffset) / pitch).coerceIn(0f, 1f) else 0f
        val adjacent = if (displayOffset > 0) pages[0] else pages[2]
        val height = (pages[1].height + (adjacent.height - pages[1].height) * progress).roundToInt()
        layout(constraints.maxWidth, height) {
            pages.forEachIndexed { index, page ->
                page.place(sidePaddingPx + (index - 1) * pitch + displayOffset.roundToInt(), 0)
            }
        }
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
    selectionWeight: (Long) -> Float,
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
                    WeekDateSelector(week, target, selectedEpochDay, onSelect, month, expansion, selectionWeight)
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
    selectionWeight: Float = if (selected) 1f else 0f,
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
            .drawBehind {
                if (selectionWeight > 0f) {
                    scale(selectionWeight, selectionWeight) {
                        drawRoundRect(
                            color = surface.copy(alpha = surface.alpha * selectionWeight),
                            cornerRadius = CornerRadius(17.dp.toPx()),
                        )
                    }
                }
            }
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

private fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(this)
