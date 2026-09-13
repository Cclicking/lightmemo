package com.click.lightmemo.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.click.lightmemo.LocalGlassSupported
import com.click.lightmemo.ui.basic.CollapsibleTopAppBar
import com.click.lightmemo.ui.basic.LiquidTopBarButton
import com.click.lightmemo.ui.basic.ProgressiveBlurTopBar
import com.click.lightmemo.ui.basic.SharedScrollBehavior
import com.click.lightmemo.ui.basic.rememberSharedScrollBehavior
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.os4.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Full-screen secondary page shell, matching NexioSchedule secondary Activities:
 * own Scaffold + collapsible top bar + back, opaque surface, no bottom bar.
 */
@Composable
fun SecondaryPageShell(
    title: String,
    onBack: () -> Unit,
    content: @Composable (padding: PaddingValues, scrollBehavior: SharedScrollBehavior) -> Unit,
) {
    val glassSupported = LocalGlassSupported.current
    val backgroundColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val scrollBehavior = rememberSharedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            var topBarBlurAlpha by remember { mutableFloatStateOf(0f) }
            ProgressiveBlurTopBar(
                backdrop = liquidGlassBackdrop,
                blurAlpha = topBarBlurAlpha,
            ) {
                CollapsibleTopAppBar(
                    title = title,
                    largeTitle = title,
                    showGradientOverlay = true,
                    scrollBehavior = scrollBehavior,
                    onAlphaChanged = { bd, _ -> topBarBlurAlpha = bd },
                    startAction = { backdropAlpha, shadowAlpha ->
                        LiquidTopBarButton(
                            onClick = onBack,
                            backdrop = liquidGlassBackdrop,
                            icon = MiuixIcons.Os4.ChevronBackward,
                            contentDescription = "返回",
                            iconSize = 25.dp,
                            iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                            backdropAlpha = backdropAlpha,
                            shadowAlpha = shadowAlpha,
                        )
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (glassSupported) {
                            Modifier.liquidGlassLayerBackdrop(liquidGlassBackdrop)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                content(padding, scrollBehavior)
            }
        }
    }
}
