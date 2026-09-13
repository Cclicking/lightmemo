package com.click.lightmemo.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.click.lightmemo.network.RecognitionPrompt
import com.click.lightmemo.ui.basic.SharedScrollBehavior
import com.click.lightmemo.ui.components.DropdownPref
import com.click.lightmemo.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PromptSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: SharedScrollBehavior?,
    listState: LazyListState,
) {
    val settings by viewModel.settings.collectAsState()
    val ready by viewModel.settingsReady.collectAsState()
    val testing by viewModel.testingPrompt.collectAsState()
    val result by viewModel.promptTestResult.collectAsState()
    val response by viewModel.promptTestResponse.collectAsState()
    val error by viewModel.error.collectAsState()
    if (!ready) return
    var selected by rememberSaveable { mutableStateOf(RecognitionPrompt.IMAGE.name) }
    val kind = RecognitionPrompt.valueOf(selected)
    // Keep drafts for all types across switching, rotation and failed tests.
    var drafts by rememberSaveable { mutableStateOf(HashMap<String, String>()) }
    val effective = kind.resolve(settings.promptOverrides)
    val draft = drafts[kind.name] ?: effective
    var background by rememberSaveable { mutableStateOf(settings.systemBackground) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) photo = uri.toString()
    }
    AiSettingsList(contentPadding, scrollBehavior, listState) {
        item {
            SmallTitle("大模型背景信息", modifier = Modifier.offset(x = (-16).dp))
            TextField(
                value = background,
                onValueChange = { background = it; viewModel.setSystemBackground(it) },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "可填写饮食偏好、过敏原等，修改后自动保存。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(12.dp),
            )
            SmallTitle("Prompt", modifier = Modifier.offset(x = (-16).dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                DropdownPref(
                    title = "提示词类型",
                    items = RecognitionPrompt.entries.map { it.label },
                    selectedIndex = kind.ordinal,
                    enabled = !testing,
                    onSelectedIndexChange = { if (!testing) selected = RecognitionPrompt.entries[it].name },
                    summary = if (settings.promptOverrides.containsKey(kind.name)) "当前使用已验证的自定义版本" else "当前使用默认版本",
                )
            }
            Spacer(Modifier.height(12.dp))
            TextField(
                value = draft,
                onValueChange = { value -> drafts = HashMap(drafts).apply { put(kind.name, value) } },
                enabled = !testing,
                minLines = 12,
                maxLines = 18,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "修改后点击“测试并应用”，通过后用于后续识别。测试失败继续使用上次有效版本；请保留 JSON 字段和重量规则。测试会调用当前模型接口，可能产生费用。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(12.dp),
            )
            if (kind.requiresImage) {
                Button(
                    onClick = { pickPhoto.launch("image/*") },
                    enabled = !testing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (photo == null) "选择测试食物照片" else "已选择照片 · 点击更换") }
                Spacer(Modifier.height(12.dp))
            }
            Button(
                onClick = { viewModel.testAndApplyPrompt(kind, draft, photo?.let(Uri::parse)) },
                enabled = !testing && draft.isNotBlank() && settings.isRecognitionConfigured && (!kind.requiresImage || photo != null),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(if (testing) "正在测试…" else "测试并应用") }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    drafts = HashMap(drafts).apply { remove(kind.name) }
                    viewModel.restorePrompt(kind)
                },
                enabled = !testing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("恢复默认 Prompt") }
            if (!settings.isRecognitionConfigured) {
                Text(
                    text = "尚未配置识别服务，请先返回 AI 设置添加配置。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            result?.let { Text(it, modifier = Modifier.padding(12.dp)) }
            response?.let {
                SmallTitle("测试返回结果", modifier = Modifier.offset(x = (-16).dp))
                TextField(value = it, onValueChange = {}, readOnly = true, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
            }
            error?.let { Text(it, modifier = Modifier.padding(12.dp)) }
        }
    }
}
