package com.click.lightmemo.ui.nav

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastRoundToInt
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition

/**
 * AOSP `activity_open_*` / `activity_close_*` geometry:
 * - entering/leaving top: full-width horizontal slide from/to the trailing edge
 * - covered: 30% parallax toward the leading edge (no extra dim, rectangular pages)
 */
val AndroidActivityTransition: NavTransition = navGraphicsTransition(
    opaqueDepth = 1f,
    motion = NavMotion(
        // Programmatic push/pop follows Activity's fast-out-slow-in ~300ms feel.
        programmatic = NavSettleSpec.Tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing,
        ),
    ),
) { scope ->
    val width = scope.layoutSize.width.toFloat()
    val d = scope.relativeDepth
    val rtl = scope.layoutDirection == LayoutDirection.Rtl
    if (d <= 0f) {
        translationX = ((if (rtl) -1f else 1f) * (-d).coerceIn(0f, 1f) * width)
            .fastRoundToInt()
            .toFloat()
    } else {
        val cover = d.coerceIn(0f, 1f)
        translationX = (if (rtl) 1f else -1f) * cover * width * 0.30f
    }
}

/**
 * Activity pages stay rectangular (no iOS-style leading corner clip).
 * [backdropColor] paints an opaque surface behind every entry so a sliding
 * LazyColumn never reveals a transparent hole — same idea as NexioSchedule
 * secondary Activities drawing `drawRect(surface)` under their content.
 */
fun androidActivityEffects(backdropColor: Color): NavDisplayEffects = NavDisplayEffects(
    enableCornerClip = false,
    dimAmount = 0f,
    blockInputDuringTransition = false,
    backdropColor = backdropColor,
)
