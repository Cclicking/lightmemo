package com.foodcalorie.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.sp
import com.foodcalorie.app.ui.basic.CollapsibleTopAppBar
import com.foodcalorie.app.ui.basic.CollapsibleTopAppBarDefaults
import com.foodcalorie.app.ui.basic.rememberCollapsibleTopAppBarState
import com.foodcalorie.app.ui.basic.rememberSharedScrollBehavior
import com.foodcalorie.app.ui.basic.LiquidTopBarButton
import com.foodcalorie.app.ui.components.LiquidAddButton
import com.foodcalorie.app.ui.components.ScheduleBottomBar
import com.foodcalorie.app.ui.overlay.BlurBottomSheet
import com.foodcalorie.app.ui.overlay.LocalSheetTopBarMaterial
import com.foodcalorie.app.ui.utils.LocalOverScrollState
import com.foodcalorie.app.ui.utils.OverScrollState
import top.yukonga.miuix.kmp.basic.Text
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.OnBackInvokedDefaultInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.foodcalorie.app.ui.nav.MineRoute
import com.foodcalorie.app.ui.nav.largeTitle
import com.foodcalorie.app.ui.screens.AddFoodRoute
import com.foodcalorie.app.ui.screens.ApiSettingsScreen
import com.foodcalorie.app.ui.screens.AppearanceSettingsScreen
import com.foodcalorie.app.ui.screens.AboutScreen
import com.foodcalorie.app.ui.screens.CalorieTargetScreen
import com.foodcalorie.app.ui.screens.DataManagementScreen
import com.foodcalorie.app.ui.screens.FoodDatabaseScreen
import com.foodcalorie.app.ui.screens.MineHubScreen
import com.foodcalorie.app.ui.screens.PersonalInfoScreen
import com.foodcalorie.app.ui.screens.StatsScreen
import com.foodcalorie.app.ui.screens.TodayScreen
import com.foodcalorie.app.ui.theme.FoodTheme
import com.foodcalorie.app.viewmodel.AddFoodViewModel
import com.foodcalorie.app.viewmodel.AddStep
import com.foodcalorie.app.viewmodel.BackupViewModel
import com.foodcalorie.app.viewmodel.SettingsViewModel
import com.foodcalorie.app.viewmodel.StatsViewModel
import com.foodcalorie.app.viewmodel.TodayViewModel
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop as rememberMiuixLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.os4.ChevronBackward
import top.yukonga.miuix.kmp.icon.os4.GridView
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import com.foodcalorie.app.ui.nav.androidActivityEffects
import com.foodcalorie.app.ui.nav.AndroidActivityTransition
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate

val LocalGlassSupported = staticCompositionLocalOf { true }

class MainActivity : ComponentActivity() {
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
}

private enum class AppTab(val title: String) {
    TODAY("今日"),
    STATS("统计"),
    MINE("我的"),
}


/** Opaque Activity-window surface for a nav entry so slide transitions never show through. */
@Composable
private fun OpaquePage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
    ) {
        content()
    }
}

/** Nexio visual shell; food state stays owned by the existing ViewModels. */
@Composable
fun FoodAppRoot() {
    val todayVm: TodayViewModel = viewModel()
    val statsVm: StatsViewModel = viewModel()
    val addVm: AddFoodViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()
    val backupVm: BackupViewModel = viewModel()

    FoodTheme {
        val context = LocalContext.current
        val addState by addVm.uiState.collectAsState()
        val selectedDate by todayVm.date.collectAsState()
        val appSettings by settingsVm.settings.collectAsState()
        val settingsError by settingsVm.error.collectAsState()
        val settingsReadError by settingsVm.readError.collectAsState()
        // 渐变模糊默认关闭；底栏液体玻璃始终保留
        val progressiveBlurEnabled = isRuntimeShaderSupported() && appSettings.glassEffectsEnabled
        val glassSupported = isRuntimeShaderSupported()
        CompositionLocalProvider(LocalGlassSupported provides glassSupported, LocalOverScrollState provides remember { OverScrollState() }) {
            var selectedTab by rememberSaveable { mutableIntStateOf(0) }
            var showAdd by rememberSaveable { mutableStateOf(false) }
            var showDatePicker by remember { mutableStateOf(false) }
            var todayManagement by rememberSaveable { mutableStateOf(false) }
            val mineBackStack = rememberNavBackStack<MineRoute>(MineRoute.Hub)
            val mineTop = (mineBackStack.lastOrNull() as? MineRoute) ?: MineRoute.Hub
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
            val apiList = rememberLazyListState()
            val targetList = rememberLazyListState()
            val profileList = rememberLazyListState()
            val dataManagementList = rememberLazyListState()
            val databaseList = rememberLazyListState()
            val appearanceList = rememberLazyListState()
            val aboutList = rememberLazyListState()
            val addList = rememberLazyListState()

            val activeList = when {
                selectedTab == 0 -> todayList
                selectedTab == 1 -> statsList
                else -> when (mineTop) {
                    MineRoute.Hub -> mineList
                    MineRoute.Api -> apiList
                    MineRoute.Target -> targetList
                    MineRoute.Profile -> profileList
                    MineRoute.DataManagement -> dataManagementList
                    MineRoute.Database -> databaseList
                    MineRoute.Appearance -> appearanceList
                    MineRoute.About -> aboutList
                }
            }

            // Each destination keeps its own LazyListState. Restore the app-bar state from that
            // destination instead of always expanding it when the bottom tab changes.
            LaunchedEffect(selectedTab, mineTop, todayManagement, activeList.canScrollBackward) {
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

            BackHandler(enabled = selectedTab == 2 && mineBackStack.size > 1) {
                mineBackStack.removeLastOrNull()
            }
            BackHandler(enabled = selectedTab == 0 && todayManagement) {
                todayManagement = false
            }

            val showBack = (selectedTab == 2 && mineBackStack.size > 1) || (selectedTab == 0 && todayManagement)
            val largeTitle = when {
                selectedTab == 0 && todayManagement -> "管理食物卡片"
                selectedTab == 0 -> when (selectedDate) {
                    LocalDate.now() -> "今日"
                    LocalDate.now().minusDays(1) -> "昨日"
                    else -> "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日"
                }
                selectedTab == 2 -> mineTop.largeTitle()
                else -> AppTab.entries[selectedTab].title
            }
            val compactTitle = when {
                selectedTab == 0 && todayManagement -> "管理食物卡片"
                selectedTab == 0 -> "今日"
                selectedTab == 2 -> mineTop.largeTitle()
                else -> AppTab.entries[selectedTab].title
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    CollapsibleTopAppBar(
                        title = compactTitle,
                        largeTitle = largeTitle,
                        showGradientOverlay = true,
                        scrollBehavior = scrollBehavior,
                        startAction = when {
                            showBack -> { { glassAlpha: Float, shadowAlpha: Float ->
                                LiquidTopBarButton(
                                onClick = {
                                    if (selectedTab == 0) {
                                        todayManagement = false
                                    } else if (mineBackStack.size > 1) {
                                        mineBackStack.removeLastOrNull()
                                    }
                                },
                                backdrop = backdrop,
                                icon = MiuixIcons.Os4.ChevronBackward,
                                contentDescription = "返回",
                                backdropAlpha = glassAlpha,
                                shadowAlpha = shadowAlpha,
                                )
                            } }
                            else -> null
                        },
                        endAction = if (selectedTab == 0 && !todayManagement) { { glassAlpha, shadowAlpha ->
                            LiquidTopBarButton(
                                onClick = { todayManagement = true },
                                backdrop = backdrop,
                                icon = MiuixIcons.Os4.GridView,
                                contentDescription = "管理食物卡片",
                                backdropAlpha = glassAlpha,
                                shadowAlpha = shadowAlpha,
                            )
                        } } else null,
                    )
                },
                bottomBar = {
                    ScheduleBottomBar(
                        selectedTab = selectedTab,
                        onTabSelected = {
                            selectedTab = it
                            todayManagement = false
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
                },
                content = { padding ->
                    @Suppress("UNUSED_VARIABLE")
                    val contentScrolled = activeList.canScrollBackward

                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (glassSupported) {
                                        Modifier
                                            .miuixLayerBackdrop(miuixBackdrop)
                                            .layerBackdrop(backdrop)
                                    } else {
                                        Modifier
                                    },
                                )
                                .background(MiuixTheme.colorScheme.surface),
                        ) {
                            when {
                                selectedTab == 0 -> TodayScreen(
                                    viewModel = todayVm,
                                    contentPadding = padding,
                                    scrollBehavior = scrollBehavior,
                                    listState = todayList,
                                    addState = addState,
                                    managementMode = todayManagement,
                                    showDatePicker = showDatePicker,
                                    proteinRingColor = Color(appSettings.proteinRingColor),
                                    carbsRingColor = Color(appSettings.carbsRingColor),
                                    fatRingColor = Color(appSettings.fatRingColor),
                                    onShowDatePicker = { showDatePicker = true },
                                    onDismissDatePicker = { showDatePicker = false },
                                    onAddClick = {
                                        addVm.setTargetDate(selectedDate)
                                        showAdd = true
                                    },
                                )
                                selectedTab == 1 -> StatsScreen(
                                    viewModel = statsVm,
                                    contentPadding = padding,
                                    scrollBehavior = scrollBehavior,
                                    listState = statsList,
                                )
                                else -> NavDisplay(
                                    backStack = mineBackStack,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MiuixTheme.colorScheme.surface),
                                    onBack = { mineBackStack.removeLastOrNull() },
                                    transition = AndroidActivityTransition,
                                    effects = androidActivityEffects(MiuixTheme.colorScheme.surface),
                                ) {
                                    entry<MineRoute.Hub> {
                                        OpaquePage {
                                        MineHubScreen(
                                            contentPadding = padding,
                                            scrollBehavior = scrollBehavior,
                                            listState = mineList,
                                            viewModel = settingsVm,
                                            onApi = { mineBackStack.add(MineRoute.Api) },
                                            onTarget = { mineBackStack.add(MineRoute.Target) },
                                            onProfile = { mineBackStack.add(MineRoute.Profile) },
                                            onDataManagement = { mineBackStack.add(MineRoute.DataManagement) },
                                            onDatabase = { mineBackStack.add(MineRoute.Database) },
                                            onAppearance = { mineBackStack.add(MineRoute.Appearance) },
                                            onAbout = { mineBackStack.add(MineRoute.About) },
                                        )
                                        }
                                    }
                                    entry<MineRoute.Profile>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                                        OpaquePage {
                                            PersonalInfoScreen(
                                            viewModel = settingsVm,
                                            contentPadding = padding,
                                            scrollBehavior = scrollBehavior,
                                            listState = profileList,
                                        )
                                        }
                                    }
                                    entry<MineRoute.Api>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                                        OpaquePage {
                                            ApiSettingsScreen(
                                            viewModel = settingsVm,
                                            contentPadding = padding,
                                            scrollBehavior = scrollBehavior,
                                            listState = apiList,
                                        )
                                        }
                                    }
                                    entry<MineRoute.Target>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                                        OpaquePage {
                                            CalorieTargetScreen(
                                            viewModel = settingsVm,
                                            contentPadding = padding,
                                            scrollBehavior = scrollBehavior,
                                            listState = targetList,
                                        )
                                        }
                                    }
                                    entry<MineRoute.DataManagement>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                                        OpaquePage {
                                            DataManagementScreen(
                                            viewModel = settingsVm,
                                            backupViewModel = backupVm,
                                            contentPadding = padding,
                                            scrollBehavior = scrollBehavior,
                                            listState = dataManagementList,
                                        )
                                        }
                                    }
                                    entry<MineRoute.Database>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                                        OpaquePage {
                                            FoodDatabaseScreen(
                                            contentPadding = padding,
                                            scrollBehavior = scrollBehavior,
                                            listState = databaseList,
                                        )
                                        }
                                    }
                                    entry<MineRoute.Appearance>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                                        OpaquePage {
                                            AppearanceSettingsScreen(
                                            viewModel = settingsVm,
                                            contentPadding = padding,
                                            scrollBehavior = scrollBehavior,
                                            listState = appearanceList,
                                        )
                                        }
                                    }
                                    entry<MineRoute.About>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                                        OpaquePage {
                                            AboutScreen(
                                            contentPadding = padding,
                                            scrollBehavior = scrollBehavior,
                                            listState = aboutList,
                                        )
                                        }
                                    }
                                }
                            }
                        }

                        if (progressiveBlurEnabled && appSettings.topGradientBlurEnabled) {
                            val statusBarHeight = androidx.compose.foundation.layout.WindowInsets.statusBars
                                .asPaddingValues()
                                .calculateTopPadding()
                            val progressiveHeight =
                                statusBarHeight + CollapsibleTopAppBarDefaults.CollapsedHeight +
                                    appSettings.topGradientBlurRangeDp.dp
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
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
                        }
                    }

                    var sheetContentBackdrop by remember { mutableStateOf<com.kyant.backdrop.Backdrop?>(null) }
                    val statusBarsPadding = androidx.compose.foundation.layout.WindowInsets.statusBars
                        .asPaddingValues()
                        .calculateTopPadding()
                    BlurBottomSheet(
                        show = showAdd,
                        title = if (addState.step is AddStep.PickSource) "添加食物" else "记录食物",
                        liquidGlassBackdrop = if (glassSupported) backdrop else null,
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
                                // Glass circle only appears after scroll; icon-only at rest.
                                backdropAlpha = material.backdropAlpha,
                                shadowAlpha = material.shadowAlpha,
                            )
                        },
                    ) {
                        // Leave a compact gap under the sheet title.
                        AddFoodRoute(
                            viewModel = addVm,
                            contentPadding = PaddingValues(top = 61.dp, bottom = 24.dp),
                            scrollBehavior = null,
                            listState = addList,
                            onDone = { showAdd = false },
                        )
                    }
                },
            )
        }
    }
}



