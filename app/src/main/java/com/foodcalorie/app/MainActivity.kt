package com.foodcalorie.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.foodcalorie.app.ui.screens.AddFoodRoute
import com.foodcalorie.app.ui.screens.SettingsScreen
import com.foodcalorie.app.ui.screens.StatsScreen
import com.foodcalorie.app.ui.screens.TodayScreen
import com.foodcalorie.app.ui.theme.FoodTheme
import com.foodcalorie.app.viewmodel.AddFoodViewModel
import com.foodcalorie.app.viewmodel.SettingsViewModel
import com.foodcalorie.app.viewmodel.StatsViewModel
import com.foodcalorie.app.viewmodel.TodayViewModel
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Years
import top.yukonga.miuix.kmp.theme.MiuixTheme

val LocalGlassSupported = staticCompositionLocalOf { true }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
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

/**
 * App shell built on Miuix [Scaffold]:
 * - [SmallTopAppBar] for page title
 * - bottom chrome (glass nav + add FAB) in the official bottomBar slot
 * - page body consumes Scaffold [PaddingValues]
 *
 * Glass nav samples a backdrop recorded only in the content subtree, never itself.
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
            val backdrop = rememberLayerBackdrop()
            val tab = AppTab.entries[selectedTab]

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                // Let FoodTheme wallpaper show through.
                containerColor = ComposeColor.Transparent,
                topBar = {
                    if (!showAdd) {
                        SmallTopAppBar(
                            title = tab.title,
                            color = ComposeColor.Transparent,
                        )
                    }
                },
                bottomBar = {
                    if (!showAdd) {
                        BottomChrome(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            onAdd = { showAdd = true },
                            backdrop = if (glassSupported) backdrop else null,
                        )
                    }
                },
                content = { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (glassSupported) Modifier.layerBackdrop(backdrop) else Modifier),
                    ) {
                        when {
                            showAdd -> AddFoodRoute(
                                viewModel = addVm,
                                onDone = { showAdd = false },
                            )
                            selectedTab == 0 -> TodayScreen(
                                viewModel = todayVm,
                                contentPadding = padding,
                                onAddClick = { showAdd = true },
                                backdrop = null,
                            )
                            selectedTab == 1 -> StatsScreen(
                                viewModel = statsVm,
                                contentPadding = padding,
                                backdrop = null,
                            )
                            else -> SettingsScreen(
                                viewModel = settingsVm,
                                contentPadding = padding,
                                backdrop = null,
                            )
                        }
                    }
                },
            )
        }
    }
}

/** Bottom chrome: floating glass nav + blue add, placed in Scaffold.bottomBar. */
@Composable
private fun BottomChrome(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onAdd: () -> Unit,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        Box(modifier = Modifier.weight(1f, fill = false)) {
            if (backdrop != null) {
                GlassNavigationBar(
                    items = listOf(
                        GlassNavigationItem(MiuixIcons.Album, AppTab.TODAY.title),
                        GlassNavigationItem(MiuixIcons.Years, AppTab.STATS.title),
                        GlassNavigationItem(MiuixIcons.ContactsCircle, AppTab.MINE.title),
                    ),
                    selectedIndex = selectedTab,
                    onSelect = onTabSelected,
                    backdrop = backdrop,
                    selectedColor = MiuixTheme.colorScheme.onSurface,
                    unselectedColor = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.widthIn(max = 300.dp),
                )
            } else {
                FloatingNavigationBar {
                    FloatingNavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { onTabSelected(0) },
                        icon = MiuixIcons.Album,
                        label = AppTab.TODAY.title,
                    )
                    FloatingNavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { onTabSelected(1) },
                        icon = MiuixIcons.Years,
                        label = AppTab.STATS.title,
                    )
                    FloatingNavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { onTabSelected(2) },
                        icon = MiuixIcons.ContactsCircle,
                        label = AppTab.MINE.title,
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onAdd,
            containerColor = MiuixTheme.colorScheme.primary,
            shadowElevation = 6.dp,
            minWidth = 52.dp,
            minHeight = 52.dp,
            modifier = Modifier.size(52.dp),
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
