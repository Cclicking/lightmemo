package com.click.lightmemo.ui.effects.edgelight

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.min

internal class EdgeLightShaderCache {
    private val cache = mutableMapOf<String, RuntimeShader>()

    // Only called after isRuntimeShaderSupported() (@ChecksSdkIntAtLeast TIRAMISU).
    @android.annotation.SuppressLint("NewApi")
    fun getOrCreate(key: String, shaderString: String): RuntimeShader {
        return cache.getOrPut(key) {
            RuntimeShader(shaderString)
        }
    }

    fun clear() {
        cache.clear()
    }
}

internal fun Canvas.clipOutline(outline: Outline, path: Path?) {
    when (outline) {
        is Outline.Rectangle -> clipRect(outline.rect)
        is Outline.Rounded -> {
            path!!.rewind()
            path.addRoundRect(outline.roundRect)
            clipPath(path)
        }
        is Outline.Generic -> clipPath(outline.path)
    }
}

internal fun androidx.compose.ui.graphics.Paint.blur(radius: Float) {
    if (radius > 0f) {
        asFrameworkPaint().apply {
            maskFilter = android.graphics.BlurMaskFilter(
                radius,
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun androidx.compose.ui.graphics.Paint.setRuntimeShader(runtimeShader: RuntimeShader?) {
    asFrameworkPaint().shader = runtimeShader
}

internal fun getCornerRadii(
    shape: Shape,
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density
): FloatArray {
    val maxRadius = min(size.width, size.height) / 2f
    val cornerShape = shape as? CornerBasedShape
        ?: return FloatArray(4) { maxRadius }
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val topLeft = if (isLtr) cornerShape.topStart.toPx(size, density) else cornerShape.topEnd.toPx(size, density)
    val topRight = if (isLtr) cornerShape.topEnd.toPx(size, density) else cornerShape.topStart.toPx(size, density)
    val bottomRight = if (isLtr) cornerShape.bottomEnd.toPx(size, density) else cornerShape.bottomStart.toPx(size, density)
    val bottomLeft = if (isLtr) cornerShape.bottomStart.toPx(size, density) else cornerShape.bottomEnd.toPx(size, density)
    return floatArrayOf(
        min(topLeft, maxRadius),
        min(topRight, maxRadius),
        min(bottomRight, maxRadius),
        min(bottomLeft, maxRadius)
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun createEdgeLightShader(
    edgeLight: EdgeLight,
    size: Size,
    shape: Shape,
    layoutDirection: LayoutDirection,
    density: Density,
    shaderCache: EdgeLightShaderCache
): RuntimeShader? {
    if (!isRuntimeShaderSupported()) return null

    val style = edgeLight.style
    val shaderString = when (style) {
        is EdgeLightStyle.Uniform -> return null
        is EdgeLightStyle.Directional -> EdgeLightDirectionalShaderString
        is EdgeLightStyle.Glow -> EdgeLightGlowShaderString
    }

    val key = "edgeLight_${style::class.simpleName}"
    val shader = shaderCache.getOrCreate(key, shaderString)

    val cornerRadii = getCornerRadii(shape, size, layoutDirection, density)
    val widthPx = with(density) { edgeLight.width.toPx() }
    val blurRadiusPx = with(density) { edgeLight.blurRadius.toPx() }

    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("cornerRadii", cornerRadii)
    shader.setFloatUniform("width", widthPx)
    shader.setFloatUniform("blurRadius", blurRadiusPx)
    shader.setFloatUniform("intensity", edgeLight.intensity)

    when (style) {
        is EdgeLightStyle.Uniform -> {}
        is EdgeLightStyle.Directional -> {
            shader.setColorUniform("color", style.color.toArgb())
            shader.setFloatUniform("angle", style.angle * (PI / 180f).toFloat())
            shader.setFloatUniform("falloff", style.falloff)
        }
        is EdgeLightStyle.Glow -> {
            shader.setColorUniform("color", style.color.toArgb())
            shader.setFloatUniform("glowSize", style.glowSize)
        }
    }

    return shader
}

private fun androidx.compose.ui.graphics.Color.toArgb(): Int {
    val red = (red * 255).toInt()
    val green = (green * 255).toInt()
    val blue = (blue * 255).toInt()
    val alpha = (alpha * 255).toInt()
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun RuntimeShader.toShaderBrush(): ShaderBrush {
    return ShaderBrush(this)
}

@ChecksSdkIntAtLeast(Build.VERSION_CODES.TIRAMISU)
internal fun isRuntimeShaderSupported(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
