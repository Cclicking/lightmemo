package com.click.lightmemo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.lightmemo.FoodApp
import com.click.lightmemo.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.BackupViewModel
import com.click.lightmemo.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate

@Composable
fun DataManagementScreen(
    viewModel: SettingsViewModel,
    backupViewModel: BackupViewModel,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val backupBusy by backupViewModel.busy.collectAsState()
    val backupMessage by backupViewModel.message.collectAsState()
    var showReset by remember { mutableStateOf(false) }
    var fdcApiKey by remember(settings.foodDataCentralApiKey) {
        mutableStateOf(settings.foodDataCentralApiKey)
    }
    val offlineStatus = remember {
        runCatching {
            (context.applicationContext as FoodApp).nutritionDatabase.offlineStatus()
        }.getOrNull()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(backupViewModel::export) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(backupViewModel::import) }

    AnimatedOverlayDialog(
        show = showReset,
        title = "恢复食物预设？",
        summary = "自定义名称、重量和营养将恢复为初始值，饮食记录不受影响。",
        onDismissRequest = { showReset = false },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = { showReset = false },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text("取消")
            }
            Button(
                onClick = {
                    backupViewModel.resetPresets()
                    showReset = false
                },
                enabled = !backupBusy,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("恢复默认")
            }
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SmallTitle(
                text = "连接情况",
                modifier = Modifier.offset(x = (-16).dp),
            )
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
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item {
            Spacer(Modifier.height(4.dp))
            SmallTitle(
                text = "数据库 API",
                modifier = Modifier.offset(x = (-16).dp),
            )
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = fdcApiKey,
                        onValueChange = {
                            fdcApiKey = it
                            viewModel.setFoodDataCentralApiKey(it)
                        },
                        label = "USDA FoodData Central API Key",
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "可留空。填写后仅在本地库未命中时调用在线检索。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            SmallTitle(
                text = "记录备份",
                modifier = Modifier.offset(x = (-16).dp),
            )
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "导出饮食记录",
                        summary = "保存为备份文件，不含照片和 API 密钥",
                        enabled = !backupBusy,
                        onClick = { exportLauncher.launch("轻食记-${LocalDate.now()}.json") },
                    )
                    ArrowPreference(
                        title = "导入饮食记录",
                        summary = "合并备份并跳过重复记录，保留现有数据",
                        enabled = !backupBusy,
                        onClick = {
                            importLauncher.launch(
                                arrayOf("application/json", "text/plain", "application/octet-stream"),
                            )
                        },
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            SmallTitle(
                text = "预设食物",
                modifier = Modifier.offset(x = (-16).dp),
            )
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                ArrowPreference(
                    title = "恢复默认食物预设",
                    summary = "恢复常用食物的初始名称、重量和营养",
                    enabled = !backupBusy,
                    onClick = { showReset = true },
                )
            }
            if (backupBusy) {
                Text("正在处理，请稍候…")
            }
            backupMessage?.let { Text(it) }
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
