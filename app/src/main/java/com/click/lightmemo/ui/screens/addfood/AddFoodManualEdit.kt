package com.click.lightmemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.viewmodel.NewComponentId
import com.click.lightmemo.viewmodel.newFoodComponent
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.FoodComponent
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import com.click.lightmemo.ui.components.ComponentDatabaseOverlay
import com.click.lightmemo.ui.theme.FoodPaletteColors
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.ManualDraftSnapshot
import java.time.LocalDate
import java.time.LocalTime
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme


/** 手动录入：完整复刻编辑记录页元素，高度与记录食物 bottomsheet 一致。 */
@Composable
internal fun ManualEditContent(
    padding: PaddingValues,
    initialName: String,
    initialGrams: Double?,
    initialNutrition: Nutrition?,
    initialComponents: List<FoodComponent> = emptyList(),
    defaultMealType: MealType,
    defaultDateEpochDay: Long,
    defaultMinuteOfDay: Int,
    defaultNote: String,
    defaultTags: Set<String>,
    calorieTarget: Float,
    proteinTarget: Float,
    carbsTarget: Float,
    fatTarget: Float,
    palette: FoodPaletteColors,
    draftDatabaseSearch: com.click.lightmemo.viewmodel.DatabaseSearchState?,
    saving: Boolean,
    error: String?,
    showMealType: Boolean = true,
    showMealInfo: Boolean = true,
    saveButtonLabel: String = "保存",
    showDeletePreset: Boolean = false,
    deletePresetLabel: String = "删除预设",
    onOpenDeletePresetConfirm: (() -> Unit)? = null,
    onOpenComponentSearch: (FoodComponent) -> Unit,
    onOpenNewComponentSearch: () -> Unit,
    onSearchComponent: (String, String) -> Unit,
    onCloseComponentSearch: () -> Unit,
    onSave: (FoodLog) -> Unit,
    onHasContentChange: (Boolean) -> Unit,
    onDraftSync: (ManualDraftSnapshot) -> Unit,
) {
    val initialEpochDay = defaultDateEpochDay
    val componentGrams = initialComponents.sumOf { it.estimatedWeightG }
    val initialDraft = remember(
        initialName,
        initialGrams,
        initialNutrition,
        initialComponents,
        defaultMealType,
        initialEpochDay,
    ) {
        FoodLog(
            name = initialName,
            mealType = defaultMealType,
            grams = when {
                componentGrams > 0.0 -> componentGrams
                initialGrams != null && initialGrams.isFinite() && initialGrams > 0 -> initialGrams
                else -> 100.0
            },
            nutrition = initialNutrition
                ?: completeComponentNutrition(initialComponents)
                ?: Nutrition(),
            components = initialComponents,
            dateEpochDay = initialEpochDay,
            mealMinuteOfDay = defaultMinuteOfDay,
            note = defaultNote.takeIf { it.isNotBlank() },
            mealTags = defaultTags.toList(),
        )
    }
    var draft by remember { mutableStateOf(initialDraft) }
    var gramsInput by remember { mutableStateOf(initialDraft.grams.formatEditNumber()) }

    LaunchedEffect(draft.name, draft.components, draft.nutrition, draft.note) {
        val emptyNutrition = Nutrition()
        val has = draft.name.isNotBlank() ||
            draft.components.isNotEmpty() ||
            draft.nutrition != emptyNutrition ||
            !draft.note.isNullOrBlank()
        onHasContentChange(has)
        onDraftSync(
            ManualDraftSnapshot(
                name = draft.name,
                grams = draft.grams,
                nutrition = draft.nutrition,
                components = draft.components,
            ),
        )
    }
    var dateInput by remember { mutableStateOf(LocalDate.ofEpochDay(initialEpochDay).toString()) }
    var timeInput by remember {
        mutableStateOf("%02d:%02d".format(java.util.Locale.ROOT, defaultMinuteOfDay / 60, defaultMinuteOfDay % 60))
    }
    var nutritionField by remember { mutableStateOf<NutritionField?>(null) }
    var textEditField by remember { mutableStateOf<TextEditField?>(null) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var showAddComponent by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val busy = saving
    val editingAddComponent = draftDatabaseSearch?.componentId == NewComponentId
    val parsedWeight = gramsInput.toDoubleOrNull()
    val parsedDate = runCatching { LocalDate.parse(dateInput) }.getOrNull()
    val parsedTime = runCatching { LocalTime.parse(timeInput) }.getOrNull()
    val valid = draft.name.isNotBlank() &&
        parsedWeight?.let { it.isFinite() && it > 0 } == true &&
        parsedDate != null &&
        draft.nutrition.isValidEditNutrition()
    val componentNutrition = completeComponentNutrition(draft.components)
    val hasManualNutritionOverride = componentNutrition != null && draft.nutrition != componentNutrition

    fun updateWeight(value: String) {
        gramsInput = value
        val nextWeight = value.toDoubleOrNull()
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
            draft = draft.copy(
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
    }

    MealDatePickerOverlay(
        show = showDatePicker,
        date = parsedDate ?: LocalDate.ofEpochDay(initialEpochDay),
        onDismiss = { showDatePicker = false },
        onConfirm = {
            dateInput = it.toString()
            showDatePicker = false
        },
    )
    MealTimePickerOverlay(
        show = showTimePicker,
        minuteOfDay = parsedTime?.let { it.hour * 60 + it.minute }
            ?: defaultMinuteOfDay.coerceIn(0, 1439),
        onDismiss = { showTimePicker = false },
        onConfirm = { minute ->
            timeInput = "%02d:%02d".format(java.util.Locale.ROOT, minute / 60, minute % 60)
            showTimePicker = false
        },
    )

    EditNutritionDialog(
        field = nutritionField,
        nutrition = draft.nutrition,
        onDismiss = { nutritionField = null },
        onConfirm = { field, value ->
            draft = draft.copy(nutrition = draft.nutrition.withField(field, value))
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
                TextEditField.FOOD_NAME -> draft = draft.copy(name = value)
                TextEditField.NOTE -> draft = draft.copy(note = value.trim().ifBlank { null })
            }
            textEditField = null
        },
    )

    ComponentDatabaseOverlay(
        searchState = draftDatabaseSearch,
        adding = editingAddComponent,
        showAdd = showAddComponent,
        onDismiss = {
            showAddComponent = false
            onCloseComponentSearch()
        },
        onSearch = onSearchComponent,
        onSelect = { componentId, query, grams, reference ->
            if (componentId == NewComponentId) {
                val weight = grams.toDoubleOrNull()
                if (weight != null && weight.isFinite() && weight > 0 && query.isNotBlank()) {
                    val next = draft.components + newFoodComponent(query, weight, reference)
                    draft = draft.copy(
                        components = next,
                        grams = next.sumOf { it.estimatedWeightG },
                        nutrition = completeComponentNutrition(next) ?: draft.nutrition,
                    )
                    gramsInput = draft.grams.formatEditNumber()
                    showAddComponent = false
                    onCloseComponentSearch()
                }
            } else {
                val next = draft.components.map { component ->
                    if (component.id == componentId) {
                        val selectedQuery = query.trim()
                        component.copy(
                            nutritionReference = reference,
                            databaseQuery = if (reference.dataType.contains("中国")) {
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
                draft = draft.copy(
                    components = next,
                    nutrition = completeComponentNutrition(next) ?: draft.nutrition,
                )
                onCloseComponentSearch()
            }
        },
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(AddFoodSheetHeight)
            .overScrollVertical()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 40.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showMealType) {
            item {
                MealTypeSelector(
                    selected = draft.mealType,
                    onSelect = { if (!busy) draft = draft.copy(mealType = it) },
                )
            }
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
        }

        if (showMealInfo) {
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
                                    onWeightChange = { componentId, weight ->
                                        if (!busy) {
                                            val next = draft.components.map {
                                                if (it.id == componentId) it.withEditWeight(weight) else it
                                            }
                                            draft = draft.copy(
                                                components = next,
                                                grams = next.sumOf { it.estimatedWeightG },
                                                nutrition = completeComponentNutrition(next) ?: draft.nutrition,
                                            )
                                            gramsInput = draft.grams.formatEditNumber()
                                        }
                                    },
                                    onRemove = { componentId ->
                                        if (!busy) {
                                            val next = draft.components.filterNot { it.id == componentId }
                                            draft = if (next.isEmpty()) {
                                                draft.copy(components = emptyList())
                                            } else {
                                                draft.copy(
                                                    components = next,
                                                    grams = next.sumOf { it.estimatedWeightG },
                                                    nutrition = completeComponentNutrition(next) ?: draft.nutrition,
                                                )
                                            }
                                            if (next.isNotEmpty()) gramsInput = draft.grams.formatEditNumber()
                                        }
                                    },
                                    onMatch = { if (!busy) onOpenComponentSearch(it) },
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
                            onOpenNewComponentSearch()
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = sheetSecondaryButtonColors(enabled = !busy),
                ) { Text("添加食物成分") }
            }
        }

        if (hasManualNutritionOverride) {
            item {
                Text(
                    "手动修改了总营养后，保存时将移除组成部分；如需保留组成，请修改克重或数据库匹配。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        error?.let { message ->
            item { Text(message, color = MiuixTheme.colorScheme.error) }
        }
        item {
            Button(
                onClick = {
                    if (parsedWeight != null && parsedDate != null) {
                        val saved = draft.copy(
                            name = draft.name.trim(),
                            grams = parsedWeight,
                            dateEpochDay = parsedDate.toEpochDay(),
                            mealMinuteOfDay = parsedTime?.let { it.hour * 60 + it.minute },
                            note = draft.note?.trim()?.ifBlank { null },
                        ).let { candidate ->
                            val nutritionFromComponents = completeComponentNutrition(candidate.components)
                            if (nutritionFromComponents != null && candidate.nutrition != nutritionFromComponents) {
                                candidate.copy(components = emptyList())
                            } else {
                                candidate
                            }
                        }
                        onSave(saved)
                    }
                },
                enabled = valid && !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(if (busy) "保存中…" else saveButtonLabel) }
        }

        if (showDeletePreset && onOpenDeletePresetConfirm != null) {
            item {
                Button(
                    onClick = { if (!busy) onOpenDeletePresetConfirm() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(
                        color = MiuixTheme.colorScheme.error,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(deletePresetLabel)
                }
            }
        }
    }
}


