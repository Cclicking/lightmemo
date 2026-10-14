package com.click.lightmemo.ui.secondary

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.click.lightmemo.ui.basic.SharedScrollBehavior
import com.click.lightmemo.ui.screens.ManageStatsScreen
import com.click.lightmemo.viewmodel.BackupViewModel
import com.click.lightmemo.viewmodel.SettingsViewModel

class ManageStatsActivity : SecondarySettingsActivity() {
    override val pageTitle = "管理统计页"

    @Composable
    override fun PageContent(
        settingsVm: SettingsViewModel,
        backupVm: BackupViewModel,
        contentPadding: PaddingValues,
        scrollBehavior: SharedScrollBehavior,
    ) {
        ManageStatsScreen(settingsVm, contentPadding)
    }
}
