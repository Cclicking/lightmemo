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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.foodcalorie.app.ui.components.GlassCard
import com.foodcalorie.app.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    backdrop: Backdrop? = null,
) {
    val settings by viewModel.settings.collectAsState()

    var baseUrl by remember { mutableStateOf(settings.baseUrl) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.model) }
    var target by remember { mutableStateOf(settings.dailyCalorieTarget.toInt().toString()) }

    LaunchedEffect(settings) {
        baseUrl = settings.baseUrl
        apiKey = settings.apiKey
        model = settings.model
        target = settings.dailyCalorieTarget.toInt().toString()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(text = "设置", style = MiuixTheme.textStyles.title2)
        }

        item {
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("识别 API", style = MiuixTheme.textStyles.title4)
                    if (!settings.isRecognitionConfigured) {
                        Text(
                            text = "填写 Base URL 与 API Key 后才能拍照识别",
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
                }
            }
        }

        item {
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("每日目标", style = MiuixTheme.textStyles.title4)
                    TextField(
                        value = target,
                        onValueChange = { value ->
                            target = value
                            value.toFloatOrNull()?.let { viewModel.setTarget(it) }
                        },
                        label = "热量目标 (kcal)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "当前 API：${settings.baseUrl} · ${settings.model}",
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}
