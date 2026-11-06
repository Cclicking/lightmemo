package com.click.lightmemo.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.domain.completeNutrition
import com.click.lightmemo.domain.hasManualNutrition
import com.click.lightmemo.domain.withComponentNutrition
import com.click.lightmemo.domain.withEditedComponents
import com.click.lightmemo.ui.basic.SharedScrollBehavior
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import com.click.lightmemo.ui.components.ComponentDatabaseOverlay
import com.click.lightmemo.ui.theme.FoodPaletteColors
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.EditFoodViewModel
import com.click.lightmemo.viewmodel.newFoodComponent
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.ArrowPreference

internal enum class NutritionField { CALORIES, PROTEIN, CARBS, FAT }
internal enum class TextEditField { FOOD_NAME, NOTE }

@Composable
fun EditFoodScreen(
    viewModel: EditFoodViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: SharedScrollBehavior,
    listState: LazyListState,
    calorieTarget: Float,
    proteinTarget: Float,
    carbsTarget: Float,
    fatTarget: Float,
    palette: FoodPaletteColors,
    onSaved: () -> Unit,
    onExit: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val entry = state.entry

    BackHandler { viewModel.requestBack(onExit) }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LinearProgressIndicator(progress = null, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))
        }
        return
    }
    if (entry == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(state.error ?: "记录读取失败", color = MiuixTheme.colorScheme.error)
        }
        return
    }

    val safeEntryDate = remember(entry.id, entry.dateEpochDay) {
        entry.dateEpochDay.toSafeEditDate()
    }
    val editor = state.draft ?: return
    val draft = editor.food
    val gramsInput = editor.grams
    val dateInput = editor.date
    val timeInput = editor.time
    var nutritionField by remember { mutableStateOf<NutritionField?>(null) }
    var textEditField by remember { mutableStateOf<TextEditField?>(null) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var showAddComponent by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val busy = state.saving || state.pinning
    val databaseSearch = state.databaseSearch
    val editingAddComponent = databaseSearch?.componentId == EditFoodViewModel.NewComponentId
    val parsedDate = runCatching { LocalDate.parse(dateInput) }.getOrNull()
    val parsedTime = runCatching { LocalTime.parse(timeInput) }.getOrNull()
    val valid = editor.validatedFood() != null

    fun updateWeight(value: String) {
        val nextWeight = value.toDoubleOrNull()
        var nextDraft = draft
        if (nextWeight != null && nextWeight.isFinite() && nextWeight > 0 && draft.grams > 0) {
            val ratio = nextWeight / draft.grams
            val componentsAreSource = completeComponentNutrition(draft.components)?.let {
                draft.nutrition == it
            } == true
            val nextComponents = if (draft.components.isNotEmpty()) {
                draft.components.map { it.withEditWeight(it.estimatedWeightG * ratio) }
            } else {
                draft.components
            }
            nextDraft = draft.copy(
                grams = nextWeight,
                components = nextComponents,
                nutrition = if (nextComponents.isNotEmpty()) {
                    if (componentsAreSource) {
                        completeComponentNutrition(nextComponents) ?: draft.nutrition * ratio
                    } else {
                        draft.nutrition * ratio
                    }
                } else {
                    draft.nutrition * ratio
                },
            )
        }
        viewModel.updateDraft { it.copy(food = nextDraft, grams = value) }
    }

    AnimatedOverlayDialog(
        show = state.showLeaveConfirm,
        title = "保存修改？",
        onDismissRequest = { if (!busy) viewModel.dismissLeaveConfirm() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.error?.let { Text(it, color = MiuixTheme.colorScheme.error) }
            Button(
                onClick = { viewModel.saveDraft(onSaved) },
                enabled = valid && !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(if (state.saving) "保存中…" else "保存并返回") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = viewModel::dismissLeaveConfirm, enabled = !busy, modifier = Modifier.weight(1f)) { Text("继续编辑") }
                Button(onClick = { viewModel.discardDraft(onExit) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("放弃修改") }
            }
        }
    }

    MealDatePickerOverlay(
        show = showDatePicker,
        date = parsedDate ?: safeEntryDate,
        onDismiss = { showDatePicker = false },
        onConfirm = {
            viewModel.updateDraft { current -> current.copy(date = it.toString()) }
            showDatePicker = false
        },
    )
    MealTimePickerOverlay(
        show = showTimePicker,
        minuteOfDay = parsedTime?.let { it.hour * 60 + it.minute }
            ?: entry.mealMinuteOfDay?.coerceIn(0, 1439)
            ?: LocalTime.now().let { it.hour * 60 + it.minute },
        onDismiss = { showTimePicker = false },
        onConfirm = { minute ->
            viewModel.updateDraft { it.copy(time = "%02d:%02d".format(Locale.ROOT, minute / 60, minute % 60)) }
            showTimePicker = false
        },
        onClear = {
            viewModel.updateDraft { it.copy(time = "") }
            showTimePicker = false
        },
    )

    EditNutritionDialog(
        field = nutritionField,
        nutrition = draft.nutrition,
        onDismiss = { nutritionField = null },
        onConfirm = { field, value ->
            viewModel.updateFoodDraft(draft.copy(nutrition = draft.nutrition.withField(field, value)))
            nutritionField = null
        },
    )

    NumberInputDialog(
        show = showWeightDialog,
        title = "重量 g",
        summary = "修改后按比例更新营养与组成",
        initial = gramsInput,
        onDismiss = { showWeightDialog = false },
        onConfirm = {
            updateWeight(it)
            showWeightDialog = false
        },
    )

    TextInputDialog(
        field = textEditField,
        initial = when (textEditField) {
            TextEditField.FOOD_NAME -> draft.name
            TextEditField.NOTE -> draft.note.orEmpty()
            null -> ""
        },
        onDismiss = { textEditField = null },
        onConfirm = { field, value ->
            when (field) {
                TextEditField.FOOD_NAME -> viewModel.updateFoodDraft(draft.copy(name = value))
                TextEditField.NOTE -> viewModel.updateFoodDraft(draft.copy(note = value.trim().ifBlank { null }))
            }
            textEditField = null
        },
    )

    ComponentDatabaseOverlay(
        searchState = databaseSearch,
        adding = editingAddComponent,
        showAdd = showAddComponent,
        onDismiss = {
            showAddComponent = false
            viewModel.closeComponentSearch()
        },
        onSearch = viewModel::searchComponentDatabase,
        onSelect = { componentId, query, grams, reference ->
            if (componentId == EditFoodViewModel.NewComponentId) {
                val weight = grams.toDoubleOrNull()
                if (weight != null && weight.isFinite() && weight > 0 && query.isNotBlank()) {
                    val next = draft.components + newFoodComponent(query, weight, reference)
                    val nextDraft = draft.withEditedComponents(next)
                    viewModel.updateDraft { it.copy(food = nextDraft, grams = nextDraft.grams.formatEditNumber()) }
                    showAddComponent = false
                    viewModel.closeComponentSearch()
                }
            } else {
                val selectedQuery = query.trim()
                val replacingName = databaseSearch?.replaceComponentName == true && selectedQuery.isNotBlank()
                val next = draft.components.map { component ->
                    if (component.id == componentId) {
                        component.copy(
                            name = if (replacingName) selectedQuery else component.name,
                            nutritionReference = reference,
                            databaseQuery = if (replacingName && reference.dataType.contains("中国")) {
                                selectedQuery
                            } else {
                                reference.description
                            },
                            chinaDatabaseQuery = selectedQuery,
                        )
                } else {
                    component
                }
                }
                viewModel.updateFoodDraft(draft.withEditedComponents(next, updateGrams = false))
                viewModel.closeComponentSearch()
            }
        },
        onEstimateNutrition = { componentId, query, weightG, onResolved ->
            viewModel.estimateComponentNutrition(componentId, query, weightG, onResolved)
        },
        onEstimateResolved = { componentId, query, weight, reference ->
            if (componentId == EditFoodViewModel.NewComponentId) {
                val safeWeight = weight.takeIf { it.isFinite() && it > 0.0 } ?: 100.0
                val next = draft.components + newFoodComponent(query, safeWeight, reference)
                val nextDraft = draft.withEditedComponents(next)
                viewModel.updateDraft { it.copy(food = nextDraft, grams = nextDraft.grams.formatEditNumber()) }
            } else {
                val selectedQuery = query.trim()
                val replacingName = databaseSearch?.replaceComponentName == true && selectedQuery.isNotBlank()
                val next = draft.components.map { component ->
                    if (component.id == componentId) {
                        component.copy(
                            name = if (replacingName) selectedQuery else component.name,
                            nutritionReference = reference,
                            databaseQuery = reference.description,
                            chinaDatabaseQuery = selectedQuery,
                        )
                    } else {
                        component
                    }
                }
                viewModel.updateFoodDraft(draft.withEditedComponents(next, updateGrams = false))
            }
        },
        estimatingComponentId = state.aiEstimatingComponentId,
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MealTypeSelector(
                selected = draft.mealType,
                onSelect = { if (!busy) viewModel.updateFoodDraft(draft.copy(mealType = it)) },
            )
        }

        item {
            Column {
                SmallTitle(
                    text = "食物信息",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PickerField(
                        label = "食物名称",
                        value = draft.name.ifBlank { "未设置" },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = { textEditField = TextEditField.FOOD_NAME },
                    )
                    PickerField(
                        label = "重量 g",
                        value = gramsInput,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = { showWeightDialog = true },
                    )
                }
            }
        }

        item {
            EditNutritionSummary(
                nutrition = draft.nutrition,
                calorieTarget = calorieTarget,
                proteinTarget = proteinTarget,
                carbsTarget = carbsTarget,
                fatTarget = fatTarget,
                palette = palette,
                enabled = !busy,
                onNutritionField = { nutritionField = it },
            )
            if (draft.hasManualNutrition) {
                ManualNutritionControl(enabled = !busy && draft.components.completeNutrition() != null, onUseComponents = {
                    viewModel.updateFoodDraft(draft.withComponentNutrition())
                })
            }
        }

        item {
            Column {
                SmallTitle(
                    text = "用餐信息",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PickerField(
                        label = "日期",
                        value = dateInput,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = { showDatePicker = true },
                    )
                    PickerField(
                        label = "时间",
                        value = timeInput.ifBlank { "未设置" },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = { showTimePicker = true },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = "备注",
                        summary = draft.note?.takeIf { it.isNotBlank() } ?: "点击添加备注",
                        enabled = !busy,
                        onClick = { textEditField = TextEditField.NOTE },
                    )
                }
            }
        }

        item {
            Column {
                SmallTitle(
                    text = "组成部分",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                    if (draft.components.isEmpty()) {
                        Text("暂无食物成分", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    } else {
                        Column {
                            draft.components.forEach { component ->
                                ComponentResultRow(
                                    component = component,
                                    onNameClick = { clicked ->
                                        if (!busy) viewModel.openComponentSearch(clicked)
                                    },
                                    onWeightChange = { componentId, weight ->
                                        if (!busy) {
                                            val next = draft.components.map {
                                                if (it.id == componentId) it.withEditWeight(weight) else it
                                            }
                                            val nextDraft = draft.withEditedComponents(next)
                                            viewModel.updateDraft { it.copy(food = nextDraft, grams = nextDraft.grams.formatEditNumber()) }
                                        }
                                    },
                                    onRemove = { componentId ->
                                        if (!busy) {
                                            val next = draft.components.filterNot { it.id == componentId }
                                            val nextDraft = draft.withEditedComponents(next)
                                            viewModel.updateDraft { it.copy(food = nextDraft, grams = nextDraft.grams.formatEditNumber()) }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (!busy) {
                            showAddComponent = true
                            viewModel.openNewComponentSearch()
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("添加食物成分") }
            }
        }

        state.error?.let { message ->
            item { Text(message, color = MiuixTheme.colorScheme.error) }
        }
        item {
            Button(
                onClick = { viewModel.saveDraft(onSaved) },
                enabled = valid && !busy,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(if (busy) "保存中…" else "保存修改") }
        }
    }
}

@Composable
internal fun ManualNutritionControl(enabled: Boolean, onUseComponents: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("手动数值", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Button(onClick = onUseComponents, enabled = enabled) { Text("按组成计算") }
    }
}

@Composable
internal fun PickerField(
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.heightIn(min = 68.dp),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = { if (enabled) onClick() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(value, style = MiuixTheme.textStyles.body1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun EditNutritionSummary(
    nutrition: Nutrition,
    calorieTarget: Float,
    proteinTarget: Float,
    carbsTarget: Float,
    fatTarget: Float,
    palette: FoodPaletteColors,
    enabled: Boolean,
    onNutritionField: (NutritionField) -> Unit,
) {
    val calorieColor = if (nutrition.caloriesKcal > calorieTarget) palette.overTarget else palette.calorie
    Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(27.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) {
                    onNutritionField(NutritionField.CALORIES)
                },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("热量", style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
                    Text("${nutrition.caloriesKcal.toInt()} kcal", style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
                }
                LinearProgressIndicator(
                    progress = nutritionProgress(nutrition.caloriesKcal, calorieTarget),
                    modifier = Modifier.fillMaxWidth(),
                    height = 8.dp,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = calorieColor),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                EditableMacroRing("蛋白质", nutrition.proteinG, proteinTarget, palette.protein, enabled) {
                    onNutritionField(NutritionField.PROTEIN)
                }
                EditableMacroRing("碳水", nutrition.carbsG, carbsTarget, palette.carbs, enabled) {
                    onNutritionField(NutritionField.CARBS)
                }
                EditableMacroRing("脂肪", nutrition.fatG, fatTarget, palette.fat, enabled) {
                    onNutritionField(NutritionField.FAT)
                }
            }
        }
    }
}

@Composable
internal fun EditableMacroRing(
    label: String,
    value: Double,
    target: Float,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    var ringSize by remember { mutableStateOf(32.dp) }
    Row(
        modifier = Modifier
            .width(104.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            progress = nutritionProgress(value, target),
            colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = color),
            size = ringSize,
            strokeWidth = (ringSize.value * 4f / 36f).dp.coerceAtLeast(3.dp),
        )
        Spacer(Modifier.width(6.dp))
        Column(
            Modifier.onSizeChanged { size ->
                val textHeight = with(density) { size.height.toDp() }
                if (textHeight > 0.dp && abs(textHeight.value - ringSize.value) > 0.5f) ringSize = textHeight
            },
        ) {
            Text(label, style = MiuixTheme.textStyles.footnote2, maxLines = 1)
            Text("${value.formatEditNumber()}g", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun EditNutritionDialog(
    field: NutritionField?,
    nutrition: Nutrition,
    onDismiss: () -> Unit,
    onConfirm: (NutritionField, Double) -> Unit,
) {
    val currentField = field ?: return
    val initial = when (currentField) {
        NutritionField.CALORIES -> nutrition.caloriesKcal
        NutritionField.PROTEIN -> nutrition.proteinG
        NutritionField.CARBS -> nutrition.carbsG
        NutritionField.FAT -> nutrition.fatG
    }.formatEditNumber()
    NumberInputDialog(
        show = true,
        title = when (currentField) {
            NutritionField.CALORIES -> "热量 kcal"
            NutritionField.PROTEIN -> "蛋白质 g"
            NutritionField.CARBS -> "碳水 g"
            NutritionField.FAT -> "脂肪 g"
        },
        summary = "点击确定后回填到记录",
        initial = initial,
        onDismiss = onDismiss,
        onConfirm = { value -> value.toDoubleOrNull()?.let { onConfirm(currentField, it) } },
    )
}

@Composable
internal fun TextInputDialog(
    field: TextEditField?,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (TextEditField, String) -> Unit,
) {
    val currentField = field ?: TextEditField.FOOD_NAME
    var draft by remember(field, initial) { mutableStateOf(initial) }
    AnimatedOverlayDialog(
        show = field != null,
        title = when (currentField) {
            TextEditField.FOOD_NAME -> "食物名称"
            TextEditField.NOTE -> "备注"
        },
        summary = "输入后点击确定保存",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(),
                ) { Text("取消") }
                Button(
                    onClick = { onConfirm(currentField, draft) },
                    enabled = currentField != TextEditField.FOOD_NAME || draft.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("确定") }
            }
        }
    }
}

internal fun FoodComponent.withEditWeight(weight: Double): FoodComponent {
    val old = estimatedWeightG.takeIf { it > 0 } ?: 1.0
    return copy(
        estimatedWeightG = weight,
        weightMinG = (weight * weightMinG / old).coerceAtMost(weight),
        weightMaxG = (weight * weightMaxG / old).coerceAtLeast(weight),
    )
}

internal fun completeComponentNutrition(components: List<FoodComponent>): Nutrition? = components.completeNutrition()

internal fun Nutrition.withField(field: NutritionField, value: Double): Nutrition = when (field) {
    NutritionField.CALORIES -> copy(caloriesKcal = value)
    NutritionField.PROTEIN -> copy(proteinG = value)
    NutritionField.CARBS -> copy(carbsG = value)
    NutritionField.FAT -> copy(fatG = value)
}

internal fun Nutrition.isValidEditNutrition(): Boolean = listOf(caloriesKcal, proteinG, carbsG, fatG).all { it.isFinite() && it >= 0 }

private fun Long.toSafeEditDate(): LocalDate =
    runCatching { LocalDate.ofEpochDay(this) }.getOrDefault(LocalDate.now())

internal fun nutritionProgress(value: Double, target: Float): Float =
    if (!value.isFinite() || !target.isFinite() || target <= 0f) {
        0f
    } else {
        (value / target).toFloat().coerceIn(0f, 1f)
    }

internal fun Double.formatEditNumber(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(Locale.ROOT, this)
