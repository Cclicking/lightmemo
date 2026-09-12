package com.foodcalorie.app.ui.utils

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Wallpaper/theme forced dark override; null follows app setting. */
val LocalForcedDarkTheme = staticCompositionLocalOf<Boolean?> { null }

@Composable
fun isAppDarkTheme(): Boolean {
    LocalForcedDarkTheme.current?.let { return it }
    return rememberAppSettingDark()
}

@Composable
fun rememberAppSettingDark(): Boolean = isSystemInDarkTheme()
