package com.click.lightmemo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.click.lightmemo.LocalGlassSupported
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.glass.GlassTabRow
import top.yukonga.miuix.kmp.theme.MiuixTheme

val LocalGlassControlsBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
internal fun glassControlsEnabled(): Boolean =
    isRuntimeShaderSupported()

@Composable
internal fun glassControlsMaterialEnabled(): Boolean =
    LocalGlassSupported.current && isRuntimeShaderSupported()

/** Inline selectors use the resting palette rather than sampling their own recorded layer. */
@Composable
fun GlassTabs(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    segmented: Boolean = false,
) {
    if (!glassControlsEnabled()) {
        if (segmented) {
            TabRowWithContour(
                tabs = tabs,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = onTabSelected,
                modifier = modifier,
            )
        } else {
            TabRow(
                tabs = tabs,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = onTabSelected,
                modifier = modifier,
            )
        }
        return
    }

    // A separate backing layer avoids recording the tabs into their own glass input.
    // A null backdrop with surfaceAlpha=0 also makes the upstream fallback hide the labels.
    val pageColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop { drawRect(pageColor) }
    Box(modifier) {
        Box(Modifier.matchParentSize().layerBackdrop(backdrop))
        if (segmented) {
            GlassSegmentedTabRow(
                tabs = tabs,
                selectedIndex = selectedTabIndex,
                onSelect = onTabSelected,
                backdrop = backdrop,
                surfaceAlpha = 0f,
            )
        } else {
            GlassTabRow(
                tabs = tabs,
                selectedIndex = selectedTabIndex,
                onSelect = onTabSelected,
                backdrop = backdrop,
                surfaceAlpha = 0f,
            )
        }
    }
}
