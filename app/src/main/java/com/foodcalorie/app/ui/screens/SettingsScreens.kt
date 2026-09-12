package com.foodcalorie.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcalorie.app.FoodApp
import com.foodcalorie.app.data.ActivityLevel
import com.foodcalorie.app.data.Gender
import com.foodcalorie.app.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.foodcalorie.app.ui.components.AnimatedOverlayDialog
import com.foodcalorie.app.ui.components.DropdownPref
import com.foodcalorie.app.ui.utils.overScrollVertical
import com.foodcalorie.app.viewmodel.SettingsViewModel
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
    val presets = settings.apiPresets
    val activeIndex = presets.indexOfFirst { it.id == settings.activePresetId }.coerceAtLeast(0)

    var presetName by remember(settings.activePresetId) { mutableStateOf(settings.activePreset.name) }
    var baseUrl by remember(settings.activePresetId) { mutableStateOf(settings.baseUrl) }
    var apiKey by remember(settings.activePresetId) { mutableStateOf(settings.apiKey) }
    var model by remember(settings.activePresetId) { mutableStateOf(settings.model) }
    var systemBackground by remember(settings.activePresetId) {
        mutableStateOf(settings.systemBackground)
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
                if (!settings.isRecognitionConfigured) {
                    Text(
                        text = "尚未配置识别服务，填写 Base URL 与 API Key 后才能拍照识别",
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                SmallTitle(
                    text = "模型配置",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    DropdownPref(
                        title = "当前配置",
                        summary = "${settings.model.ifBlank { "未命名" }} · 可保存多套并切换",
                        items = presets.map { it.name },
                        selectedIndex = activeIndex,
                        onSelectedIndexChange = { index ->
                            presets.getOrNull(index)?.let { viewModel.selectPreset(it.id) }
                        },
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { viewModel.addPreset("配置 ${presets.size + 1}") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("新增配置")
                    }
                    Button(
                        onClick = { viewModel.deleteActivePreset() },
                        modifier = Modifier.weight(1f),
                        enabled = presets.size > 1,
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Text("删除当前")
                    }
                }

                Spacer(Modifier.height(FieldToTitleSpacing))
                SmallTitle(
                    text = "配置名称",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                TextField(
                    value = presetName,
                    onValueChange = { value ->
                        presetName = value
                        viewModel.renamePreset(settings.activePreset.id, value)
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
                        viewModel.setBaseUrl(it)
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
                        viewModel.setApiKey(it)
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
                        viewModel.setModel(it)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(FieldToTitleSpacing))
                SmallTitle(
                    text = "大模型背景信息",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                TextField(
                    value = systemBackground,
                    onValueChange = {
                        systemBackground = it
                        viewModel.setSystemBackground(it)
                    },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "可填写年龄、饮食偏好、过敏原、目标（如减脂）等，识别时会作为参考上下文发给大模型",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = FootnoteHorizontalPadding),
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "当前：${settings.baseUrl} · ${settings.model}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = FootnoteHorizontalPadding),
                )
            }
        }
    }
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
fun DatabaseSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    var fdcApiKey by remember(settings.foodDataCentralApiKey) {
        mutableStateOf(settings.foodDataCentralApiKey)
    }
    val offlineStatus = remember {
        runCatching {
            (context.applicationContext as FoodApp).nutritionDatabase.offlineStatus()
        }.getOrNull()
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
                    text = "连接情况",
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
                            title = "USDA 离线库",
                            summary = "本地 SR Legacy 宏量营养素库",
                            status = when {
                                offlineStatus == null -> "检测中"
                                offlineStatus.usdaAvailable -> "已就绪"
                                else -> "不可用"
                            },
                        )
                        StatusRow(
                            title = "中国食物成分表",
                            summary = "本地第 6 版离线库",
                            status = when {
                                offlineStatus == null -> "检测中"
                                offlineStatus.chinaAvailable -> "已就绪"
                                else -> "不可用"
                            },
                        )
                        StatusRow(
                            title = "USDA 在线 API",
                            summary = "离线未命中时的可选补充",
                            status = if (settings.foodDataCentralApiKey.isNotBlank()) "已配置" else "未配置",
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "查询顺序：USDA 离线库 → USDA 在线 API → 中国食物成分离线库。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = FootnoteHorizontalPadding),
                )

                Spacer(Modifier.height(FieldToTitleSpacing))
                SmallTitle(
                    text = "USDA FoodData Central API Key",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                TextField(
                    value = fdcApiKey,
                    onValueChange = {
                        fdcApiKey = it
                        viewModel.setFoodDataCentralApiKey(it)
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "可留空。填写后仅在本地库未命中时调用在线检索。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = FootnoteHorizontalPadding),
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    title: String,
    summary: String,
    status: String,
) {
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

private val RingColorPalette = listOf(
    0xFFF3A17C,
    0xFF2F7D2B,
    0xFFFFB300,
    0xFF8EAEFF,
    0xFFE85D75,
    0xFF9B6DFF,
    0xFF2BB3A3,
    0xFF6B7280,
)

@Composable
fun AppearanceSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
) {
    val settings by viewModel.settings.collectAsState()
    var editing by remember { mutableStateOf<RingSlot?>(null) }

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
                    text = "今日页圆环颜色",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        RingColorRow(
                            title = "蛋白质",
                            color = Color(settings.proteinRingColor),
                            onClick = { editing = RingSlot.PROTEIN },
                        )
                        RingColorRow(
                            title = "碳水",
                            color = Color(settings.carbsRingColor),
                            onClick = { editing = RingSlot.CARBS },
                        )
                        RingColorRow(
                            title = "脂肪",
                            color = Color(settings.fatRingColor),
                            onClick = { editing = RingSlot.FAT },
                        )
                    }
                }

                Spacer(Modifier.height(FieldToTitleSpacing))
                SmallTitle(
                    text = "顶部效果",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    SwitchPreference(
                        checked = settings.topGradientBlurEnabled,
                        onCheckedChange = { viewModel.setTopGradientBlurEnabled(it) },
                        title = "顶部渐变模糊",
                        summary = "顶栏下方的 miuix 渐进模糊（progressive blur）",
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

    val slot = editing
    val current = when (slot) {
            RingSlot.PROTEIN -> settings.proteinRingColor
            RingSlot.CARBS -> settings.carbsRingColor
            RingSlot.FAT -> settings.fatRingColor
            null -> settings.proteinRingColor
        }
    ColorPickerDialog(
        show = slot != null,
        title = when (slot) {
            RingSlot.PROTEIN -> "蛋白质圆环颜色"
            RingSlot.CARBS -> "碳水圆环颜色"
            RingSlot.FAT -> "脂肪圆环颜色"
            null -> "选择颜色"
        },
        selected = current,
        onSelect = { color ->
            when (slot) {
                RingSlot.PROTEIN -> viewModel.setProteinRingColor(color)
                RingSlot.CARBS -> viewModel.setCarbsRingColor(color)
                RingSlot.FAT -> viewModel.setFatRingColor(color)
                null -> Unit
            }
        },
        onDismiss = { editing = null },
    )
}

private enum class RingSlot { PROTEIN, CARBS, FAT }

@Composable
private fun RingColorRow(
    title: String,
    color: Color,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        endActions = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(color),
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun ColorPickerDialog(
    show: Boolean,
    title: String,
    selected: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedOverlayDialog(
        title = title,
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            RingColorPalette.chunked(4).forEachIndexed { rowIndex, chunk ->
                if (rowIndex > 0) Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    chunk.forEach { color ->
                        val selectedHere = color == selected
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .background(Color(color))
                                .then(
                                    if (selectedHere) {
                                        Modifier.padding(2.dp)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable {
                                    onSelect(color)
                                    onDismiss()
                                },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "选择一种颜色后立即生效",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

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
                            status = "Food Calorie",
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
                    text = "说明",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                Card(
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Text(
                        text = "本应用用于拍照识别食物、记录一餐热量与宏量营养素，并提供离线营养库与可选在线 API 查询。",
                        style = MiuixTheme.textStyles.body1,
                    )
                }
            }
        }
    }
}
