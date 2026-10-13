package com.click.lightmemo.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.click.lightmemo.mcp.BackgroundRunSettings
import com.click.lightmemo.ui.basic.SharedScrollBehavior
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.McpViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun McpSettingsScreen(viewModel: McpViewModel, contentPadding: PaddingValues,
    scrollBehavior: SharedScrollBehavior?, listState: LazyListState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var batteryExempt by remember(context) {
        mutableStateOf(BackgroundRunSettings.ignoresBatteryOptimizations(context))
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryExempt = BackgroundRunSettings.ignoresBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val state by viewModel.state.collectAsState()
    val check by viewModel.check.collectAsState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setEnabled(true)
        else Toast.makeText(context, "请允许通知，以便查看运行状态和随时停止服务。", Toast.LENGTH_LONG).show()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().overScrollVertical().then(
            if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SmallTitle("连接", modifier = Modifier.offset(x = (-16).dp))
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(0.dp)) {
                SwitchPreference(
                    title = "MCP 服务",
                    summary = "允许助手查询营养、发起文字录入识别",
                    checked = state.running,
                    enabled = !state.starting,
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else viewModel.setEnabled(enabled)
                    },
                )
                BasicComponent(title = "运行状态", summary = state.error ?: when {
                    state.starting -> "正在开启…"
                    state.running -> "服务已开启，等待助手连接"
                    else -> "服务已关闭"
                })
            }
        }
        item {
            SmallTitle("连接设置", modifier = Modifier.offset(x = (-16).dp))
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(0.dp)) {
                ArrowPreference(title = "复制连接地址", summary = "仅供本机连接 · 127.0.0.1:8765", enabled = state.running,
                    onClick = {
                        val clip = ClipData.newPlainText("轻食记 MCP 连接地址", state.address)
                        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                        Toast.makeText(context, "连接地址已复制", Toast.LENGTH_SHORT).show()
                    })
                ArrowPreference(title = "检查连接", summary = if (check.checking && state.running) "正在检查…" else check.message?.takeIf { state.running } ?: "检查服务连接和营养查询是否可用",
                    enabled = state.running && !check.checking, onClick = viewModel::checkConnection)
            }
        }
        item {
            SmallTitle("后台运行", modifier = Modifier.offset(x = (-16).dp))
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(0.dp)) {
                ArrowPreference(title = "忽略电池优化",
                    summary = if (batteryExempt) "已允许 · 减少待机时的系统限制" else "未允许 · 点击申请，保持待机时可连接",
                    onClick = { BackgroundRunSettings.openBatteryOptimization(context) })
                ArrowPreference(title = "允许自启动", summary = "在系统设置中允许轻食记自启动或后台自动启动",
                    onClick = { BackgroundRunSettings.openAutostart(context) })
                ArrowPreference(title = "后台省电策略", summary = "建议选择无限制，或允许后台活动",
                    onClick = { BackgroundRunSettings.openBackgroundBattery(context) })
            }
            Text("后台运行可能增加耗电。自启动和省电策略请在系统中确认；也可在最近任务中锁定轻食记，减少清理时被关闭。强行停止或重启手机后，需重新开启 MCP 服务并更新连接地址。",
                style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        item {
            SmallTitle("使用方法", modifier = Modifier.offset(x = (-16).dp))
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(0.dp)) {
                BasicComponent(title = "连接超级小爱", summary = "开启服务后复制连接地址，在小爱的 MCP 服务设置中粘贴完整地址。随后可询问：查询轻食记里今天的营养摄入。")
                BasicComponent(title = "可查询的数据", summary = "今天已记录的热量、蛋白质、碳水化合物和脂肪。查询不会修改或删除饮食记录。")
                BasicComponent(title = "用语音记录饮食", summary = "告诉小爱你吃了什么，描述会进入轻食记原有文字识别流程，并显示实时识别通知。识别完成后，打开结果核对并保存。")
            }
        }
        item {
            Text("连接地址包含访问凭证，请仅提供给你信任的助手。查询结果会交给助手处理。关闭服务即撤销访问；重新开启后需更新连接地址。系统结束服务后，请回到此页面重新开启。",
                style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}
