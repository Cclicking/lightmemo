package com.foodcalorie.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun FoodTheme(content: @Composable () -> Unit) {
    val controller = remember {
        ThemeController(ColorSchemeMode.System)
    }
    MiuixTheme(controller = controller) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HomeWallpaperBrush(isSystemInDarkTheme())),
        ) {
            content()
        }
    }
}

/** HyperOS-style soft gradient wallpaper so glass surfaces have something to sample. */
fun HomeWallpaperBrush(dark: Boolean): Brush {
    return if (dark) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF1B1B2F),
                Color(0xFF2A1B3D),
                Color(0xFF14304A),
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFFD6E8FF),
                Color(0xFFF4E7FF),
                Color(0xFFFFF0E8),
                Color(0xFFE8F7F2),
            ),
        )
    }
}
