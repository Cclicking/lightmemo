package com.click.lightmemo.ui.secondary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.click.lightmemo.ui.basic.SharedScrollBehavior
import com.click.lightmemo.viewmodel.BackupViewModel
import com.click.lightmemo.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class HealthConnectPrivacyActivity : SecondarySettingsActivity() {
    override val pageTitle = "健康数据使用说明"

    @Composable
    override fun PageContent(
        settingsVm: SettingsViewModel,
        backupVm: BackupViewModel,
        contentPadding: PaddingValues,
        scrollBehavior: SharedScrollBehavior,
    ) {
        Column(
            Modifier.padding(contentPadding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Health Connect", style = MiuixTheme.textStyles.headline1)
            Text("轻记仅读取 Health Connect 中的每日总消耗热量，用于统计页的摄入与消耗折线图及热量差计算。总消耗包括健康数据来源记录的活动与基础代谢消耗。")
            Text("历史数据权限用于查看较早日期的消耗热量。你可以拒绝历史权限，仍然查看近期数据。应用不会向 Health Connect 写入数据。")
            Text("健康数据仅在打开统计页时读取，在本机内存中用于显示，不上传到服务器，不用于食物识别，也不包含在导出或备份中。")
            Text("你可以随时在 Health Connect 的应用权限中撤销授权。撤销后，统计页停止显示消耗数据；饮食记录仍可正常使用。")
        }
    }
}
