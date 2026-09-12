package com.foodcalorie.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foodcalorie.app.LocalGlassSupported
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassShape
import top.yukonga.miuix.kmp.glass.GlassStyle
import top.yukonga.miuix.kmp.glass.GlassStyles
import top.yukonga.miuix.kmp.glass.glassPanel

/** Glass card with readable fallback when the device has no runtime shaders. */
@Composable
fun GlassCard(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: GlassShape = GlassShape(22.dp),
    style: GlassStyle = GlassStyles.CommonMediumRegularLowLight,
    fallbackColor: Color = Color.White.copy(alpha = 0.55f),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    val supported = LocalGlassSupported.current && backdrop != null
    Box(
        modifier = modifier
            .then(
                if (supported && backdrop != null) {
                    Modifier.glassPanel(
                        backdrop = backdrop,
                        shape = shape,
                        style = style,
                        fallback = Modifier.background(fallbackColor),
                    )
                } else {
                    Modifier.background(fallbackColor)
                },
            )
            .padding(contentPadding),
    ) {
        content()
    }
}

@Composable
fun ScreenBackdropHost(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    content: @Composable (Backdrop?) -> Unit,
) {
    // Backdrop recording is owned by the root; pages just receive it.
    Box(modifier = modifier.fillMaxSize()) {
        content(backdrop)
    }
}

val PageHorizontalPadding: Dp = 16.dp
