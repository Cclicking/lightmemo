package com.foodcalorie.app.ui.nav

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
sealed interface MineRoute : NavKey {
    @Serializable
    data object Hub : MineRoute

    @Serializable
    data object Profile : MineRoute

    @Serializable
    data object Api : MineRoute

    @Serializable
    data object Target : MineRoute

    @Serializable
    data object Database : MineRoute

    @Serializable
    data object Appearance : MineRoute

    @Serializable
    data object About : MineRoute
}

fun MineRoute.largeTitle(): String = when (this) {
    MineRoute.Hub -> "我的"
    MineRoute.Profile -> "个人信息"
    MineRoute.Api -> "AI设置"
    MineRoute.Target -> "每日目标"
    MineRoute.Database -> "数据库设置"
    MineRoute.Appearance -> "个性化设置"
    MineRoute.About -> "关于"
}
