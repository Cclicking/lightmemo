package com.click.lightmemo.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import com.click.lightmemo.ui.theme.FoodPaletteColors
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.AddFoodUiState
import com.click.lightmemo.viewmodel.AddStep
import com.click.lightmemo.viewmodel.TodayViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private val dateFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
private const val ProteinTarget = 120f
private const val CarbsTarget = 250f
private const val FatTarget = 60f

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
    addState: AddFoodUiState,
    managementMode: Boolean,
    showDatePicker: Boolean,
    calendarExpanded: Boolean = false,
    palette: FoodPaletteColors = FoodPaletteColors.Default,
    onShowDatePicker: () -> Unit,
    onDismissDatePicker: () -> Unit,
    onAddClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val date by viewModel.date.collectAsState()
    val deleted by viewModel.deletedEntries.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val operationError by viewModel.operationError.collectAsState()
    val readError by viewModel.readError.collectAsState()
    var editingEntry by remember { mutableStateOf<FoodLog?>(null) }
    var selectedEntry by remember { mutableStateOf<FoodLog?>(null) }
    var deleteEntry by remember { mutableStateOf<FoodLog?>(null) }
    var showDetail by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    DatePickerOverlay(
        show = showDatePicker,
        date = date,
        onDismiss = onDismissDatePicker,
        onConfirm = {
            viewModel.selectDate(it)
            onDismissDatePicker()
        },
    )
    selectedEntry?.let { entry ->
        FoodDetailOverlay(
            entry = entry,
            show = showDetail,
            onDismiss = { showDetail = false },
            onDismissFinished = { selectedEntry = null },
            targets = state,
            palette = palette,
            onEdit = {
                showDetail = false
                editingEntry = entry
                viewModel.operationError.value = null
            },
        )
    }
    editingEntry?.let { entry ->
        EditFoodOverlay(entry, busy, operationError, onDismiss = { editingEntry = null }) { updated ->
            viewModel.update(updated) { editingEntry = null }
        }
    }
    deleteEntry?.let { entry ->
        DeleteFoodOverlay(
            entry = entry,
            show = showDelete,
            onDismiss = { showDelete = false },
            onDismissFinished = { deleteEntry = null },
            onDelete = {
                viewModel.delete(entry.id)
                showDelete = false
            },
        )
    }

    val swipe = rememberDaySwipeState(date to calendarExpanded)
    val progress = swipe.progress
    val previewDate = when {
        progress > 0f -> date.plusDays(1)
        progress < 0f -> date.minusDays(1)
        else -> null
    }
    val calendarDays = remember(state.entriesByDate) {
        state.entriesByDate.map { (epoch, entries) ->
            com.click.lightmemo.domain.DayNutritionSummary(
                epoch, entries.fold(Nutrition()) { total, entry -> total + entry.nutrition },
            )
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Fill only the visible content area (below the date, above the bottom bar).
        // Extending under the bottom bar would steal presses from the liquid add button.
        var dateHeightPx by remember { mutableIntStateOf(0) }
        val swipeMinHeight = (
            maxHeight -
                contentPadding.calculateTopPadding() -
                contentPadding.calculateBottomPadding() -
                with(LocalDensity.current) { dateHeightPx.toDp() }
            ).coerceAtLeast(0.dp)
        LazyColumn(
            modifier = Modifier.fillMaxSize().overScrollVertical()
            .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier),
        state = listState,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (deleted.isNotEmpty()) {
            item {
                Button(onClick = viewModel::undoDelete, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text("已删除「${deleted.last().name}」 · 撤销")
                }
            }
        }
        operationError?.let { message -> item { Text(message, modifier = Modifier.padding(horizontal = 16.dp)) } }
        readError?.let { message -> item { Text(message, modifier = Modifier.padding(horizontal = 16.dp)) } }
        item(key = "date") {
            if (managementMode) {
                Text(
                    text = date.format(dateFormatter),
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 28.dp, top = 4.dp),
                )
            } else {
                DateCalendar(
                    days = calendarDays,
                    target = state.target,
                    selectedEpochDay = date.toEpochDay(),
                    expanded = calendarExpanded,
                    anchor = date,
                    daySwipeProgress = progress,
                    previewEpochDay = previewDate?.toEpochDay(),
                    canMoveNext = if (calendarExpanded) YearMonth.from(date) < YearMonth.now()
                        else date.plusDays(7 - date.dayOfWeek.value.toLong()) < LocalDate.now(),
                    onMove = { direction ->
                        val next = if (calendarExpanded) date.plusMonths(direction.toLong())
                            else date.plusWeeks(direction.toLong())
                        viewModel.selectDate(minOf(next, LocalDate.now()))
                    },
                    onSelect = { viewModel.selectDate(LocalDate.ofEpochDay(it)) },
                    modifier = Modifier.onSizeChanged { dateHeightPx = it.height },
                    title = { pageDate ->
                        Text(
                            text = (if (previewDate != null && pageDate != date) previewDate else pageDate).format(dateFormatter),
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.clickable(role = Role.Button, onClick = onShowDatePicker)
                                .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 10.dp),
                        )
                    },
                )
            }
        }
        item(key = "dayCards") {
            DaySwipePages(
                state = swipe,
                enabled = !managementMode,
                canMoveNext = date < LocalDate.now(),
                onMove = { viewModel.selectDate(date.plusDays(it.toLong())) },
                modifier = Modifier.heightIn(min = swipeMinHeight),
            ) { direction ->
                val displayedDate = date.plusDays(direction.toLong())
                val pageEntries = state.entriesByDate[displayedDate.toEpochDay()].orEmpty()
                val pageTotal = remember(pageEntries) {
                    pageEntries.fold(Nutrition()) { total, entry -> total + entry.nutrition }
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!managementMode) {
                        NutritionSummaryCard(
                            total = pageTotal,
                            calorieTarget = state.target,
                            proteinTarget = state.proteinTarget,
                            carbsTarget = state.carbsTarget,
                            fatTarget = state.fatTarget,
                            palette = palette,
                        )
                        Button(onClick = onAddClick, modifier = Modifier.fillMaxWidth()) { Text("记录食物") }
                    } else {
                        Text(
                            text = "点击卡片查看详情，点击删除按钮或长按卡片进行移除。",
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }

                    val pendingForDate = addState.targetDateEpochDay == displayedDate.toEpochDay()
                    val pendingStep = addState.step as? AddStep.Review
                    MealType.entries.forEach { meal ->
                        val saved = pageEntries.filter { it.mealType == meal }
                        val recognizingHere = pendingForDate && addState.recognizing && addState.mealType == meal
                        val pendingDishes = if (pendingForDate && addState.mealType == meal) pendingStep?.result?.dishes.orEmpty() else emptyList()
                        if (saved.isNotEmpty() || recognizingHere || pendingDishes.isNotEmpty()) {
                            SmallTitle(
                                text = meal.label,
                                insideMargin = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 0.dp),
                            )
                            if (recognizingHere) FoodCardRow(left = { RecognitionCard() })
                            pendingDishes.chunked(2).forEach { row ->
                                androidx.compose.runtime.key("pending-${meal.name}-${row.first().id}") {
                                    FoodCardRow(
                                        left = { PendingFoodCard(row[0].name, row[0].grams, row[0].nutrition, onAddClick) },
                                        right = row.getOrNull(1)?.let { dish ->
                                            { PendingFoodCard(dish.name, dish.grams, dish.nutrition, onAddClick) }
                                        },
                                    )
                                }
                            }
                            saved.chunked(2).forEach { row ->
                                androidx.compose.runtime.key("saved-${meal.name}-${row.first().id}") {
                                    FoodCardRow(
                                        left = {
                                            FoodCard(row[0], managementMode, {
                                                selectedEntry = row[0]
                                                showDetail = true
                                            }, {
                                                deleteEntry = row[0]
                                                showDelete = true
                                            })
                                        },
                                        right = row.getOrNull(1)?.let { entry ->
                                            { FoodCard(entry, managementMode, {
                                                selectedEntry = entry
                                                showDetail = true
                                            }, {
                                                deleteEntry = entry
                                                showDelete = true
                                            }) }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (pageEntries.isEmpty() && (!pendingForDate || (!addState.recognizing && pendingStep == null))) {
                        Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp)) {
                            Text("还没有记录", style = MiuixTheme.textStyles.title4)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "拍照识别或手动添加一餐，开始统计热量",
                                style = MiuixTheme.textStyles.subtitle,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun NutritionSummaryCard(
    total: Nutrition,
    calorieTarget: Float,
    proteinTarget: Float,
    carbsTarget: Float,
    fatTarget: Float,
    palette: FoodPaletteColors = FoodPaletteColors.Default,
) {
    val calorieColor = if (total.caloriesKcal > calorieTarget) palette.overTarget else palette.calorie
    val progress by animateFloatAsState(
        targetValue = if (calorieTarget <= 0f) 0f else (total.caloriesKcal / calorieTarget).toFloat().coerceIn(0f, 1f),
        animationSpec = folmeSpring(damping = 1f, response = 0.6f),
        label = "calorieProgress",
    )
    Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Text("热量", style = MiuixTheme.textStyles.subtitle)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(total.caloriesKcal.toInt().toString(), fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(
                " / ${calorieTarget.toInt()} kcal",
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
            height = 10.dp,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = calorieColor),
        )
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MacroRing("蛋白质", total.proteinG, proteinTarget, palette.protein, Modifier.weight(1f))
            MacroRing("碳水", total.carbsG, carbsTarget, palette.carbs, Modifier.weight(1f))
            MacroRing("脂肪", total.fatG, fatTarget, palette.fat, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroRing(label: String, value: Double, target: Float, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            progress = (value / target).toFloat().coerceIn(0f, 1f),
            colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = color),
            size = 50.dp,
            strokeWidth = 6.dp,
        )
        Spacer(Modifier.width(7.dp))
        Column {
            Text(label, style = MiuixTheme.textStyles.footnote1, maxLines = 1)
            Text("${value.toInt()}g", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Bold)
            Text("/${target.toInt()}g", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

/** 与识别结果页总览一致的紧凑宏量环（仅两行文案，圆环随文字高度） */
@Composable
private fun CompactMacroRing(label: String, value: Double, target: Float, color: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var ringSize by remember { mutableStateOf(32.dp) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            progress = (value / target).toFloat().coerceIn(0f, 1f),
            colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = color),
            size = ringSize,
            strokeWidth = (ringSize.value * 4f / 36f).dp.coerceAtLeast(3.dp),
        )
        Spacer(Modifier.width(6.dp))
        Column(
            Modifier.onSizeChanged { coords ->
                val textHeight = with(density) { coords.height.toDp() }
                if (textHeight > 0.dp && abs(textHeight.value - ringSize.value) > 0.5f) {
                    ringSize = textHeight
                }
            },
        ) {
            Text(label, style = MiuixTheme.textStyles.footnote2, maxLines = 1)
            Text("${value.formatMacro()}g", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Bold)
        }
    }
}

private fun Double.formatMacro(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)

@Composable
private fun FoodCardRow(left: @Composable () -> Unit, right: (@Composable () -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { left() }
        Box(Modifier.weight(1f)) { right?.invoke() }
    }
}

@Composable
private fun FoodCard(entry: FoodLog, managementMode: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(14.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
        onLongPress = onDelete,
    ) {
        Text(entry.name, style = MiuixTheme.textStyles.title4, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${entry.grams.toInt()}g", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.weight(1f))
        Text("${entry.nutrition.caloriesKcal.toInt()} kcal", style = MiuixTheme.textStyles.title4)
        Text(
            "蛋白 ${entry.nutrition.proteinG.toInt()}g · 碳水 ${entry.nutrition.carbsG.toInt()}g · 脂肪 ${entry.nutrition.fatG.toInt()}g",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (managementMode) {
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onDelete,
                    minWidth = 48.dp,
                    minHeight = 30.dp,
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 3.dp),
                    colors = ButtonDefaults.buttonColorsPrimary(color = MiuixTheme.colorScheme.error),
                ) { Text("删除", style = MiuixTheme.textStyles.footnote1) }
            }
        }
    }
}

@Composable
private fun RecognitionCard() {
    Card(modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp), cornerRadius = 20.dp, insideMargin = PaddingValues(14.dp)) {
        Text("识别中", style = MiuixTheme.textStyles.body1)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = null, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text("正在分析食物与营养组成…", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun PendingFoodCard(name: String, grams: Double, nutrition: Nutrition, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(14.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Text("待确认", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.primary)
        Text(name, style = MiuixTheme.textStyles.title4, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${grams.toInt()}g", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.weight(1f))
        Text("${nutrition.caloriesKcal.toInt()} kcal", style = MiuixTheme.textStyles.title4)
        Text("点击检查并确认", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun FoodDetailOverlay(
    entry: FoodLog,
    targets: com.click.lightmemo.viewmodel.TodayUiState,
    palette: FoodPaletteColors,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onEdit: () -> Unit,
) {
    val calorieColor = if (entry.nutrition.caloriesKcal > targets.target) palette.overTarget else palette.calorie
    val timeText = entry.mealMinuteOfDay?.let { minute ->
        String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)
    }
    val summaryParts = buildList {
        add("${entry.grams.toInt()}g")
        add(entry.mealType.label)
        timeText?.let { add(it) }
    }
    AnimatedOverlayDialog(
        show = show,
        title = entry.name,
        summary = summaryParts.joinToString(" · "),
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!entry.note.isNullOrBlank() || entry.mealTags.isNotEmpty()) {
                Card(cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (entry.mealTags.isNotEmpty()) {
                            Text(
                                "本餐说明：${entry.mealTags.joinToString("、")}",
                                style = MiuixTheme.textStyles.subtitle,
                            )
                        }
                        if (!entry.note.isNullOrBlank()) {
                            Text(
                                "备注：${entry.note}",
                                style = MiuixTheme.textStyles.subtitle,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        timeText?.let {
                            Text(
                                "时间：$it",
                                style = MiuixTheme.textStyles.subtitle,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
            // 与识别结果页总览卡片保持一致
            Column(verticalArrangement = Arrangement.spacedBy(27.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("热量", style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${entry.nutrition.caloriesKcal.toInt()} kcal",
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    LinearProgressIndicator(
                        progress = (entry.nutrition.caloriesKcal / targets.target.coerceAtLeast(1f)).toFloat().coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                        height = 8.dp,
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = calorieColor),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    CompactMacroRing("蛋白质", entry.nutrition.proteinG, targets.proteinTarget, palette.protein, Modifier.weight(1f))
                    CompactMacroRing("碳水", entry.nutrition.carbsG, targets.carbsTarget, palette.carbs, Modifier.weight(1f))
                    CompactMacroRing("脂肪", entry.nutrition.fatG, targets.fatTarget, palette.fat, Modifier.weight(1f))
                }
            }
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("编辑记录") }
            SmallTitle(
                text = "组成部分",
                insideMargin = PaddingValues(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 0.dp),
            )
            if (entry.components.isEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(entry.name, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text("${entry.grams.toInt()}g · ${entry.nutrition.caloriesKcal.toInt()} kcal")
                }
            } else {
                entry.components.forEach { component ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(component.name)
                            component.nutritionReference?.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("${component.estimatedWeightG.toInt()}g · ${component.nutrition.caloriesKcal.toInt()} kcal")
                    }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") }
        }
    }
}

@Composable
private fun DeleteFoodOverlay(
    entry: FoodLog,
    show: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onDelete: () -> Unit,
) {
    AnimatedOverlayDialog(
        show = show,
        title = entry.name,
        summary = "要删除这张食物卡片吗？",
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(color = MiuixTheme.colorScheme.error),
            ) { Text("删除") }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("取消") }
        }
    }
}

@Composable
private fun DatePickerOverlay(show: Boolean, date: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    var year by remember(date, show) { mutableIntStateOf(date.year) }
    var month by remember(date, show) { mutableIntStateOf(date.monthValue) }
    var day by remember(date, show) { mutableIntStateOf(date.dayOfMonth) }
    val maxDay = YearMonth.of(year, month).lengthOfMonth()
    LaunchedEffect(maxDay) {
        if (day > maxDay) day = maxDay
    }

    AnimatedOverlayDialog(show = show, title = "选择日期", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                NumberPicker(value = year, onValueChange = { year = it }, range = 2020..LocalDate.now().year, label = { "${it}年" }, visibleItemCount = 3, modifier = Modifier.weight(1.25f))
                NumberPicker(value = month, onValueChange = { month = it }, range = 1..12, label = { "${it}月" }, visibleItemCount = 3, wrapAround = true, modifier = Modifier.weight(1f))
                NumberPicker(value = day, onValueChange = { day = it }, range = 1..maxDay, label = { "${it}日" }, visibleItemCount = 3, wrapAround = true, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = { onConfirm(LocalDate.of(year, month, day).coerceAtMost(LocalDate.now())) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("确定") }
            }
        }
    }
}
