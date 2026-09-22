package com.click.lightmemo.ui.components

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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Th1
import top.yukonga.miuix.kmp.icon.extended.Th10
import top.yukonga.miuix.kmp.icon.extended.Th11
import top.yukonga.miuix.kmp.icon.extended.Th12
import top.yukonga.miuix.kmp.icon.extended.Th13
import top.yukonga.miuix.kmp.icon.extended.Th14
import top.yukonga.miuix.kmp.icon.extended.Th15
import top.yukonga.miuix.kmp.icon.extended.Th16
import top.yukonga.miuix.kmp.icon.extended.Th17
import top.yukonga.miuix.kmp.icon.extended.Th18
import top.yukonga.miuix.kmp.icon.extended.Th19
import top.yukonga.miuix.kmp.icon.extended.Th2
import top.yukonga.miuix.kmp.icon.extended.Th20
import top.yukonga.miuix.kmp.icon.extended.Th21
import top.yukonga.miuix.kmp.icon.extended.Th22
import top.yukonga.miuix.kmp.icon.extended.Th23
import top.yukonga.miuix.kmp.icon.extended.Th24
import top.yukonga.miuix.kmp.icon.extended.Th25
import top.yukonga.miuix.kmp.icon.extended.Th26
import top.yukonga.miuix.kmp.icon.extended.Th27
import top.yukonga.miuix.kmp.icon.extended.Th28
import top.yukonga.miuix.kmp.icon.extended.Th29
import top.yukonga.miuix.kmp.icon.extended.Th3
import top.yukonga.miuix.kmp.icon.extended.Th30
import top.yukonga.miuix.kmp.icon.extended.Th31
import top.yukonga.miuix.kmp.icon.extended.Th4
import top.yukonga.miuix.kmp.icon.extended.Th5
import top.yukonga.miuix.kmp.icon.extended.Th6
import top.yukonga.miuix.kmp.icon.extended.Th7
import top.yukonga.miuix.kmp.icon.extended.Th8
import top.yukonga.miuix.kmp.icon.extended.Th9
import top.yukonga.miuix.kmp.icon.extended.Years
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val todayIcons: List<ImageVector> = listOf(
    MiuixIcons.Th1,
    MiuixIcons.Th2,
    MiuixIcons.Th3,
    MiuixIcons.Th4,
    MiuixIcons.Th5,
    MiuixIcons.Th6,
    MiuixIcons.Th7,
    MiuixIcons.Th8,
    MiuixIcons.Th9,
    MiuixIcons.Th10,
    MiuixIcons.Th11,
    MiuixIcons.Th12,
    MiuixIcons.Th13,
    MiuixIcons.Th14,
    MiuixIcons.Th15,
    MiuixIcons.Th16,
    MiuixIcons.Th17,
    MiuixIcons.Th18,
    MiuixIcons.Th19,
    MiuixIcons.Th20,
    MiuixIcons.Th21,
    MiuixIcons.Th22,
    MiuixIcons.Th23,
    MiuixIcons.Th24,
    MiuixIcons.Th25,
    MiuixIcons.Th26,
    MiuixIcons.Th27,
    MiuixIcons.Th28,
    MiuixIcons.Th29,
    MiuixIcons.Th30,
    MiuixIcons.Th31,
)

/** Keeps the day icon correct even when the app stays open across midnight. */
@Composable
private fun rememberTodayIcon(): ImageVector {
    val dayOfMonth by produceState(initialValue = LocalDate.now().dayOfMonth) {
        while (true) {
            val now = LocalDateTime.now()
            value = now.dayOfMonth
            val nextDay = now.toLocalDate().plusDays(1).atStartOfDay()
            delay(Duration.between(now, nextDay).toMillis().coerceAtLeast(1L))
        }
    }
    return todayIcons[dayOfMonth - 1]
}

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
    val todayIcon = rememberTodayIcon()
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
                        imageVector = todayIcon,
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
                        imageVector = MiuixIcons.Album,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconTint),
                    )
                    Text("推荐", fontSize = 11.sp, color = iconTint)
                }
            }
            addButton()
        }
    }
}
