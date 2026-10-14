package com.click.lightmemo.ui.secondary

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import com.click.lightmemo.ui.basic.SharedScrollBehavior
import com.click.lightmemo.ui.screens.HealthConnectSettingsScreen
import com.click.lightmemo.viewmodel.BackupViewModel
import com.click.lightmemo.viewmodel.SettingsViewModel

class HealthConnectSettingsActivity : SecondarySettingsActivity() {
    override val pageTitle = "Health Connect"

    @Composable
    override fun PageContent(
        settingsVm: SettingsViewModel,
        backupVm: BackupViewModel,
        contentPadding: PaddingValues,
        scrollBehavior: SharedScrollBehavior,
    ) {
        HealthConnectSettingsScreen(
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            listState = rememberLazyListState(),
        )
    }
}
