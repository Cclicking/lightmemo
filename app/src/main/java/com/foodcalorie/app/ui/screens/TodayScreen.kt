package com.foodcalorie.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.compose.ui.unit.sp
import com.foodcalorie.app.domain.FoodLog
import com.foodcalorie.app.domain.MealType
import com.foodcalorie.app.domain.Nutrition
import com.foodcalorie.app.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.foodcalorie.app.ui.utils.overScrollVertical
import com.foodcalorie.app.viewmodel.AddFoodUiState
import com.foodcalorie.app.viewmodel.AddStep
import com.foodcalorie.app.viewmodel.TodayViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
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
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private val dateFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
private const val ProteinTarget = 120f
private const val CarbsTarget = 250f
private const val FatTarget = 60f
private val ProteinColor = Color(0xFFF3A17C)
private val CarbsColor = Color(0xFF2F7D2B)
private val FatColor = Color(0xFFFFB300)

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
    addState: AddFoodUiState,
    managementMode: Boolean,
    showDatePicker: Boolean,
    onShowDatePicker: () -> Unit,
    onDismissDatePicker: () -> Unit,
    onAddClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val date by viewModel.date.collectAsState()
    var selectedEntry by remember { mutableStateOf<FoodLog?>(null) }
    var deleteEntry by remember { mutableStateOf<FoodLog?>(null) }

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
        FoodDetailOverlay(entry = entry, onDismiss = { selectedEntry = null })
    }
    deleteEntry?.let { entry ->
        DeleteFoodOverlay(
            entry = entry,
            onDismiss = { deleteEntry = null },
            onDelete = {
                viewModel.delete(entry.id)
                deleteEntry = null
            },
        )
    }

    AnimatedContent(
        targetState = date,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "selectedDate",
    ) { displayedDate ->
        var horizontalDrag by remember(displayedDate) { mutableStateOf(0f) }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(displayedDate, managementMode) {
                    if (!managementMode) {
                        detectHorizontalDragGestures(
                            onDragStart = { horizontalDrag = 0f },
                            onHorizontalDrag = { change, amount ->
                                if (kotlin.math.abs(amount) > 1f) change.consume()
                                horizontalDrag += amount
                            },
                            onDragEnd = {
                                when {
                                    horizontalDrag > 72f -> viewModel.previousDay()
                                    horizontalDrag < -72f && displayedDate < LocalDate.now() -> viewModel.nextDay()
                                }
                                horizontalDrag = 0f
                            },
                        )
                    }
                }
                .overScrollVertical()
                .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier),
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = displayedDate.format(dateFormatter),
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onShowDatePicker)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            if (!managementMode) {
                item { NutritionSummaryCard(state.total, state.target) }
                item {
                    Button(onClick = onAddClick, modifier = Modifier.fillMaxWidth()) { Text("记录食物") }
                }
            } else {
                item {
                    Text(
                        text = "点击卡片查看详情，点击删除按钮或长按卡片进行移除。",
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }

            val pendingForDate = addState.targetDateEpochDay == displayedDate.toEpochDay()
            val pendingStep = addState.step as? AddStep.Review
            MealType.entries.forEach { meal ->
                val saved = state.entries.filter { it.mealType == meal }
                val recognizingHere = pendingForDate && addState.recognizing && addState.mealType == meal
                val pendingDishes = if (pendingForDate && addState.mealType == meal) pendingStep?.result?.dishes.orEmpty() else emptyList()
                if (saved.isNotEmpty() || recognizingHere || pendingDishes.isNotEmpty()) {
                    item {
                        SmallTitle(
                            text = meal.label,
                            insideMargin = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 0.dp),
                        )
                    }
                    if (recognizingHere) item { FoodCardRow(left = { RecognitionCard() }) }
                    pendingDishes.chunked(2).forEach { row ->
                        item(key = "pending-${meal.name}-${row.first().id}") {
                            FoodCardRow(
                                left = { PendingFoodCard(row[0].name, row[0].grams, row[0].nutrition, onAddClick) },
                                right = row.getOrNull(1)?.let { dish ->
                                    { PendingFoodCard(dish.name, dish.grams, dish.nutrition, onAddClick) }
                                },
                            )
                        }
                    }
                    saved.chunked(2).forEach { row ->
                        item(key = "saved-${meal.name}-${row.first().id}") {
                            FoodCardRow(
                                left = {
                                    FoodCard(row[0], managementMode, { selectedEntry = row[0] }, { deleteEntry = row[0] })
                                },
                                right = row.getOrNull(1)?.let { entry ->
                                    { FoodCard(entry, managementMode, { selectedEntry = entry }, { deleteEntry = entry }) }
                                },
                            )
                        }
                    }
                }
            }

            if (state.entries.isEmpty() && (!pendingForDate || (!addState.recognizing && pendingStep == null))) {
                item {
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

@Composable
private fun NutritionSummaryCard(total: Nutrition, calorieTarget: Float) {
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
        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth(), height = 10.dp)
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MacroRing("蛋白质", total.proteinG, ProteinTarget, ProteinColor, Modifier.weight(1f))
            MacroRing("碳水", total.carbsG, CarbsTarget, CarbsColor, Modifier.weight(1f))
            MacroRing("脂肪", total.fatG, FatTarget, FatColor, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroRing(label: String, value: Double, target: Float, color: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var ringSize by remember { mutableStateOf(40.dp) }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            progress = (value / target).toFloat().coerceIn(0f, 1f),
            colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = color),
            size = ringSize,
            strokeWidth = (ringSize.value * 6f / 50f).dp.coerceAtLeast(3.dp),
        )
        Spacer(Modifier.width(7.dp))
        Column(
            Modifier.onSizeChanged { coords ->
                val textHeight = with(density) { coords.height.toDp() }
                if (textHeight > 0.dp && abs(textHeight.value - ringSize.value) > 0.5f) {
                    ringSize = textHeight
                }
            },
        ) {
            Text(label, style = MiuixTheme.textStyles.footnote1, maxLines = 1)
            Text("${value.toInt()}g", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Bold)
            Text("/${target.toInt()}g", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

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
private fun FoodDetailOverlay(entry: FoodLog, onDismiss: () -> Unit) {
    val timeText = entry.mealMinuteOfDay?.let { minute ->
        String.format("%02d:%02d", minute / 60, minute % 60)
    }
    val summaryParts = buildList {
        add("${entry.grams.toInt()}g")
        add(entry.mealType.label)
        timeText?.let { add(it) }
    }
    OverlayDialog(
        show = true,
        title = entry.name,
        summary = summaryParts.joinToString(" · "),
        onDismissRequest = onDismiss,
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
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("热量", style = MiuixTheme.textStyles.title4)
                    Text("${entry.nutrition.caloriesKcal.toInt()} kcal", style = MiuixTheme.textStyles.title4)
                }
                Spacer(Modifier.height(3.dp))
                LinearProgressIndicator(
                    progress = (entry.nutrition.caloriesKcal / 1800.0).toFloat().coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    height = 8.dp,
                )
                Spacer(Modifier.height(15.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MacroRing("蛋白质", entry.nutrition.proteinG, ProteinTarget, ProteinColor, Modifier.weight(1f))
                    MacroRing("碳水", entry.nutrition.carbsG, CarbsTarget, CarbsColor, Modifier.weight(1f))
                    MacroRing("脂肪", entry.nutrition.fatG, FatTarget, FatColor, Modifier.weight(1f))
                }
            }
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
private fun DeleteFoodOverlay(entry: FoodLog, onDismiss: () -> Unit, onDelete: () -> Unit) {
    OverlayDialog(show = true, title = entry.name, summary = "要删除这张食物卡片吗？", onDismissRequest = onDismiss) {
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

    OverlayDialog(show = show, title = "选择日期", onDismissRequest = onDismiss) {
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
