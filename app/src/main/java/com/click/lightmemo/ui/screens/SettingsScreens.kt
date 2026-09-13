package com.click.lightmemo.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.lightmemo.data.ActivityLevel
import com.click.lightmemo.data.ColorThemePreset
import com.click.lightmemo.data.FoodColorPalette
import com.click.lightmemo.data.FoodPaletteGenerator
import com.click.lightmemo.data.HsvColor
import com.click.lightmemo.data.Gender
import com.click.lightmemo.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import com.click.lightmemo.ui.components.DropdownPref
import com.click.lightmemo.ui.theme.FoodPaletteColors
import com.click.lightmemo.ui.theme.toComposeColors
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** SmallTitle 与下方内容之间的间距 */
private val TitleToFieldSpacing = 0.dp

/** 内容与下一个 SmallTitle 之间的间距 */
private val FieldToTitleSpacing = 17.dp

/** 底部说明文案相对内容区的额外左右边距 */
private val FootnoteHorizontalPadding = 16.dp

@Composable
fun ApiSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
) {
    val settings by viewModel.settings.collectAsState()
    val ready by viewModel.settingsReady.collectAsState()
    val error by viewModel.error.collectAsState()
    val adding by viewModel.addingPreset.collectAsState()
    val context = LocalContext.current
    if (!ready) return
    AiSettingsList(contentPadding, scrollBehavior, listState) {
        item {
            if (!settings.isRecognitionConfigured) {
                Text(
                    text = "尚未配置识别服务，点击新增配置，填写 Base URL 与 API Key 后即可识别",
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 20.dp),
                )
            }
            SmallTitle("模型配置", modifier = Modifier.offset(x = (-16).dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                DropdownPref(
                    title = "当前配置",
                    summary = settings.model,
                    items = settings.apiPresets.map { it.name },
                    selectedIndex = settings.apiPresets.indexOfFirst { it.id == settings.activePresetId }.coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        settings.apiPresets.getOrNull(index)?.let { viewModel.selectPreset(it.id) }
                    },
                )
                ArrowPreference(
                    title = "编辑当前配置",
                    summary = settings.activePreset.name,
                    onClick = { context.startActivity(android.content.Intent(context, com.click.lightmemo.ui.secondary.ApiConfigurationActivity::class.java)) },
                )
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    viewModel.addPreset("配置 ${settings.apiPresets.size + 1}") {
                        context.startActivity(android.content.Intent(context, com.click.lightmemo.ui.secondary.ApiConfigurationActivity::class.java))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
                enabled = !adding,
            ) { Text("新增配置") }
            error?.let { Text(it, modifier = Modifier.padding(12.dp)) }
            Spacer(Modifier.height(FieldToTitleSpacing))
            SmallTitle("识别提示词", modifier = Modifier.offset(x = (-16).dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "Prompt 修改",
                    summary = "编辑、测试识别提示词与大模型背景信息",
                    onClick = { context.startActivity(android.content.Intent(context, com.click.lightmemo.ui.secondary.PromptSettingsActivity::class.java)) },
                )
            }
        }
    }
}

@Composable
fun ApiConfigurationScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
    onDeleted: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val ready by viewModel.settingsReady.collectAsState()
    if (!ready) return
    var presetName by androidx.compose.runtime.saveable.rememberSaveable(settings.activePreset.id) { mutableStateOf(settings.activePreset.name) }
    var baseUrl by androidx.compose.runtime.saveable.rememberSaveable(settings.activePreset.id) { mutableStateOf(settings.baseUrl) }
    var apiKey by androidx.compose.runtime.saveable.rememberSaveable(settings.activePreset.id) { mutableStateOf(settings.apiKey) }
    var model by androidx.compose.runtime.saveable.rememberSaveable(settings.activePreset.id) { mutableStateOf(settings.model) }
    val error by viewModel.error.collectAsState()
    val saving by viewModel.savingPreset.collectAsState()
    AiSettingsList(contentPadding, scrollBehavior, listState) {
        item {
            Column {
                SmallTitle(
                    text = "配置名称",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                TextField(
                    value = presetName,
                    onValueChange = { value ->
                        presetName = value
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(FieldToTitleSpacing))
                SmallTitle(
                    text = "Base URL",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                TextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(FieldToTitleSpacing))
                SmallTitle(
                    text = "API Key",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                TextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(FieldToTitleSpacing))
                SmallTitle(
                    text = "模型名",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                TextField(
                    value = model,
                    onValueChange = {
                        model = it
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )


                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        viewModel.savePreset(
                            id = settings.activePreset.id,
                            name = presetName,
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            model = model,
                        )
                    },
                    enabled = !saving && presetName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text(if (saving) "保存中…" else "保存配置") }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.deleteActivePreset(onDeleted) },
                    enabled = settings.apiPresets.size > 1 && !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("删除当前配置") }
                error?.let { Text(it, modifier = Modifier.padding(12.dp)) }
            }
        }
    }
}

@Composable
internal fun AiSettingsList(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding().overScrollVertical()
            .then(scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        content = content,
    )
}

@Composable
fun CalorieTargetScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
) {
    val settings by viewModel.settings.collectAsState()
    val kcal = settings.dailyCalorieTarget.toInt()
    val protein = settings.effectiveProteinG
    val fat = settings.effectiveFatG
    val carbs = settings.effectiveCarbsG

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
    ) {
        item {
            Column {
                SmallTitle(
                    text = "热量目标",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    var showDialog by remember { mutableStateOf(false) }
                    var draft by remember { mutableStateOf(kcal.toString()) }

                    ArrowPreference(
                        title = "每日热量",
                        summary = "用于进度条与达标统计",
                        endActions = {
                            Text(
                                text = "$kcal kcal",
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        },
                        onClick = {
                            draft = kcal.toString()
                            showDialog = true
                        },
                    )
                    AnimatedOverlayDialog(
                        title = "每日热量目标",
                        summary = "输入 500–10000 kcal",
                        show = showDialog,
                        onDismissRequest = { showDialog = false },
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TextField(
                                value = draft,
                                onValueChange = { value ->
                                    draft = value.filter { it.isDigit() }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = { showDialog = false },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(),
                                ) {
                                    Text("取消")
                                }
                                Button(
                                    onClick = {
                                        draft.toIntOrNull()?.let { viewModel.setTarget(it.toFloat()) }
                                        showDialog = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                ) {
                                    Text("确定")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(FieldToTitleSpacing))
                SmallTitle(
                    text = "营养素目标",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    InputDialogPreferenceRow(
                        title = "蛋白质",
                        unit = "g",
                        value = protein.toInt(),
                        range = 20..300,
                        summary = if (settings.proteinTargetG <= 0f) "跟随推荐" else null,
                        onValueChange = { viewModel.setProteinTarget(it.toFloat()) },
                    )
                    InputDialogPreferenceRow(
                        title = "脂肪",
                        unit = "g",
                        value = fat.toInt(),
                        range = 10..200,
                        summary = if (settings.fatTargetG <= 0f) "跟随推荐" else null,
                        onValueChange = { viewModel.setFatTarget(it.toFloat()) },
                    )
                    InputDialogPreferenceRow(
                        title = "碳水",
                        unit = "g",
                        value = carbs.toInt(),
                        range = 20..600,
                        summary = if (settings.carbsTargetG <= 0f) "跟随推荐" else null,
                        onValueChange = { viewModel.setCarbsTarget(it.toFloat()) },
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "未手动设置时，营养素目标根据身高体重与运动强度自动推荐",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = FootnoteHorizontalPadding),
                )
            }
        }
    }
}

@Composable
private fun NumberPickerPreferenceRow(
    title: String,
    unit: String,
    value: Int,
    range: IntRange,
    summary: String? = null,
    onValueChange: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(value) }

    ArrowPreference(
        title = title,
        summary = summary,
        endActions = {
            Text(
                text = "$value $unit",
                fontSize = 14.5.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        onClick = {
            draft = value.coerceIn(range.first, range.last)
            showPicker = true
        },
    )
    AnimatedOverlayDialog(
        title = "选择$title",
        show = showPicker,
        onDismissRequest = { showPicker = false },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NumberPicker(
                value = draft,
                onValueChange = { draft = it },
                range = range,
                label = { "$it $unit" },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    onValueChange(draft)
                    showPicker = false
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("确定")
            }
        }
    }
}

@Composable
private fun InputDialogPreferenceRow(
    title: String,
    unit: String,
    value: Int,
    range: IntRange,
    summary: String? = null,
    dialogTitle: String = "${title}目标",
    onValueChange: (Int) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(value.toString()) }

    ArrowPreference(
        title = title,
        summary = summary,
        endActions = {
            Text(
                text = "$value $unit",
                fontSize = 14.5.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        onClick = {
            draft = value.toString()
            showDialog = true
        },
    )
    AnimatedOverlayDialog(
        title = dialogTitle,
        summary = "输入 ${range.first}–${range.last} $unit",
        show = showDialog,
        onDismissRequest = { showDialog = false },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextField(
                value = draft,
                onValueChange = { text -> draft = text.filter { it.isDigit() } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { showDialog = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        draft.toIntOrNull()?.let { raw ->
                            onValueChange(raw.coerceIn(range.first, range.last))
                        }
                        showDialog = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("确定")
                }
            }
        }
    }
}

@Composable
fun PersonalInfoScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
) {
    val settings by viewModel.settings.collectAsState()
    val nutrients = settings.recommendedNutrients

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
    ) {
        item {
            Column {
                SmallTitle(
                    text = "身体信息",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    NumberPickerPreferenceRow(
                        title = "身高",
                        unit = "cm",
                        value = settings.heightCm.toInt().takeIf { it > 0 } ?: 170,
                        range = 100..220,
                        onValueChange = { viewModel.setHeight(it.toFloat()) },
                    )
                    NumberPickerPreferenceRow(
                        title = "体重",
                        unit = "kg",
                        value = settings.weightKg.toInt().takeIf { it > 0 } ?: 60,
                        range = 30..150,
                        onValueChange = { viewModel.setWeight(it.toFloat()) },
                    )
                    NumberPickerPreferenceRow(
                        title = "年龄",
                        unit = "岁",
                        value = settings.ageYears.takeIf { it > 0 } ?: 25,
                        range = 10..100,
                        onValueChange = { viewModel.setAge(it) },
                    )
                }

                Spacer(Modifier.height(FieldToTitleSpacing))
                SmallTitle(
                    text = "性别",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                TabRow(
                    tabs = Gender.entries.map { it.label },
                    selectedTabIndex = Gender.entries.indexOf(settings.gender).coerceAtLeast(0),
                    onTabSelected = { index -> viewModel.setGender(Gender.entries[index]) },
                )

                Spacer(Modifier.height(FieldToTitleSpacing))
                SmallTitle(
                    text = "每周运动强度",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                TabRow(
                    tabs = ActivityLevel.entries.map { it.label },
                    selectedTabIndex = ActivityLevel.entries.indexOf(settings.activityLevel)
                        .coerceAtLeast(0),
                    onTabSelected = { index ->
                        viewModel.setActivityLevel(ActivityLevel.entries[index])
                    },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = settings.activityLevel.summary,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = FootnoteHorizontalPadding),
                )
            }
        }

        if (nutrients != null) {
            item {
                Spacer(Modifier.height(FieldToTitleSpacing))
                SmallTitle(
                    text = "推荐摄入",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RecommendCard(
                        label = "热量",
                        value = "${nutrients.calories.toInt()}",
                        unit = "kcal",
                        modifier = Modifier.weight(1f),
                    )
                    RecommendCard(
                        label = "蛋白质",
                        value = formatMacro(nutrients.proteinG),
                        unit = "g",
                        modifier = Modifier.weight(1f),
                    )
                    RecommendCard(
                        label = "脂肪",
                        value = formatMacro(nutrients.fatG),
                        unit = "g",
                        modifier = Modifier.weight(1f),
                    )
                    RecommendCard(
                        label = "碳水",
                        value = formatMacro(nutrients.carbsG),
                        unit = "g",
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "基于 Mifflin-St Jeor 公式与当前运动强度估算，可作为饮食计划参考",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = FootnoteHorizontalPadding),
                )

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.applyRecommendedTarget() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("写入每日热量与营养目标")
                }
            }
        } else {
            item {
                Spacer(Modifier.height(FieldToTitleSpacing))
                Text(
                    text = "填写身高、体重与年龄后，这里会显示推荐热量与营养素",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = FootnoteHorizontalPadding),
                )
            }
        }
    }
}

@Composable
private fun RecommendCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = unit,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private fun formatMacro(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)

@Composable
private fun StatusRow(
    title: String,
    summary: String,
    status: String,
    onClick: (() -> Unit)? = null,
) {
    if (onClick != null) {
        ArrowPreference(
            title = title,
            summary = summary,
            onClick = onClick,
            endActions = {
                Text(
                    text = status,
                    fontSize = 14.5.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            },
        )
    } else {
        ArrowPreference(
            title = title,
            summary = summary,
            endActions = {
                Text(
                    text = status,
                    fontSize = 14.5.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            },
        )
    }
}

@Composable
fun AppearanceSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
) {
    val settings by viewModel.settings.collectAsState()
    var showColorPicker by remember { mutableStateOf(false) }

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
    ) {
        item {
            Column {
                SmallTitle(
                    text = "颜色主题",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ColorThemePreset.entries
                            .filter { it != ColorThemePreset.CUSTOM }
                            .forEach { preset ->
                                val selected = settings.colorTheme == preset
                                val palette = FoodColorPalette.forPreset(preset).toComposeColors()
                                ArrowPreference(
                                    title = preset.label,
                                    summary = if (selected) "当前使用 · 热量、营养素和饮食结构图统一配色" else "应用这套配色",
                                    endActions = {
                                        PaletteStrip(colors = palette.previewColors, selected = selected)
                                    },
                                    onClick = { viewModel.setColorTheme(preset) },
                                )
                            }
                        ArrowPreference(
                            title = "自定义颜色",
                            summary = if (settings.colorTheme == ColorThemePreset.CUSTOM) {
                                "已根据主色自动生成整套配色"
                            } else {
                                "选择一个主色，自动生成协调的配色"
                            },
                            endActions = {
                                PaletteStrip(
                                    colors = settings.colorPalette.toComposeColors().previewColors,
                                    selected = settings.colorTheme == ColorThemePreset.CUSTOM,
                                )
                            },
                            onClick = { showColorPicker = true },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "配色会同步应用到今日页、统计页、识别结果和卡片详情中的热量/营养素进度，以及饮食结构图。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = FootnoteHorizontalPadding),
                )

                Spacer(Modifier.height(FieldToTitleSpacing))
                SmallTitle(
                    text = "视觉效果",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    SwitchPreference(
                        checked = settings.glassEffectsEnabled,
                        onCheckedChange = { viewModel.setGlassEffectsEnabled(it) },
                        title = "玻璃特效",
                        summary = "控制顶部渐变模糊等高开销特效。底栏液体玻璃始终保留",
                    )
                    SwitchPreference(
                        checked = settings.topGradientBlurEnabled && settings.glassEffectsEnabled,
                        onCheckedChange = { viewModel.setTopGradientBlurEnabled(it) },
                        title = "顶部渐变模糊",
                        summary = "顶栏下方的 miuix 渐进模糊（progressive blur），需先开启玻璃特效",
                        enabled = settings.glassEffectsEnabled,
                    )
                    InputDialogPreferenceRow(
                        title = "渐变模糊范围",
                        unit = "dp",
                        value = settings.topGradientBlurRangeDp,
                        range = 0..240,
                        summary = "控制顶部模糊从标题栏向下延伸的高度",
                        dialogTitle = "渐变模糊范围",
                        onValueChange = viewModel::setTopGradientBlurRangeDp,
                    )
                }
            }
        }
    }

    ColorPickerDialog(
        show = showColorPicker,
        selected = settings.colorSeed,
        onSelect = viewModel::setCustomColorTheme,
        onDismiss = { showColorPicker = false },
    )
}

@Composable
private fun ColorPickerDialog(
    show: Boolean,
    selected: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var hsv by remember(show, selected) { mutableStateOf(FoodPaletteGenerator.argbToHsv(selected)) }
    val selectedColor = FoodPaletteGenerator.hsvToArgb(hsv)
    val generated = remember(selectedColor) { FoodPaletteGenerator.fromSeed(selectedColor).toComposeColors() }
    AnimatedOverlayDialog(
        title = "自定义配色",
        summary = "选择主色后自动生成协调的热量、营养素和饮食结构颜色",
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SaturationValuePicker(
                hsv = hsv,
                onHsvChange = { hsv = it },
            )
            HuePicker(
                hue = hsv.hue,
                onHueChange = { hsv = hsv.copy(hue = it) },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(selectedColor)),
                )
                Column {
                    Text("主色  ${selectedColor.toHexString()}", style = MiuixTheme.textStyles.body2)
                    Text(
                        "已生成 ${generated.previewColors.size} 个应用颜色",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Text("预览", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            PaletteStrip(colors = generated.previewColors)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors()) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        onSelect(selectedColor)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("应用配色")
                }
            }
            Text(
                text = "拖动上方色板选择明度/饱和度，拖动色相条选择色相。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

private val HueColors = listOf(
    Color.Red,
    Color.Yellow,
    Color.Green,
    Color.Cyan,
    Color.Blue,
    Color.Magenta,
    Color.Red,
)

private val FoodPaletteColors.previewColors: List<Color>
    get() = listOf(calorie, protein, carbs, fat) + structure

@Composable
private fun PaletteStrip(colors: List<Color>, selected: Boolean = false) {
    Row(
        modifier = Modifier.width(96.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        colors.take(8).forEach { color ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .background(color),
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MiuixTheme.colorScheme.primary)
                    .align(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
private fun SaturationValuePicker(
    hsv: HsvColor,
    onHsvChange: (HsvColor) -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints {
        val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 180.dp.toPx() }
        fun update(offset: Offset) {
            onHsvChange(
                hsv.copy(
                    saturation = (offset.x / widthPx).coerceIn(0f, 1f),
                    value = (1f - offset.y / heightPx).coerceIn(0f, 1f),
                ),
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                .pointerInput(widthPx, heightPx, hsv.hue) {
                    detectTapGestures(onTap = ::update)
                }
                .pointerInput(widthPx, heightPx, hsv.hue) {
                    detectDragGestures(
                        onDragStart = ::update,
                        onDrag = { change, _ -> update(change.position) },
                    )
                },
        ) {
            val hueColor = Color(FoodPaletteGenerator.hsvToArgb(HsvColor(hsv.hue, 1f, 1f)))
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val x = hsv.saturation * size.width
            val y = (1f - hsv.value) * size.height
            drawCircle(Color.Black, radius = 9.dp.toPx(), center = Offset(x, y))
            drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun HuePicker(
    hue: Float,
    onHueChange: (Float) -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints {
        val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 28.dp.toPx() }
        fun update(offset: Offset) {
            onHueChange((offset.x / widthPx * 360f).coerceIn(0f, 360f))
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                .pointerInput(widthPx, heightPx) {
                    detectTapGestures(onTap = ::update)
                }
                .pointerInput(widthPx, heightPx) {
                    detectDragGestures(
                        onDragStart = ::update,
                        onDrag = { change, _ -> update(change.position) },
                    )
                },
        ) {
            drawRect(Brush.horizontalGradient(HueColors))
            val x = hue / 360f * size.width
            drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(x, size.height / 2f))
            drawCircle(Color.Black, radius = 7.dp.toPx(), center = Offset(x, size.height / 2f))
        }
    }
}

private fun Long.toHexString(): String =
    "#%06X".format(java.util.Locale.ROOT, this and 0xFFFFFF)

@Composable
fun AboutScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
) {
    val context = LocalContext.current
    val packageInfo = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "—"
    val versionCode = packageInfo?.longVersionCode?.toString() ?: "—"
    val githubUrl = "https://github.com/Cclicking/lightmemo"
    val acknowledgements = remember {
        listOf(
            Acknowledgement(
                name = "miuix",
                author = "compose-miuix-ui",
                url = "https://github.com/compose-miuix-ui/miuix",
            ),
            Acknowledgement(
                name = "miuix-glass",
                author = "lingqiqi5211",
                url = "https://github.com/compose-miuix-ui/miuix/pull/423",
            ),
            Acknowledgement(
                name = "中国食物成分表",
                author = "Sanotsu",
                url = "https://github.com/Sanotsu/china-food-composition-data",
            ),
            Acknowledgement(
                name = "USDA FoodData Central",
                author = "USDA",
                url = "https://fdc.nal.usda.gov/download-datasets/",
            ),
            Acknowledgement(
                name = "AndroidLiquidGlass",
                author = "Kyant0",
                url = "https://github.com/Kyant0/AndroidLiquidGlass",
            ),
        )
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
    ) {
        item {
            Column {
                SmallTitle(
                    text = "应用信息",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        StatusRow(
                            title = "应用名称",
                            summary = "热量记录与营养识别",
                            status = "LightMemo · 轻食记",
                        )
                        StatusRow(
                            title = "版本",
                            summary = "versionName / versionCode",
                            status = "$versionName ($versionCode)",
                        )
                        StatusRow(
                            title = "包名",
                            summary = "applicationId",
                            status = context.packageName,
                        )
                    }
                }

                Spacer(Modifier.height(FieldToTitleSpacing))
                SmallTitle(
                    text = "开源",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        StatusRow(
                            title = "GitHub",
                            summary = "Cclicking/lightmemo",
                            status = "打开",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(githubUrl),
                                        )
                                    )
                                }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(FieldToTitleSpacing))
                SmallTitle(
                    text = "鸣谢",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        acknowledgements.forEachIndexed { index, acknowledgement ->
                            if (index > 0) {
                                Spacer(Modifier.height(1.dp))
                            }
                            AcknowledgementRow(
                                acknowledgement = acknowledgement,
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(acknowledgement.url),
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class Acknowledgement(
    val name: String,
    val author: String,
    val url: String,
)

@Composable
private fun AcknowledgementRow(
    acknowledgement: Acknowledgement,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = acknowledgement.name,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = acknowledgement.author,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
