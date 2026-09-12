package com.foodcalorie.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.foodcalorie.app.ui.screens.AddFoodRoute
import com.foodcalorie.app.ui.screens.ApiSettingsScreen
import com.foodcalorie.app.ui.screens.CalorieTargetScreen
import com.foodcalorie.app.ui.screens.MineHubScreen
import com.foodcalorie.app.ui.screens.StatsScreen
import com.foodcalorie.app.ui.screens.TodayScreen
import com.foodcalorie.app.ui.theme.FoodTheme
import com.foodcalorie.app.viewmodel.AddFoodViewModel
import com.foodcalorie.app.viewmodel.SettingsViewModel
import com.foodcalorie.app.viewmodel.StatsViewModel
import com.foodcalorie.app.viewmodel.TodayViewModel
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.glass.GlassDefaults
import top.yukonga.miuix.kmp.glass.GlassColorBlendMode
import top.yukonga.miuix.kmp.glass.GlassColorLayer
import top.yukonga.miuix.kmp.glass.GlassMaterial
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassShape
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Years
import top.yukonga.miuix.kmp.icon.os4.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
            FoodAppRoot()
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

/**
 * Miuix Scaffold shell with large title [TopAppBar] / [GlassTopAppBar]
 * that collapses on scroll via [MiuixScrollBehavior].
 */
@Composable
fun FoodAppRoot() {
    val todayVm: TodayViewModel = viewModel()
    val statsVm: StatsViewModel = viewModel()
    val addVm: AddFoodViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()

    FoodTheme {
        val glassSupported = isRuntimeShaderSupported()
        CompositionLocalProvider(LocalGlassSupported provides glassSupported) {
            var selectedTab by remember { mutableIntStateOf(0) }
            var showAdd by remember { mutableStateOf(false) }
            var mineSection by remember { mutableStateOf(MineSection.HUB) }
            val backdrop = rememberLayerBackdrop()

            val appBarState = rememberTopAppBarState()
            val scrollBehavior = MiuixScrollBehavior(state = appBarState)
            val todayList = rememberLazyListState()
            val statsList = rememberLazyListState()
            val mineList = rememberLazyListState()
            val addList = rememberLazyListState()

            LaunchedEffect(selectedTab, showAdd, mineSection) {
                appBarState.heightOffset = 0f
                appBarState.contentOffset = 0f
            }

            BackHandler(enabled = showAdd || (selectedTab == 2 && mineSection != MineSection.HUB)) {
                when {
                    showAdd -> showAdd = false
                    mineSection != MineSection.HUB -> mineSection = MineSection.HUB
                }
            }

            val showBack = showAdd || (selectedTab == 2 && mineSection != MineSection.HUB)
            val largeTitle = when {
                showAdd -> "记录食物"
                selectedTab == 2 -> when (mineSection) {
                    MineSection.HUB -> "我的"
                    MineSection.API -> "识别 API"
                    MineSection.TARGET -> "每日目标"
                }
                else -> AppTab.entries[selectedTab].title
            }
            val compactTitle = when {
                showAdd -> "记录食物"
                selectedTab == 2 && mineSection == MineSection.API -> "识别 API"
                selectedTab == 2 && mineSection == MineSection.TARGET -> "每日目标"
                else -> AppTab.entries[selectedTab].title
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    val navIcon: @Composable () -> Unit = {
                        if (showBack) {
                            IconButton(onClick = {
                                when {
                                    showAdd -> showAdd = false
                                    selectedTab == 2 && mineSection != MineSection.HUB -> {
                                        mineSection = MineSection.HUB
                                    }
                                }
                            }) {
                                Icon(
                                    MiuixIcons.Os4.ChevronBackward,
                                    contentDescription = "返回",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    if (glassSupported) {
                        GlassTopAppBar(
                            title = compactTitle,
                            largeTitle = largeTitle,
                            scrollBehavior = scrollBehavior,
                            backdrop = backdrop,
                            navigationIcon = navIcon,
                        )
                    } else {
                        TopAppBar(
                            title = compactTitle,
                            largeTitle = largeTitle,
                            scrollBehavior = scrollBehavior,
                            navigationIcon = navIcon,
                        )
                    }
                },
                bottomBar = {
                    if (!showAdd) {
                        BottomChrome(
                            selectedTab = selectedTab,
                            onTabSelected = {
                                selectedTab = it
                                if (it == 2) mineSection = MineSection.HUB
                            },
                            onAdd = { showAdd = true },
                            backdrop = backdrop,
                        )
                    }
                },
                content = { padding ->
                    val activeList: LazyListState = when {
                        showAdd -> addList
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
                            showAdd -> AddFoodRoute(
                                viewModel = addVm,
                                contentPadding = padding,
                                scrollBehavior = scrollBehavior,
                                listState = addList,
                                onDone = { showAdd = false },
                            )
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
                                    listState = mineList,
                                )
                                MineSection.TARGET -> CalorieTargetScreen(
                                    viewModel = settingsVm,
                                    contentPadding = padding,
                                    scrollBehavior = scrollBehavior,
                                    listState = mineList,
                                )
                            }
                        }
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
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop,
) {
    val items = remember {
        listOf(
            GlassNavigationItem(MiuixIcons.Album, AppTab.TODAY.title),
            GlassNavigationItem(MiuixIcons.Years, AppTab.STATS.title),
            GlassNavigationItem(MiuixIcons.ContactsCircle, AppTab.MINE.title),
        )
    }
    // Preserve the library's glass geometry without its default gray body tint.
    val navigationStyle = remember {
        GlassDefaults.Style.let { style ->
            style.copy(
                blend = style.blend.copy(amount = 0f, saturation = 1f, brightness = 0f, darker = 0f),
                inner = style.inner.copy(tintStrength = 0f, colorMix = 0f, colorPow = 1f),
            )
        }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Row(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            GlassNavigationBar(
                items = items,
                selectedIndex = selectedTab,
                onSelect = onTabSelected,
                backdrop = backdrop,
                modifier = Modifier.weight(1f),
                style = navigationStyle,
            )

            FloatingActionButton(
                onClick = onAdd,
                containerColor = Color.Transparent,
                shadowElevation = 0.dp,
                minWidth = 54.dp,
                minHeight = 54.dp,
                modifier = Modifier.size(54.dp).glassPanel(
                    backdrop = if (LocalGlassSupported.current) backdrop else null,
                    shape = GlassShape(27.dp),
                    shading = false,
                    material = GlassMaterial(
                        blurRadius = 20.dp,
                        first = GlassColorLayer(
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.88f),
                            GlassColorBlendMode.SrcOver,
                        ),
                    ),
                    stroke = GlassNavigationBarDefaults.stroke(),
                    fallback = Modifier.background(MiuixTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                ),
            ) {
                Icon(
                    imageVector = MiuixIcons.Add,
                    contentDescription = "添加",
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
