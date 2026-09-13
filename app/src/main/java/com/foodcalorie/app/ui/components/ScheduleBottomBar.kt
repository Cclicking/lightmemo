package com.foodcalorie.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Years
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Floating liquid-glass bottom navigation with optional add button.
 * Always uses the liquid glass tabs — no non-glass fallback.
 */
@Composable
internal fun ScheduleBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    liquidGlassBackdrop: Backdrop,
    addButton: @Composable () -> Unit = {},
) {
    val hapticFeedback = LocalHapticFeedback.current
    val onSelect: (Int) -> Unit = { idx ->
        if (idx != selectedTab) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            onTabSelected(idx)
        }
    }

    val iconTint = MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.8f)
    var liquidSelectedTab by remember { mutableIntStateOf(selectedTab) }
    LaunchedEffect(selectedTab) { liquidSelectedTab = selectedTab }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidBottomTabs(
                selectedTabIndex = { liquidSelectedTab },
                onTabSelected = { onSelect(it) },
                backdrop = liquidGlassBackdrop,
                tabsCount = 3,
                modifier = Modifier
                    .fillMaxWidth(0.63f)
                    .height(56.dp),
            ) {
                LiquidBottomTab({ onSelect(0) }) {
                    Image(
                        modifier = Modifier.size(24.dp),
                        imageVector = MiuixIcons.Album,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconTint),
                    )
                    Text("今日", fontSize = 11.sp, color = iconTint)
                }
                LiquidBottomTab({ onSelect(1) }) {
                    Image(
                        modifier = Modifier.size(24.dp),
                        imageVector = MiuixIcons.Years,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconTint),
                    )
                    Text("统计", fontSize = 11.sp, color = iconTint)
                }
                LiquidBottomTab({ onSelect(2) }) {
                    Image(
                        modifier = Modifier.size(24.dp),
                        imageVector = MiuixIcons.ContactsCircle,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconTint),
                    )
                    Text("我的", fontSize = 11.sp, color = iconTint)
                }
            }
            addButton()
        }
    }
}
