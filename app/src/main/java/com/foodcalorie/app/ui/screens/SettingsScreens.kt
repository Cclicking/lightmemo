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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcalorie.app.data.ActivityLevel
import com.foodcalorie.app.data.Gender
import com.foodcalorie.app.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.foodcalorie.app.ui.utils.overScrollVertical
import com.foodcalorie.app.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** SmallTitle 与下方输入框之间的间距（与「我的」页 SpacedBy 一致） */
private val TitleToFieldSpacing = 12.dp

/** 输入框与下一个 SmallTitle 之间的间距（原 20dp，缩小 3dp） */
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
                    OverlayDropdownPreference(
                        title = "当前配置",
                        summary = "${settings.model.ifBlank { "未命名" }} · 可保存多套并切换",
                        items = presets.map { it.name },
                        selectedIndex = activeIndex,
                        onSelectedIndexChange = { index ->
                            presets.getOrNull(index)?.let { viewModel.selectPreset(it.id) }
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { viewModel.addPreset("配置 ${presets.size + 1}") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(
                            color = Color(0xFF0A84FF),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("新增配置")
                    }
                    Button(
                        onClick = { viewModel.deleteActivePreset() },
                        modifier = Modifier.weight(1f),
                        enabled = presets.size > 1,
                        colors = ButtonDefaults.buttonColors(
                            color = Color(0xFFFFEBEE),
                            contentColor = Color(0xFFE53935),
                        ),
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
                    colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.White),
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
                    colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.White),
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
                    colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.White),
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
                    colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.White),
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
                    colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.White),
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
    var target by remember { mutableStateOf(settings.dailyCalorieTarget.toInt().toString()) }

    LaunchedEffect(settings.dailyCalorieTarget) {
        val next = settings.dailyCalorieTarget.toInt().toString()
        if (target.toFloatOrNull()?.toInt()?.toString() != next) {
            target = next
        }
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
                    text = "每日热量目标",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                TextField(
                    value = target,
                    onValueChange = { value ->
                        target = value
                        val n = value.toFloatOrNull()
                        if (n != null && n in 500f..10000f) {
                            viewModel.setTarget(n)
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "用于进度条与达标统计，默认 1800 kcal / 天，可按个人情况调整",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = FootnoteHorizontalPadding),
                )
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

    // 本地字符串：用户编辑后不再被 settings 回写，避免输入中途被 clamp 成错误数字
    var height by remember { mutableStateOf(formatNumber(settings.heightCm)) }
    var weight by remember { mutableStateOf(formatNumber(settings.weightKg)) }
    var age by remember {
        mutableStateOf(if (settings.ageYears > 0) settings.ageYears.toString() else "")
    }
    var heightTouched by remember { mutableStateOf(false) }
    var weightTouched by remember { mutableStateOf(false) }
    var ageTouched by remember { mutableStateOf(false) }

    LaunchedEffect(settings.heightCm) {
        if (!heightTouched) height = formatNumber(settings.heightCm)
    }
    LaunchedEffect(settings.weightKg) {
        if (!weightTouched) weight = formatNumber(settings.weightKg)
    }
    LaunchedEffect(settings.ageYears) {
        if (!ageTouched) {
            age = if (settings.ageYears > 0) settings.ageYears.toString() else ""
        }
    }

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
                    text = "身高 (cm)",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                TextField(
                    value = height,
                    onValueChange = { value ->
                        heightTouched = true
                        height = value
                        val n = value.toFloatOrNull()
                        if (n != null && n in 50f..250f) {
                            viewModel.setHeight(n)
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(FieldToTitleSpacing))
                SmallTitle(
                    text = "体重 (kg)",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                TextField(
                    value = weight,
                    onValueChange = { value ->
                        weightTouched = true
                        weight = value
                        val n = value.toFloatOrNull()
                        if (n != null && n in 20f..300f) {
                            viewModel.setWeight(n)
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(FieldToTitleSpacing))
                SmallTitle(
                    text = "年龄",
                    modifier = Modifier.offset(x = (-16).dp),
                )
                Spacer(Modifier.height(TitleToFieldSpacing))
                TextField(
                    value = age,
                    onValueChange = { value ->
                        ageTouched = true
                        age = value
                        val n = value.toIntOrNull()
                        if (n != null && n in 10..100) {
                            viewModel.setAge(n)
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

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
                    enabled = settings.dailyCalorieTarget.toInt() != nutrients.calories.toInt(),
                    colors = ButtonDefaults.buttonColorsPrimary(
                        color = Color(0xFF0A84FF),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("写入每日热量目标")
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

private fun formatNumber(value: Float): String =
    if (value <= 0f) "" else if (value % 1f == 0f) value.toInt().toString() else value.toString()

private fun formatMacro(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)
