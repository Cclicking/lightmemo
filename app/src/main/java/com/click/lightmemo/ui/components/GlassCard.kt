package com.click.lightmemo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.click.lightmemo.LocalGlassSupported
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.glass.GlassShape
import top.yukonga.miuix.kmp.glass.GlassStyle
import top.yukonga.miuix.kmp.glass.GlassStyles
import top.yukonga.miuix.kmp.glass.glassPanel

/** Glass card with readable, rounded fallback when not sampling a backdrop. */
@Composable
fun GlassCard(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: GlassShape = GlassShape(22.dp),
    style: GlassStyle = GlassStyles.CommonMediumRegularLowLight,
    fallbackColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    val resolvedFallback = if (fallbackColor == Color.Unspecified) {
        if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.55f)
    } else {
        fallbackColor
    }
    val backdropOrNull = backdrop?.takeIf { LocalGlassSupported.current && isRuntimeShaderSupported() }
    Box(
        modifier = modifier
            .then(
                if (backdropOrNull != null) {
                    Modifier.glassPanel(
                        backdrop = backdropOrNull,
                        shape = shape,
                        style = style,
                        fallback = Modifier.clip(shape).background(resolvedFallback),
                    )
                } else {
                    Modifier.clip(shape).background(resolvedFallback)
                },
            )
            .padding(contentPadding),
    ) {
        content()
    }
}
