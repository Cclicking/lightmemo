package com.foodcalorie.app

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalDensity
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
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.os4.Image
import top.yukonga.miuix.kmp.icon.os4.Settings

val LocalGlassSupported = staticCompositionLocalOf { true }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
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
            val density = LocalDensity.current
            val navInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
            val bottomPad = (navInset + 16.dp).coerceAtLeast(20.dp)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (!showAdd) {
                            Box(
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .padding(bottom = bottomPad)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                if (glassSupported) {
                                    val items = listOf(
                                        GlassNavigationItem(MiuixIcons.Home, "今日"),
                                        GlassNavigationItem(MiuixIcons.Os4.Image, "统计"),
                                        GlassNavigationItem(MiuixIcons.Os4.Settings, "设置"),
                                    )
                                    GlassNavigationBar(
                                        items = items,
                                        selectedIndex = selectedTab,
                                        onSelect = { selectedTab = it },
                                        backdrop = backdrop,
                                    )
                                } else {
                                    FloatingNavigationBar {
                                        FloatingNavigationBarItem(
                                            selected = selectedTab == 0,
                                            onClick = { selectedTab = 0 },
                                            icon = MiuixIcons.Home,
                                            label = "今日",
                                        )
                                        FloatingNavigationBarItem(
                                            selected = selectedTab == 1,
                                            onClick = { selectedTab = 1 },
                                            icon = MiuixIcons.Os4.Image,
                                            label = "统计",
                                        )
                                        FloatingNavigationBarItem(
                                            selected = selectedTab == 2,
                                            onClick = { selectedTab = 2 },
                                            icon = MiuixIcons.Os4.Settings,
                                            label = "设置",
                                        )
                                    }
                                }
                            }
                        }
                    },
                ) { padding ->
                    when {
                        showAdd -> AddFoodRoute(
                            viewModel = addVm,
                            onDone = { showAdd = false },
                        )
                        selectedTab == 0 -> TodayScreen(
                            viewModel = todayVm,
                            contentPadding = padding,
                            onAddClick = { showAdd = true },
                            backdrop = backdrop,
                        )
                        selectedTab == 1 -> StatsScreen(
                            viewModel = statsVm,
                            contentPadding = padding,
                            backdrop = backdrop,
                        )
                        else -> SettingsScreen(
                            viewModel = settingsVm,
                            contentPadding = padding,
                            backdrop = backdrop,
                        )
                    }
                }
            }
        }
    }
}
