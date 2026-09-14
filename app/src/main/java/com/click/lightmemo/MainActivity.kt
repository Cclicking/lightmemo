package com.click.lightmemo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.sp
import com.click.lightmemo.ui.basic.CollapsibleTopAppBar
import com.click.lightmemo.ui.basic.CollapsibleTopAppBarDefaults
import com.click.lightmemo.ui.basic.rememberCollapsibleTopAppBarState
import com.click.lightmemo.ui.basic.rememberSharedScrollBehavior
import com.click.lightmemo.ui.basic.LiquidTopBarButton
import com.click.lightmemo.ui.components.LiquidAddButton
import com.click.lightmemo.ui.components.ScheduleBottomBar
import com.click.lightmemo.ui.overlay.BlurBottomSheet
import com.click.lightmemo.ui.overlay.LocalSheetTopBarMaterial
import com.click.lightmemo.ui.platform.NativeTextContextMenuHost
import com.click.lightmemo.ui.utils.LocalOverScrollState
import com.click.lightmemo.ui.utils.OverScrollState
import top.yukonga.miuix.kmp.basic.Text
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.OnBackInvokedDefaultInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.click.lightmemo.ui.screens.AddFoodRoute
import com.click.lightmemo.ui.screens.AppUpdateDialog
import com.click.lightmemo.ui.screens.ApiSettingsScreen
import com.click.lightmemo.ui.screens.AppearanceSettingsScreen
import com.click.lightmemo.ui.screens.AboutScreen
import com.click.lightmemo.ui.screens.CalorieTargetScreen
import com.click.lightmemo.ui.screens.DataManagementScreen
import com.click.lightmemo.ui.screens.FoodDatabaseScreen
import com.click.lightmemo.ui.screens.MineHubScreen
import com.click.lightmemo.ui.screens.PersonalInfoScreen
import com.click.lightmemo.ui.screens.StatsScreen
import com.click.lightmemo.ui.screens.TodayScreen
import com.click.lightmemo.ui.theme.FoodTheme
import com.click.lightmemo.ui.theme.toComposeColors
import com.click.lightmemo.viewmodel.AddFoodViewModel
import com.click.lightmemo.viewmodel.AddStep
import com.click.lightmemo.viewmodel.AppUpdateViewModel
import com.click.lightmemo.viewmodel.BackupViewModel
import com.click.lightmemo.viewmodel.SettingsViewModel
import com.click.lightmemo.viewmodel.StatsViewModel
import com.click.lightmemo.viewmodel.TodayViewModel
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop as rememberMiuixLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.isRenderEffectSupported
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.os4.ChevronBackward
import top.yukonga.miuix.kmp.icon.os4.GridView
import top.yukonga.miuix.kmp.icon.os4.Months
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow

val LocalGlassSupported = staticCompositionLocalOf { true }

class MainActivity : ComponentActivity() {
    private val openRecognition = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleRecognitionIntent(intent)
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

            // Miuix OverlayBottomSheet uses NavigationEventHandler internally.  The app is not
            // hosted by a navigation library, so provide the root dispatcher explicitly instead
            // of allowing the sheet to fail during its first composition.
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
            ) {
                FoodAppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRecognitionIntent(intent)
    }

    private fun handleRecognitionIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(com.click.lightmemo.recognition.EXTRA_OPEN_RECOGNITION, false) == true) {
            openRecognition.value = true
        }
    }

    internal fun consumeOpenRecognition() {
        openRecognition.value = false
    }

    internal fun openRecognitionState() = openRecognition
}

private enum class AppTab(val title: String) {
    TODAY("今日"),
    STATS("统计"),
    MINE("我的"),
}


/** Nexio visual shell; food state stays owned by the existing ViewModels. */
@Composable
fun FoodAppRoot() {
    val todayVm: TodayViewModel = viewModel()
    val statsVm: StatsViewModel = viewModel()
    val addVm: AddFoodViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()
    val backupVm: BackupViewModel = viewModel()
    val appUpdateVm: AppUpdateViewModel = viewModel()

    FoodTheme {
        NativeTextContextMenuHost {
            val context = LocalContext.current
            val addState by addVm.uiState.collectAsState()
            val appUpdateState by appUpdateVm.uiState.collectAsState()
            val selectedDate by todayVm.date.collectAsState()
            val appSettings by settingsVm.settings.collectAsState()
            val palette = remember(appSettings.colorPalette) { appSettings.colorPalette.toComposeColors() }
            val settingsError by settingsVm.error.collectAsState()
            val settingsReadError by settingsVm.readError.collectAsState()
            // Android 12（API 31–32）：玻璃降级为高斯模糊（RenderEffect），渐变模糊降级为软渐变
            val fullLiquidGlassSupported = isRuntimeShaderSupported()
            val blurGlassSupported = isRenderEffectSupported()
            val progressiveBlurEnabled = fullLiquidGlassSupported && appSettings.glassEffectsEnabled
            val softGradientBlurEnabled =
                !fullLiquidGlassSupported && blurGlassSupported &&
                    appSettings.glassEffectsEnabled && appSettings.topGradientBlurEnabled
            CompositionLocalProvider(
                LocalGlassSupported provides blurGlassSupported,
                LocalOverScrollState provides remember { OverScrollState() },
            ) {
            var selectedTab by rememberSaveable { mutableIntStateOf(0) }
            var showAdd by rememberSaveable { mutableStateOf(false) }
            var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
            var todayCalendarExpanded by rememberSaveable { mutableStateOf(false) }
            var statsCalendarExpanded by rememberSaveable { mutableStateOf(false) }
            var showDatePicker by remember { mutableStateOf(false) }
            val activity = context as? MainActivity
            val openRecognition = if (activity != null) {
                activity.openRecognitionState().collectAsState().value
            } else {
                false
            }
            LaunchedEffect(openRecognition) {
                if (openRecognition) {
                    selectedTab = 0
                    showAdd = true
                    activity?.consumeOpenRecognition()
                }
            }
            LaunchedEffect(settingsError, settingsReadError) {
                (settingsError ?: settingsReadError)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
            }
            val backdrop = rememberLayerBackdrop()
            val surfaceColor = MiuixTheme.colorScheme.surface
            val miuixBackdrop = rememberMiuixLayerBackdrop {
                drawRect(surfaceColor)
                drawContent()
            }

            val appBarState = rememberCollapsibleTopAppBarState()
            val scrollBehavior = rememberSharedScrollBehavior(state = appBarState)
            val todayList = rememberLazyListState()
            val statsList = rememberLazyListState()
            val mineList = rememberLazyListState()
            val addList = rememberLazyListState()

            val activeList = when (selectedTab) {
                0 -> todayList
                1 -> statsList
                else -> mineList
            }

            // Each destination keeps its own LazyListState. Restore the app-bar state from that
            // destination instead of always expanding it when the bottom tab changes.
            LaunchedEffect(selectedTab, activeList.canScrollBackward) {
                withFrameNanos { }
                val scrolled = activeList.canScrollBackward
                appBarState.heightOffset = if (scrolled) appBarState.heightOffsetLimit else 0f
                appBarState.contentOffset = if (scrolled) -100f else 0f
            }

            LaunchedEffect(selectedDate) {
                todayList.scrollToItem(0)
                appBarState.heightOffset = 0f
                appBarState.contentOffset = 0f
            }

            LaunchedEffect(Unit) {
                addVm.events.collect { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }

            LaunchedEffect(appUpdateState.available) {
                showUpdateDialog = appUpdateState.available != null
            }

            val largeTitle = when (selectedTab) {
                0 -> when (selectedDate) {
                    LocalDate.now() -> "今日"
                    LocalDate.now().minusDays(1) -> "昨日"
                    else -> "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日"
                }
                2 -> "我的"
                else -> AppTab.entries[selectedTab].title
            }
            val compactTitle = when (selectedTab) {
                0 -> "今日"
                2 -> "我的"
                else -> AppTab.entries[selectedTab].title
            }

            @Composable
            fun MainTopBar() {
                CollapsibleTopAppBar(
                    title = compactTitle,
                    largeTitle = largeTitle,
                    showGradientOverlay = true,
                    scrollBehavior = scrollBehavior,
                    endAction = when (selectedTab) {
                        0 -> { { glassAlpha, shadowAlpha ->
                            androidx.compose.foundation.layout.Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                            LiquidTopBarButton(
                                onClick = {
                                    context.startActivity(
                                        android.content.Intent(
                                            context,
                                            com.click.lightmemo.ui.secondary.ManageFoodCardsActivity::class.java,
                                        ),
                                    )
                                },
                                backdrop = backdrop,
                                icon = MiuixIcons.Os4.GridView,
                                contentDescription = "管理食物卡片",
                                backdropAlpha = glassAlpha,
                                shadowAlpha = shadowAlpha,
                            )
                            LiquidTopBarButton(
                                onClick = { todayCalendarExpanded = !todayCalendarExpanded },
                                backdrop = backdrop,
                                icon = MiuixIcons.Os4.Months,
                                contentDescription = if (todayCalendarExpanded) "收起月视图" else "展开月视图",
                                backdropAlpha = glassAlpha,
                                shadowAlpha = shadowAlpha,
                            )
                            }
                        } }
                        1 -> { { glassAlpha, shadowAlpha ->
                            LiquidTopBarButton(
                                onClick = { statsCalendarExpanded = !statsCalendarExpanded },
                                backdrop = backdrop,
                                icon = MiuixIcons.Os4.Months,
                                contentDescription = if (statsCalendarExpanded) "收起月视图" else "展开月视图",
                                backdropAlpha = glassAlpha,
                                shadowAlpha = shadowAlpha,
                            )
                        } }
                        else -> null
                    },
                )
            }

            @Composable
            fun MainBottomBar() {
                ScheduleBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = {
                        selectedTab = it
                        showDatePicker = false
                    },
                    liquidGlassBackdrop = backdrop,
                    addButton = {
                        LiquidAddButton(
                            onClick = {
                                addVm.setTargetDate(if (selectedTab == 0) selectedDate else LocalDate.now())
                                showAdd = true
                            },
                            backdrop = backdrop
                        )
                    }
                )
            }

            @Composable
            fun GlassContentBox(content: @Composable () -> Unit) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (blurGlassSupported) {
                                Modifier
                                    .miuixLayerBackdrop(miuixBackdrop)
                                    .layerBackdrop(backdrop)
                            } else {
                                Modifier
                            },
                        )
                        .background(MiuixTheme.colorScheme.surface),
                ) {
                    content()
                }
            }

            @Composable
            fun TopProgressiveBlur() {
                if (progressiveBlurEnabled && appSettings.topGradientBlurEnabled) {
                    val statusBarHeight = androidx.compose.foundation.layout.WindowInsets.statusBars
                        .asPaddingValues()
                        .calculateTopPadding()
                    val progressiveHeight =
                        statusBarHeight + CollapsibleTopAppBarDefaults.CollapsedHeight +
                            appSettings.topGradientBlurRangeDp.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(progressiveHeight)
                            .progressiveTextureBlur(
                                backdrop = miuixBackdrop,
                                shape = RectangleShape,
                                blurRadius = 24f,
                                gradient = ProgressiveBlur.Top,
                                enabled = true,
                            ),
                    )
                } else if (softGradientBlurEnabled) {
                    val statusBarHeight = androidx.compose.foundation.layout.WindowInsets.statusBars
                        .asPaddingValues()
                        .calculateTopPadding()
                    val progressiveHeight =
                        statusBarHeight + CollapsibleTopAppBarDefaults.CollapsedHeight +
                            appSettings.topGradientBlurRangeDp.dp
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val gradientColor = MiuixTheme.colorScheme.surface
                    val endY = progressiveHeight.value * density.density
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(progressiveHeight)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to gradientColor.copy(alpha = 0.9f),
                                        0.4f to gradientColor.copy(alpha = 0.82f),
                                        0.7f to gradientColor.copy(alpha = 0.6f),
                                        1.0f to gradientColor.copy(alpha = 0.0f),
                                    ),
                                    startY = 0f,
                                    endY = endY,
                                ),
                            ),
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { MainTopBar() },
                    bottomBar = { MainBottomBar() },
                    content = { padding ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            GlassContentBox {
                                when (selectedTab) {
                                    0 -> TodayScreen(
                                        viewModel = todayVm,
                                        contentPadding = padding,
                                        scrollBehavior = scrollBehavior,
                                        listState = todayList,
                                        addState = addState,
                                        managementMode = false,
                                        calendarExpanded = todayCalendarExpanded,
                                        showDatePicker = showDatePicker,
                                        palette = palette,
                                        onShowDatePicker = { showDatePicker = true },
                                        onDismissDatePicker = { showDatePicker = false },
                                        onAddClick = {
                                            addVm.setTargetDate(selectedDate)
                                            showAdd = true
                                        },
                                    )
                                    1 -> StatsScreen(
                                        viewModel = statsVm,
                                        contentPadding = padding,
                                        scrollBehavior = scrollBehavior,
                                        listState = statsList,
                                        calendarExpanded = statsCalendarExpanded,
                                        palette = palette,
                                    )
                                    else -> MineHubScreen(
                                        contentPadding = padding,
                                        scrollBehavior = scrollBehavior,
                                        listState = mineList,
                                        viewModel = settingsVm,
                                    )
                                }
                            }
                    var sheetContentBackdrop by remember { mutableStateOf<com.kyant.backdrop.Backdrop?>(null) }
                    val statusBarsPadding = androidx.compose.foundation.layout.WindowInsets.statusBars
                        .asPaddingValues()
                        .calculateTopPadding()
                    BlurBottomSheet(
                        show = showAdd,
                        title = if (addState.step is AddStep.PickSource) "添加食物" else "记录食物",
                        liquidGlassBackdrop = if (blurGlassSupported) backdrop else null,
                        dimBackground = true,
                        sheetOffsetDp = statusBarsPadding + 5.dp,
                        onDismissRequest = { if (!addState.saving) showAdd = false },
                        onSheetContentBackdropCreated = { sheetContentBackdrop = it },
                        startAction = {
                            val material = LocalSheetTopBarMaterial.current
                            LiquidTopBarButton(
                                onClick = {
                                    if (addState.saving) return@LiquidTopBarButton
                                    if (addState.step is AddStep.PickSource) {
                                        showAdd = false
                                    } else {
                                        addVm.backToPick()
                                    }
                                },
                                backdrop = sheetContentBackdrop ?: backdrop,
                                icon = if (addState.step is AddStep.PickSource) {
                                    MiuixIcons.Basic.Close
                                } else {
                                    MiuixIcons.Os4.ChevronBackward
                                },
                                contentDescription = if (addState.step is AddStep.PickSource) {
                                    "关闭"
                                } else {
                                    "返回添加食物"
                                },
                                modifier = Modifier.padding(start = 18.dp),
                                iconSize = 24.dp,
                                backdropAlpha = material.backdropAlpha,
                                shadowAlpha = material.shadowAlpha,
                            )
                        },
                    ) {
                        AddFoodRoute(
                            viewModel = addVm,
                            contentPadding = PaddingValues(top = 61.dp, bottom = 24.dp),
                            scrollBehavior = null,
                            listState = addList,
                            onDone = { showAdd = false },
                            palette = palette,
                        )
                    }
                            TopProgressiveBlur()
                            AppUpdateDialog(
                                show = showUpdateDialog,
                                state = appUpdateState,
                                onDismiss = { showUpdateDialog = false },
                            )
                        }
                    },
                )

            }
        }
    }
}

}
