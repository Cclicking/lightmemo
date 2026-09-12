package com.foodcalorie.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun FoodTheme(content: @Composable () -> Unit) {
    val controller = remember {
        ThemeController(ColorSchemeMode.System)
    }
    MiuixTheme(controller = controller) {
        content()
    }
}
