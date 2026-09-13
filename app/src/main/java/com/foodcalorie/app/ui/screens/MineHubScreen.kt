package com.foodcalorie.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcalorie.app.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.foodcalorie.app.ui.utils.overScrollVertical
import com.foodcalorie.app.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MineHubScreen(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior?,
    listState: LazyListState,
    viewModel: SettingsViewModel,
    onApi: () -> Unit,
    onTarget: () -> Unit,
    onProfile: () -> Unit,
    onDataManagement: () -> Unit,
    onDatabase: () -> Unit,
    onAppearance: () -> Unit,
    onAbout: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()

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
                text = "基本设置",
                modifier = Modifier.offset(x = (-16).dp),
            )
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "个人信息",
                        summary = "身高体重、性别与运动强度",
                        endActions = {
                            Text(
                                text = if (settings.hasPersonalProfile) "已填写" else "未填写",
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        },
                        onClick = onProfile,
                    )
                    ArrowPreference(
                        title = "每日目标",
                        summary = "热量目标 (kcal)",
                        endActions = {
                            Text(
                                text = "${settings.dailyCalorieTarget.toInt()}",
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        },
                        onClick = onTarget,
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            SmallTitle(
                text = "数据管理",
                modifier = Modifier.offset(x = (-16).dp),
            )
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "AI设置",
                        summary = "Base URL、API Key 与模型",
                        endActions = {
                            Text(
                                text = if (settings.isRecognitionConfigured) {
                                    settings.model.ifBlank { "已配置" }
                                } else {
                                    "未配置"
                                },
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        },
                        onClick = onApi,
                    )
                    ArrowPreference(
                        title = "数据管理",
                        summary = "数据库 API、导入导出与预设恢复",
                        endActions = {
                            Text(
                                text = if (settings.foodDataCentralApiKey.isNotBlank()) "在线已配置" else "离线优先",
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        },
                        onClick = onDataManagement,
                    )
                    ArrowPreference(
                        title = "数据库浏览",
                        summary = "浏览离线食物，点击查看营养素",
                        onClick = onDatabase,
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            SmallTitle(
                text = "其他",
                modifier = Modifier.offset(x = (-16).dp),
            )
            Card(
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "个性化设置",
                        summary = "今日页圆环颜色与顶部渐变",
                        onClick = onAppearance,
                    )
                    ArrowPreference(
                        title = "关于",
                        summary = "版本与应用信息",
                        onClick = onAbout,
                    )
                }
            }
        }
    }
}
