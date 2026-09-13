package com.click.lightmemo.ui.secondary

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.OnBackInvokedDefaultInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.click.lightmemo.LocalGlassSupported
import com.click.lightmemo.ui.nav.SecondaryPageShell
import com.click.lightmemo.ui.screens.TodayScreen
import com.click.lightmemo.ui.theme.FoodTheme
import com.click.lightmemo.ui.theme.toComposeColors
import com.click.lightmemo.ui.utils.LocalOverScrollState
import com.click.lightmemo.ui.utils.OverScrollState
import com.click.lightmemo.viewmodel.AddFoodUiState
import com.click.lightmemo.viewmodel.TodayViewModel
import com.kyant.backdrop.isRenderEffectSupported

/** Full-screen manage-food-cards page with the system Activity transition. */
class ManageFoodCardsActivity : ComponentActivity() {
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
                val settingsVm: com.click.lightmemo.viewmodel.SettingsViewModel = viewModel()
                val settings by settingsVm.settings.collectAsState()
                FoodTheme {
                    val todayVm: TodayViewModel = viewModel()
                    val palette = settings.colorPalette.toComposeColors()
                    SecondaryPageShell(title = "管理食物卡片", onBack = { finish() }) { padding, scroll ->
                        TodayScreen(
                            viewModel = todayVm,
                            contentPadding = padding,
                            scrollBehavior = scroll,
                            listState = rememberLazyListState(),
                            addState = AddFoodUiState(),
                            managementMode = true,
                            showDatePicker = false,
                            onShowDatePicker = {},
                            onDismissDatePicker = {},
                            onAddClick = {},
                            palette = palette,
                        )
                    }
                }
            }
        }
    }
}
