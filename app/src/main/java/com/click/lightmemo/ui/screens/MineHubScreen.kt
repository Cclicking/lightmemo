package com.click.lightmemo.ui.screens

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.click.lightmemo.ui.basic.SharedScrollBehavior as ScrollBehavior
import com.click.lightmemo.ui.secondary.AboutActivity
import com.click.lightmemo.ui.secondary.ApiSettingsActivity
import com.click.lightmemo.ui.secondary.AppearanceSettingsActivity
import com.click.lightmemo.ui.secondary.CalorieTargetActivity
import com.click.lightmemo.ui.secondary.DataManagementActivity
import com.click.lightmemo.ui.secondary.FoodDatabaseActivity
import com.click.lightmemo.ui.secondary.PersonalInfoActivity
import com.click.lightmemo.ui.utils.overScrollVertical
import com.click.lightmemo.viewmodel.SettingsViewModel
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
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    fun open(cls: Class<*>) {
        context.startActivity(Intent(context, cls))
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
                        summary = "身高、体重、性别和运动水平",
                        endActions = {
                            Text(
                                text = if (settings.hasPersonalProfile) "已填写" else "未填写",
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        },
                        onClick = { open(PersonalInfoActivity::class.java) },
                    )
                    ArrowPreference(
                        title = "每日目标",
                        summary = "每日热量与三大营养素目标",
                        endActions = {
                            Text(
                                text = "${settings.dailyCalorieTarget.toInt()}",
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        },
                        onClick = { open(CalorieTargetActivity::class.java) },
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
                        summary = "照片识别服务与模型配置",
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
                        onClick = { open(ApiSettingsActivity::class.java) },
                    )
                    ArrowPreference(
                        title = "数据管理",
                        summary = "备份恢复与食物数据管理",
                        endActions = {
                            Text(
                                text = if (settings.foodDataCentralApiKey.isNotBlank()) "在线已配置" else "离线优先",
                                fontSize = 14.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        },
                        onClick = { open(DataManagementActivity::class.java) },
                    )
                    ArrowPreference(
                        title = "数据库浏览",
                        summary = "搜索离线食物并查看营养数据",
                        onClick = { open(FoodDatabaseActivity::class.java) },
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
                        summary = "颜色主题、自动配色与顶部模糊效果",
                        onClick = { open(AppearanceSettingsActivity::class.java) },
                    )
                    ArrowPreference(
                        title = "关于",
                        summary = "版本、开源项目与数据来源",
                        onClick = { open(AboutActivity::class.java) },
                    )
                }
            }
        }
    }
}
