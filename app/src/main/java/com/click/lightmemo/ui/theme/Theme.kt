package com.click.lightmemo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.click.lightmemo.ui.platform.NativeTextContextMenuHost
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun FoodTheme(content: @Composable () -> Unit) {
    val controller = remember {
        ThemeController(ColorSchemeMode.System)
    }
    MiuixTheme(controller = controller) {
        NativeTextContextMenuHost {
            content()
        }
    }
}
