package com.click.lightmemo.ui.secondary

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.OnBackInvokedDefaultInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.click.lightmemo.LocalGlassSupported
import com.click.lightmemo.ui.basic.LiquidTopBarButton
import com.click.lightmemo.ui.basic.SharedScrollBehavior
import com.click.lightmemo.ui.nav.SecondaryPageShell
import com.click.lightmemo.ui.screens.AboutScreen
import com.click.lightmemo.ui.screens.ApiSettingsScreen
import com.click.lightmemo.ui.screens.ApiConfigurationScreen
import com.click.lightmemo.ui.screens.PromptSettingsScreen
import com.click.lightmemo.ui.screens.AppearanceSettingsScreen
import com.click.lightmemo.ui.screens.CalorieTargetScreen
import com.click.lightmemo.ui.screens.DataManagementScreen
import com.click.lightmemo.ui.screens.FoodDatabaseScreen
import com.click.lightmemo.ui.screens.EditFoodScreen
import com.click.lightmemo.ui.screens.PersonalInfoScreen
import com.click.lightmemo.ui.screens.PermissionManagementScreen
import com.click.lightmemo.ui.theme.FoodTheme
import com.click.lightmemo.ui.theme.toComposeColors
import com.click.lightmemo.ui.utils.LocalOverScrollState
import com.click.lightmemo.ui.utils.OverScrollState
import com.click.lightmemo.viewmodel.BackupViewModel
import com.click.lightmemo.viewmodel.AppUpdateViewModel
import com.click.lightmemo.viewmodel.SettingsViewModel
import com.click.lightmemo.viewmodel.EditFoodViewModel
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.isRenderEffectSupported
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.os4.Pin
import top.yukonga.miuix.kmp.icon.os4.Unpin

/**
 * Real Activity host for a settings page. The system window transition applies
 * automatically — same model as NexioSchedule secondary screens.
 */
abstract class SecondarySettingsActivity : ComponentActivity() {
    protected abstract val pageTitle: String
    protected open val hasPageEndAction: Boolean = false

    @Composable
    protected open fun PageEndAction(
        backdrop: Backdrop,
        backdropAlpha: Float,
        shadowAlpha: Float,
    ) = Unit

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
                LocalGlassSupported provides isRenderEffectSupported(),
                LocalOverScrollState provides remember { OverScrollState() },
            ) {
                FoodTheme {
                    val settingsVm: SettingsViewModel = viewModel()
                    val backupVm: BackupViewModel = viewModel()
                    SecondaryPageShell(
                        title = pageTitle,
                        onBack = { finish() },
                        endAction = if (hasPageEndAction) {
                            { backdrop, backdropAlpha, shadowAlpha ->
                                PageEndAction(backdrop, backdropAlpha, shadowAlpha)
                            }
                        } else {
                            null
                        },
                    ) { padding, scroll ->
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

class ApiConfigurationActivity : SecondarySettingsActivity() {
    override val pageTitle = "配置设置"

    @Composable
    override fun PageContent(settingsVm: SettingsViewModel, backupVm: BackupViewModel, contentPadding: PaddingValues, scrollBehavior: SharedScrollBehavior) {
        ApiConfigurationScreen(settingsVm, contentPadding, scrollBehavior, rememberLazyListState(), onDeleted = { finish() })
    }
}

class PromptSettingsActivity : SecondarySettingsActivity() {
    override val pageTitle = "Prompt 修改"

    @Composable
    override fun PageContent(settingsVm: SettingsViewModel, backupVm: BackupViewModel, contentPadding: PaddingValues, scrollBehavior: SharedScrollBehavior) {
        PromptSettingsScreen(settingsVm, contentPadding, scrollBehavior, rememberLazyListState())
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
    override val hasPageEndAction = true
    private var searchVisible by mutableStateOf(false)

    @Composable
    override fun PageEndAction(
        backdrop: Backdrop,
        backdropAlpha: Float,
        shadowAlpha: Float,
    ) {
        LiquidTopBarButton(
            onClick = { searchVisible = !searchVisible },
            backdrop = backdrop,
            icon = if (searchVisible) MiuixIcons.Basic.Close else MiuixIcons.Basic.Search,
            contentDescription = if (searchVisible) "关闭搜索" else "搜索",
            backdropAlpha = backdropAlpha,
            shadowAlpha = shadowAlpha,
        )
    }

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
            searchVisible = searchVisible,
            onSearchVisibleChange = { searchVisible = it },
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
        val updateVm: AppUpdateViewModel = viewModel()
        AboutScreen(
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            listState = rememberLazyListState(),
            updateViewModel = updateVm,
        )
    }
}

class PermissionManagementActivity : SecondarySettingsActivity() {
    override val pageTitle = "权限管理"

    @Composable
    override fun PageContent(
        settingsVm: SettingsViewModel,
        backupVm: BackupViewModel,
        contentPadding: PaddingValues,
        scrollBehavior: SharedScrollBehavior,
    ) {
        PermissionManagementScreen(
            viewModel = settingsVm,
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            listState = rememberLazyListState(),
        )
    }
}

class EditFoodActivity : SecondarySettingsActivity() {
    override val pageTitle = "编辑记录"
    override val hasPageEndAction: Boolean = true

    // 必须用 Compose 状态持有 VM，否则顶栏 endAction 首帧后不会因 holder 赋值而重组
    private val editVmState = mutableStateOf<EditFoodViewModel?>(null)

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
    }

    @Composable
    override fun PageEndAction(
        backdrop: Backdrop,
        backdropAlpha: Float,
        shadowAlpha: Float,
    ) {
        val editVm = editVmState.value ?: return
        val state by editVm.uiState.collectAsState()
        val name = state.entry?.name?.trim().orEmpty()
        val pinned = name.isNotEmpty() && state.pinnedNames.contains(name)
        LiquidTopBarButton(
            onClick = {
                val entry = state.entry ?: return@LiquidTopBarButton
                if (pinned) editVm.unpinEntryPreset(entry.name)
                else editVm.pinEntryAsPreset(entry)
            },
            backdrop = backdrop,
            icon = if (pinned) MiuixIcons.Os4.Unpin else MiuixIcons.Os4.Pin,
            contentDescription = if (pinned) "取消预设" else "加入预设",
            modifier = Modifier.padding(end = 18.dp),
            iconSize = 24.dp,
            backdropAlpha = backdropAlpha,
            shadowAlpha = shadowAlpha,
        )
    }

    @Composable
    override fun PageContent(
        settingsVm: SettingsViewModel,
        backupVm: BackupViewModel,
        contentPadding: PaddingValues,
        scrollBehavior: SharedScrollBehavior,
    ) {
        val editVm: EditFoodViewModel = viewModel()
        editVmState.value = editVm
        val settings by settingsVm.settings.collectAsState()
        val entryId = intent.getLongExtra(EXTRA_ENTRY_ID, -1L)
        LaunchedEffect(entryId) {
            editVm.load(entryId)
        }
        EditFoodScreen(
            viewModel = editVm,
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            listState = rememberLazyListState(),
            calorieTarget = settings.dailyCalorieTarget,
            proteinTarget = settings.effectiveProteinG.takeIf { it > 0f } ?: 120f,
            carbsTarget = settings.effectiveCarbsG.takeIf { it > 0f } ?: 250f,
            fatTarget = settings.effectiveFatG.takeIf { it > 0f } ?: 60f,
            palette = settings.colorPalette.toComposeColors(),
            onSaved = { finish() },
        )
    }
}
