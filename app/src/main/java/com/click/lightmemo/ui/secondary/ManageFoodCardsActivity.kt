package com.click.lightmemo.ui.secondary

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.ui.basic.LiquidTopBarButton
import com.click.lightmemo.ui.components.AnimatedOverlayDialog
import com.click.lightmemo.ui.nav.SecondaryPageShell
import com.click.lightmemo.ui.screens.TodayScreen
import com.click.lightmemo.ui.theme.FoodTheme
import com.click.lightmemo.ui.theme.toComposeColors
import com.click.lightmemo.ui.utils.LocalOverScrollState
import com.click.lightmemo.ui.utils.OverScrollState
import com.click.lightmemo.viewmodel.AddFoodUiState
import com.click.lightmemo.viewmodel.TodayViewModel
import com.kyant.backdrop.isRenderEffectSupported
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.os4.Ok

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
                    val busy by todayVm.busy.collectAsState()
                    var pendingEntries by remember { mutableStateOf<Map<Long, FoodLog>>(emptyMap()) }
                    var showDiscardDialog by remember { mutableStateOf(false) }
                    val palette = settings.colorPalette.toComposeColors()

                    fun requestBack() {
                        if (busy) return
                        if (pendingEntries.isEmpty()) finish() else showDiscardDialog = true
                    }

                    fun saveChanges() {
                        if (busy) return
                        if (pendingEntries.isEmpty()) {
                            finish()
                        } else {
                            todayVm.updateAll(pendingEntries.values) {
                                setResult(android.app.Activity.RESULT_OK)
                                finish()
                            }
                        }
                    }

                    BackHandler(
                        enabled = pendingEntries.isNotEmpty() && !busy && !showDiscardDialog,
                        onBack = ::requestBack,
                    )

                    SecondaryPageShell(
                        title = "管理食物卡片",
                        onBack = ::requestBack,
                        endAction = { backdrop, backdropAlpha, shadowAlpha ->
                            LiquidTopBarButton(
                                onClick = ::saveChanges,
                                backdrop = backdrop,
                                icon = MiuixIcons.Os4.Ok,
                                contentDescription = "保存",
                                backdropAlpha = backdropAlpha,
                                shadowAlpha = shadowAlpha,
                            )
                        },
                    ) { padding, scroll ->
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
                            managementDrafts = pendingEntries,
                            onManagementDraftChange = { updated ->
                                pendingEntries = pendingEntries + (updated.id to updated)
                            },
                        )
                        AnimatedOverlayDialog(
                            show = showDiscardDialog,
                            title = "放弃修改？",
                            summary = "返回后将丢失尚未保存的食物份量调整。",
                            onDismissRequest = { showDiscardDialog = false },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = { showDiscardDialog = false },
                                    modifier = Modifier.weight(1f),
                                ) { Text("取消") }
                                Button(
                                    onClick = {
                                        showDiscardDialog = false
                                        finish()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                ) { Text("放弃") }
                            }
                        }
                    }
                }
            }
        }
    }
}
