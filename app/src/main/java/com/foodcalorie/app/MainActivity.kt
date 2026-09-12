package com.foodcalorie.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.foodcalorie.app.ui.basic.CollapsibleTopAppBar
import com.foodcalorie.app.ui.basic.rememberCollapsibleTopAppBarState
import com.foodcalorie.app.ui.basic.rememberSharedScrollBehavior
import com.foodcalorie.app.ui.basic.LiquidTopBarButton
import com.foodcalorie.app.ui.components.LiquidBottomTabs
import com.foodcalorie.app.ui.components.LiquidBottomTab
import com.foodcalorie.app.ui.components.LiquidAddButton
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.OnBackInvokedDefaultInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.foodcalorie.app.ui.screens.AddFoodRoute
import com.foodcalorie.app.ui.screens.ApiSettingsScreen
import com.foodcalorie.app.ui.screens.CalorieTargetScreen
import com.foodcalorie.app.ui.screens.MineHubScreen
import com.foodcalorie.app.ui.screens.StatsScreen
import com.foodcalorie.app.ui.screens.TodayScreen
import com.foodcalorie.app.ui.theme.FoodTheme
import com.foodcalorie.app.viewmodel.AddFoodViewModel
import com.foodcalorie.app.viewmodel.AddStep
import com.foodcalorie.app.viewmodel.SettingsViewModel
import com.foodcalorie.app.viewmodel.StatsViewModel
import com.foodcalorie.app.viewmodel.TodayViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Years
import top.yukonga.miuix.kmp.icon.os4.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

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

private enum class MineSection {
    HUB,
    API,
    TARGET,
}

/** Nexio visual shell; food state stays owned by the existing ViewModels. */
@Composable
fun FoodAppRoot() {
    val todayVm: TodayViewModel = viewModel()
    val statsVm: StatsViewModel = viewModel()
    val addVm: AddFoodViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()

    FoodTheme {
        val context = LocalContext.current
        val glassSupported = isRuntimeShaderSupported()
        CompositionLocalProvider(LocalGlassSupported provides glassSupported, LocalOverScrollState provides remember { OverScrollState() }) {
            var selectedTab by rememberSaveable { mutableIntStateOf(0) }
            var showAdd by remember { mutableStateOf(false) }
            var mineSection by rememberSaveable { mutableStateOf(MineSection.HUB) }
            val addState by addVm.uiState.collectAsState()
            val backdrop = rememberLayerBackdrop()

            val appBarState = rememberCollapsibleTopAppBarState()
            val scrollBehavior = rememberSharedScrollBehavior(state = appBarState)
            val todayList = rememberLazyListState()
            val statsList = rememberLazyListState()
            val mineList = rememberLazyListState()
            val apiList = rememberLazyListState()
            val targetList = rememberLazyListState()
            val addList = rememberLazyListState()

            LaunchedEffect(selectedTab, mineSection) {
                appBarState.heightOffset = 0f
                appBarState.contentOffset = 0f
            }

            LaunchedEffect(Unit) {
                addVm.events.collect { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }

            BackHandler(enabled = selectedTab == 2 && mineSection != MineSection.HUB) {
                mineSection = MineSection.HUB
            }

            val showBack = selectedTab == 2 && mineSection != MineSection.HUB
            val largeTitle = when {
                selectedTab == 2 -> when (mineSection) {
                    MineSection.HUB -> "我的"
                    MineSection.API -> "识别 API"
                    MineSection.TARGET -> "每日目标"
                }
                else -> AppTab.entries[selectedTab].title
            }
            val compactTitle = when {
                selectedTab == 2 && mineSection == MineSection.API -> "识别 API"
                selectedTab == 2 && mineSection == MineSection.TARGET -> "每日目标"
                else -> AppTab.entries[selectedTab].title
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    CollapsibleTopAppBar(
                        title = compactTitle,
                        largeTitle = largeTitle,
                        scrollBehavior = scrollBehavior,
                        startAction = if (showBack) { { glassAlpha, shadowAlpha ->
                            LiquidTopBarButton(
                                onClick = { mineSection = MineSection.HUB },
                                backdrop = backdrop,
                                icon = MiuixIcons.Os4.ChevronBackward,
                                contentDescription = "返回",
                                backdropAlpha = glassAlpha,
                                shadowAlpha = shadowAlpha,
                            )
                        } } else null,
                    )
                },
                bottomBar = {
                    BottomChrome(
                        selectedTab = selectedTab,
                        onTabSelected = {
                            selectedTab = it
                            if (it == 2) mineSection = MineSection.HUB
                        },
                        onAdd = { showAdd = true },
                        backdrop = backdrop,
                    )
                },
                content = { padding ->
                    val activeList: LazyListState = when {
                        selectedTab == 0 -> todayList
                        selectedTab == 1 -> statsList
                        else -> mineList
                    }
                    @Suppress("UNUSED_VARIABLE")
                    val contentScrolled = activeList.canScrollBackward

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (glassSupported) Modifier.layerBackdrop(backdrop) else Modifier)
                            .background(MiuixTheme.colorScheme.surface),
                    ) {
                        when {
                            selectedTab == 0 -> TodayScreen(
                                viewModel = todayVm,
                                contentPadding = padding,
                                scrollBehavior = scrollBehavior,
                                listState = todayList,
                                onAddClick = { showAdd = true },
                            )
                            selectedTab == 1 -> StatsScreen(
                                viewModel = statsVm,
                                contentPadding = padding,
                                scrollBehavior = scrollBehavior,
                                listState = statsList,
                            )
                            else -> when (mineSection) {
                                MineSection.HUB -> MineHubScreen(
                                    contentPadding = padding,
                                    scrollBehavior = scrollBehavior,
                                    listState = mineList,
                                    onApi = { mineSection = MineSection.API },
                                    onTarget = { mineSection = MineSection.TARGET },
                                )
                                MineSection.API -> ApiSettingsScreen(
                                    viewModel = settingsVm,
                                    contentPadding = padding,
                                    scrollBehavior = scrollBehavior,
                                    listState = apiList,
                                )
                                MineSection.TARGET -> CalorieTargetScreen(
                                    viewModel = settingsVm,
                                    contentPadding = padding,
                                    scrollBehavior = scrollBehavior,
                                    listState = targetList,
                                )
                            }
                        }

                    }

                    OverlayBottomSheet(
                        show = showAdd,
                        title = "记录食物",
                        insideMargin = DpSize(0.dp, 0.dp),
                        startAction = {
                            IconButton(
                                modifier = Modifier.padding(start = 12.dp),
                                onClick = {
                                    if (addState.step is AddStep.PickSource) {
                                        showAdd = false
                                    } else {
                                        addVm.backToPick()
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = if (addState.step is AddStep.PickSource) {
                                        MiuixIcons.Basic.Close
                                    } else {
                                        MiuixIcons.Os4.ChevronBackward
                                    },
                                    contentDescription = if (addState.step is AddStep.PickSource) {
                                        "关闭"
                                    } else {
                                        "返回记录方式"
                                    },
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        },
                        onDismissRequest = { showAdd = false },
                    ) {
                        AddFoodRoute(
                            viewModel = addVm,
                            contentPadding = PaddingValues(0.dp),
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

@Composable
private fun BottomChrome(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onAdd: () -> Unit,
    backdrop: com.kyant.backdrop.Backdrop,
) {
    val haptics = LocalHapticFeedback.current
    val icons = listOf(MiuixIcons.Album, MiuixIcons.Years, MiuixIcons.ContactsCircle)
    val tint = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.8f)
    val select: (Int) -> Unit = {
        if (it != selectedTab) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onTabSelected(it)
        }
    }
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LiquidBottomTabs(
                selectedTabIndex = { selectedTab },
                onTabSelected = select,
                backdrop = backdrop,
                tabsCount = 3,
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                AppTab.entries.forEachIndexed { index, tab ->
                    LiquidBottomTab(onClick = { select(index) }) {
                        Icon(icons[index], contentDescription = null, modifier = Modifier.size(24.dp), tint = tint)
                        Text(tab.title, fontSize = 11.sp, color = tint)
                    }
                }
            }
            LiquidAddButton(onClick = onAdd, backdrop = backdrop)
        }
    }
}