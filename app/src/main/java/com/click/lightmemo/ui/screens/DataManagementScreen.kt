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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.click.lightmemo.FoodApp
import com.click.lightmemo.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.BackupViewModel
import com.click.lightmemo.viewmodel.SettingsViewModel
import android.content.Intent
import com.click.lightmemo.ui.secondary.PersonalFoodMemoryActivity
import com.click.lightmemo.data.NutritionDatabase
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.BasicComponent
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
    var showFdcApiDialog by remember { mutableStateOf(false) }
    var showDatabaseDetails by remember { mutableStateOf(false) }
    var fdcApiKey by remember(settings.foodDataCentralApiKey) {
        mutableStateOf(settings.foodDataCentralApiKey)
    }
    var fdcApiDraft by remember { mutableStateOf("") }
    val offlineStatus = remember {
        runCatching {
            (context.applicationContext as FoodApp).nutritionDatabase.offlineStatus()
        }.getOrNull()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(backupViewModel::export) }
    val exportFoodDataLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(backupViewModel::exportFoodData) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(backupViewModel::import) }


    LaunchedEffect(backupMessage) {
        backupMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    AnimatedOverlayDialog(
        show = showFdcApiDialog,
        title = "USDA FoodData Central API",
        summary = "可留空；仅在本地库未命中时调用在线检索。",
        onDismissRequest = { showFdcApiDialog = false },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = fdcApiDraft,
                onValueChange = { fdcApiDraft = it },
                label = "API Key",
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { showFdcApiDialog = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        fdcApiKey = fdcApiDraft
                        viewModel.setFoodDataCentralApiKey(fdcApiDraft)
                        showFdcApiDialog = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("保存")
                }
            }
        }
    }

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

    AnimatedOverlayDialog(
        show = showDatabaseDetails,
        title = "数据源详情",
        onDismissRequest = { showDatabaseDetails = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                BasicComponent(title = "USDA SR Legacy", summary = "本地 SR Legacy 宏量营养素库")
                BasicComponent(title = "USDA Foundation", summary = "2026-04 基础食物离线库")
                BasicComponent(title = "USDA FNDDS", summary = "2021–2023 食物与饮料离线库")
                BasicComponent(title = "中国食物成分表", summary = "第 6 版离线库")
                BasicComponent(title = "USDA 在线 API", summary = "离线未命中时补充检索，需要 API Key")
            }
            Button(onClick = { showDatabaseDetails = false }, modifier = Modifier.fillMaxWidth()) { Text("完成") }
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
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                ArrowPreference(
                    title = "个人食物记忆",
                    onClick = { context.startActivity(Intent(context, PersonalFoodMemoryActivity::class.java)) },
                )
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            SmallTitle(
                text = "营养数据源",
                modifier = Modifier.offset(x = (-16).dp),
            )
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "USDA SR Legacy",
                        summary = if (offlineStatus?.srLegacyAvailable == false) "离线库不可用" else null,
                        checked = settings.nutritionDatabases.enabled(NutritionDatabase.SR_LEGACY),
                        onCheckedChange = { viewModel.setNutritionDatabase(NutritionDatabase.SR_LEGACY, it) },
                    )
                    SwitchPreference(
                        title = "USDA Foundation",
                        summary = if (offlineStatus?.foundationAvailable == false) "离线库不可用" else null,
                        checked = settings.nutritionDatabases.enabled(NutritionDatabase.FOUNDATION),
                        onCheckedChange = { viewModel.setNutritionDatabase(NutritionDatabase.FOUNDATION, it) },
                    )
                    SwitchPreference(
                        title = "USDA FNDDS",
                        summary = if (offlineStatus?.fnddsAvailable == false) "离线库不可用" else null,
                        checked = settings.nutritionDatabases.enabled(NutritionDatabase.FNDDS),
                        onCheckedChange = { viewModel.setNutritionDatabase(NutritionDatabase.FNDDS, it) },
                    )
                    SwitchPreference(
                        title = "中国食物成分表",
                        summary = if (offlineStatus?.chinaAvailable == false) "离线库不可用" else null,
                        checked = settings.nutritionDatabases.enabled(NutritionDatabase.CHINA),
                        onCheckedChange = { viewModel.setNutritionDatabase(NutritionDatabase.CHINA, it) },
                    )
                    SwitchPreference(
                        title = "USDA 在线 API",
                        summary = if (settings.foodDataCentralApiKey.isBlank() && settings.nutritionDatabases.online) "需要 API Key" else null,
                        checked = settings.nutritionDatabases.enabled(NutritionDatabase.ONLINE),
                        onCheckedChange = { viewModel.setNutritionDatabase(NutritionDatabase.ONLINE, it) },
                    )
                    ArrowPreference(title = "数据源详情", onClick = { showDatabaseDetails = true })
                }
            }
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
                insideMargin = PaddingValues(0.dp),
            ) {
                ArrowPreference(
                    title = "USDA FoodData Central API",
                    summary = if (fdcApiKey.isBlank()) "未配置" else null,
                    onClick = {
                        fdcApiDraft = fdcApiKey
                        showFdcApiDialog = true
                    },
                )
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            SmallTitle(
                text = "数据备份",
                modifier = Modifier.offset(x = (-16).dp),
            )
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "导出全部数据",
                        summary = "记录、预设、个人信息、目标、模型/数据库配置和主题色",
                        enabled = !backupBusy,
                        onClick = { exportLauncher.launch("轻食记-完整备份-${LocalDate.now()}.json") },
                    )
                    ArrowPreference(
                        title = "导入数据备份",
                        summary = "恢复设置与预设食物，并合并饮食记录",
                        enabled = !backupBusy,
                        onClick = {
                            importLauncher.launch(
                                arrayOf("application/json", "text/plain", "application/octet-stream"),
                            )
                        },
                    )
                    ArrowPreference(
                        title = "导出食物数据",
                        summary = "仅导出饮食记录及营养数据，便于 AI 分析，不含配置和个人信息",
                        enabled = !backupBusy,
                        onClick = { exportFoodDataLauncher.launch("轻食记-食物数据-${LocalDate.now()}.json") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "完整备份包含模型和数据库 API Key，请妥善保存；导出食物数据仅包含饮食记录。两种导出均不包含照片。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
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
                    enabled = !backupBusy,
                    onClick = { showReset = true },
                )
            }
            if (backupBusy) {
                Text("正在处理，请稍候…")
            }
        }
    }
}
