package com.foodcalorie.app.ui.secondary

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.OnBackInvokedDefaultInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.foodcalorie.app.LocalGlassSupported
import com.foodcalorie.app.ui.basic.SharedScrollBehavior
import com.foodcalorie.app.ui.nav.SecondaryPageShell
import com.foodcalorie.app.ui.screens.AboutScreen
import com.foodcalorie.app.ui.screens.ApiSettingsScreen
import com.foodcalorie.app.ui.screens.AppearanceSettingsScreen
import com.foodcalorie.app.ui.screens.CalorieTargetScreen
import com.foodcalorie.app.ui.screens.DataManagementScreen
import com.foodcalorie.app.ui.screens.FoodDatabaseScreen
import com.foodcalorie.app.ui.screens.PersonalInfoScreen
import com.foodcalorie.app.ui.theme.FoodTheme
import com.foodcalorie.app.ui.utils.LocalOverScrollState
import com.foodcalorie.app.ui.utils.OverScrollState
import com.foodcalorie.app.viewmodel.BackupViewModel
import com.foodcalorie.app.viewmodel.SettingsViewModel
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported

/**
 * Real Activity host for a settings page. The system window transition applies
 * automatically — same model as NexioSchedule secondary screens.
 */
abstract class SecondarySettingsActivity : ComponentActivity() {
    protected abstract val pageTitle: String

    @Composable
    protected abstract fun PageContent(
        settingsVm: SettingsViewModel,
        backupVm: BackupViewModel,
        contentPadding: PaddingValues,
        scrollBehavior: SharedScrollBehavior,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { dark },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                onDispose {}
            }
            val navigationEventOwner = rememberNavigationEventDispatcherOwner(parent = null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val navigationEventInput = remember(navigationEventOwner) {
                    OnBackInvokedDefaultInput(onBackInvokedDispatcher)
                }
                DisposableEffect(navigationEventOwner, navigationEventInput) {
                    navigationEventOwner.navigationEventDispatcher.addInput(navigationEventInput)
                    onDispose {
                        navigationEventOwner.navigationEventDispatcher.removeInput(navigationEventInput)
                    }
                }
            }
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides navigationEventOwner,
                LocalGlassSupported provides isRuntimeShaderSupported(),
                LocalOverScrollState provides remember { OverScrollState() },
            ) {
                FoodTheme {
                    val settingsVm: SettingsViewModel = viewModel()
                    val backupVm: BackupViewModel = viewModel()
                    SecondaryPageShell(title = pageTitle, onBack = { finish() }) { padding, scroll ->
                        PageContent(settingsVm, backupVm, padding, scroll)
                    }
                }
            }
        }
    }
}

class PersonalInfoActivity : SecondarySettingsActivity() {
    override val pageTitle = "个人信息"

    @Composable
    override fun PageContent(
        settingsVm: SettingsViewModel,
        backupVm: BackupViewModel,
        contentPadding: PaddingValues,
        scrollBehavior: SharedScrollBehavior,
    ) {
        PersonalInfoScreen(
            viewModel = settingsVm,
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            listState = rememberLazyListState(),
        )
    }
}

class ApiSettingsActivity : SecondarySettingsActivity() {
    override val pageTitle = "AI设置"

    @Composable
    override fun PageContent(
        settingsVm: SettingsViewModel,
        backupVm: BackupViewModel,
        contentPadding: PaddingValues,
        scrollBehavior: SharedScrollBehavior,
    ) {
        ApiSettingsScreen(
            viewModel = settingsVm,
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            listState = rememberLazyListState(),
        )
    }
}

class CalorieTargetActivity : SecondarySettingsActivity() {
    override val pageTitle = "每日目标"

    @Composable
    override fun PageContent(
        settingsVm: SettingsViewModel,
        backupVm: BackupViewModel,
        contentPadding: PaddingValues,
        scrollBehavior: SharedScrollBehavior,
    ) {
        CalorieTargetScreen(
            viewModel = settingsVm,
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            listState = rememberLazyListState(),
        )
    }
}

class DataManagementActivity : SecondarySettingsActivity() {
    override val pageTitle = "数据管理"

    @Composable
    override fun PageContent(
        settingsVm: SettingsViewModel,
        backupVm: BackupViewModel,
        contentPadding: PaddingValues,
        scrollBehavior: SharedScrollBehavior,
    ) {
        DataManagementScreen(
            viewModel = settingsVm,
            backupViewModel = backupVm,
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            listState = rememberLazyListState(),
        )
    }
}

class FoodDatabaseActivity : SecondarySettingsActivity() {
    override val pageTitle = "数据库浏览"

    @Composable
    override fun PageContent(
        settingsVm: SettingsViewModel,
        backupVm: BackupViewModel,
        contentPadding: PaddingValues,
        scrollBehavior: SharedScrollBehavior,
    ) {
        FoodDatabaseScreen(
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            listState = rememberLazyListState(),
        )
    }
}

class AppearanceSettingsActivity : SecondarySettingsActivity() {
    override val pageTitle = "个性化设置"

    @Composable
    override fun PageContent(
        settingsVm: SettingsViewModel,
        backupVm: BackupViewModel,
        contentPadding: PaddingValues,
        scrollBehavior: SharedScrollBehavior,
    ) {
        AppearanceSettingsScreen(
            viewModel = settingsVm,
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            listState = rememberLazyListState(),
        )
    }
}

class AboutActivity : SecondarySettingsActivity() {
    override val pageTitle = "关于"

    @Composable
    override fun PageContent(
        settingsVm: SettingsViewModel,
        backupVm: BackupViewModel,
        contentPadding: PaddingValues,
        scrollBehavior: SharedScrollBehavior,
    ) {
        AboutScreen(
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            listState = rememberLazyListState(),
        )
    }
}
