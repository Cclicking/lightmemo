package com.click.lightmemo.widget

import android.appwidget.AppWidgetManager
import android.content.res.Configuration
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import androidx.core.os.BundleCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.click.lightmemo.data.AppSettings
import com.click.lightmemo.domain.Nutrition
import java.time.LocalDate

/** Render the real instance as an image, so preview taps cannot execute widget actions. */
@Composable
internal fun WidgetInstancePreview(
    id: Int,
    kind: WidgetKind,
    settings: AppSettings,
    totals: Map<Long, Nutrition>,
    revision: Int = 0,
    appearance: WidgetAppearance? = null,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val today = LocalDate.now()
    val size = remember(context, id, kind, configuration) {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
        widgetPreviewSize(options, kind, configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }
    val bitmap = remember(context, id, kind, settings, totals, today, configuration, revision, appearance) {
        val preferences = WidgetPreferences(context)
        val remoteViews = FoodWidgets.render(
            context, id, kind, size, settings, totals, totals[today.toEpochDay()] ?: Nutrition(), today,
            preferences.dish(id, today.toEpochDay()).orEmpty(),
            appearance = appearance,
        )
        val parent = FrameLayout(context)
        val view = remoteViews.apply(context, parent)
        val density = context.resources.displayMetrics.density
        val width = (size.width * density).toInt().coerceAtLeast(1)
        val height = (size.height * density).toInt().coerceAtLeast(1)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }.asImageBitmap()
    }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val previewWidth = minOf(maxWidth, 200.dp, (200f * size.width / size.height).dp)
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.width(previewWidth).aspectRatio(size.width / size.height),
        )
    }
}

internal fun widgetPreviewSize(options: Bundle, kind: WidgetKind, landscape: Boolean): SizeF {
    val supplied = BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java).orEmpty()
    // Min width and min height describe different orientations; never combine those extremes.
    val size = if (landscape) supplied.maxByOrNull { it.width } else supplied.minByOrNull { it.width }
    if (size != null && size.width > 0f && size.height > 0f) return size
    val height = if (kind == WidgetKind.QUICK || kind == WidgetKind.SUGGESTION) 90 else 180
    return SizeF(
        options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
            .coerceAtLeast(110).toFloat(),
        options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
            .coerceAtLeast(40).toFloat(),
    )
}
