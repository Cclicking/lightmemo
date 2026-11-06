package com.click.lightmemo.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.SizeF
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetPreviewSizeTest {
    @Test fun suppliedSizesKeepEachOrientationsWidthAndHeightTogether() {
        val portrait = SizeF(146f, 285f)
        val landscape = SizeF(285f, 146f)
        val options = Bundle().apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(portrait, landscape))
        }
        assertEquals(portrait, widgetPreviewSize(options, WidgetKind.INTAKE, landscape = false))
        assertEquals(landscape, widgetPreviewSize(options, WidgetKind.INTAKE, landscape = true))
    }

    @Test fun legacySizeRangesDoNotProduceAnArtificialSquare() {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 146)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 285)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 146)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 285)
        }
        assertEquals(SizeF(146f, 285f), widgetPreviewSize(options, WidgetKind.INTAKE, landscape = false))
        assertEquals(SizeF(285f, 146f), widgetPreviewSize(options, WidgetKind.INTAKE, landscape = true))
    }

    @Test fun missingOptionsUseTheWidgetsActualShape() {
        assertEquals(SizeF(180f, 180f), widgetPreviewSize(Bundle(), WidgetKind.INTAKE, landscape = false))
        assertEquals(SizeF(180f, 90f), widgetPreviewSize(Bundle(), WidgetKind.QUICK, landscape = false))
    }
}
