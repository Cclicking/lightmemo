package com.foodcalorie.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.foodcalorie.app.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun ApiSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
) {
    val settings by viewModel.settings.collectAsState()
    var baseUrl by remember { mutableStateOf(settings.baseUrl) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.model) }

    LaunchedEffect(settings) {
        baseUrl = settings.baseUrl
        apiKey = settings.apiKey
        model = settings.model
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
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SmallTitle(text = "服务配置")
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!settings.isRecognitionConfigured) {
                        BasicComponent(
                            title = "尚未配置识别服务",
                            summary = "填写 Base URL 与 API Key 后才能拍照识别",
                        )
                    }
                    TextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            viewModel.setBaseUrl(it)
                        },
                        label = "Base URL",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            viewModel.setApiKey(it)
                        },
                        label = "API Key",
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = model,
                        onValueChange = {
                            model = it
                            viewModel.setModel(it)
                        },
                        label = "模型名",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "当前：${settings.baseUrl} · ${settings.model}",
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
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
        target = settings.dailyCalorieTarget.toInt().toString()
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
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SmallTitle(text = "目标设定")
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicComponent(
                        title = "每日热量目标",
                        summary = "用于进度条与达标统计",
                    )
                    TextField(
                        value = target,
                        onValueChange = { value ->
                            target = value
                            value.toFloatOrNull()?.let { viewModel.setTarget(it) }
                        },
                        label = "kcal / 天",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "默认 1800，可按个人情况调整",
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}
