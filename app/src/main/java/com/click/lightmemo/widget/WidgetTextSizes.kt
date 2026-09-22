package com.click.lightmemo.widget

import android.util.TypedValue
import android.widget.RemoteViews
import com.click.lightmemo.R

/**
 * Shared widget typography based on NexioSchedule's 480 dpi reference.
 *
 * RemoteViews otherwise lets launcher density/font-scale changes make the same
 * widget look noticeably different across devices.  Applying the size in px
 * keeps the widget typography stable while the XML values still provide a
 * sensible preview before the first refresh.
 */
internal object WidgetTextSizes {
    const val REFERENCE_DENSITY = 3.0f

    private fun setTextSize(views: RemoteViews, id: Int, sp: Int) {
        views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, sp * REFERENCE_DENSITY)
    }

    fun apply(views: RemoteViews, kind: WidgetKind) {
        if (kind == WidgetKind.QUICK) return
        setTextSize(views, R.id.widget_title, if (kind == WidgetKind.SUGGESTION) 12 else 14)
        setTextSize(views, R.id.widget_value, 17)
        if (kind == WidgetKind.INTAKE) {
            setTextSize(views, R.id.widget_protein_label, 12)
            setTextSize(views, R.id.widget_carbs_label, 12)
            setTextSize(views, R.id.widget_fat_label, 12)
        }
    }
}
