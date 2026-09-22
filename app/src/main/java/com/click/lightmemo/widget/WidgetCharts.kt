package com.click.lightmemo.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.click.lightmemo.data.AppSettings
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.domain.RecordingHeatmap
import java.time.LocalDate

/** Small bitmap charts keep the launcher implementation independent of Compose. */
internal object WidgetCharts {
    fun heatmap(width: Int, height: Int, totals: Map<Long, Nutrition>, settings: AppSettings, today: LocalDate, dark: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val days = calendarDays(today)
        val rows = days.size / 7
        val gap = width * .032f
        val cellWidth = (width - 6 * gap) / 7f
        val cellHeight = (height - (rows - 1) * gap) / rows
        val radius = minOf(cellWidth, cellHeight) * .28f
        days.forEachIndexed { index, day ->
            val x = (index % 7) * (cellWidth + gap)
            val y = (index / 7) * (cellHeight + gap)
            val inactive = day.month != today.month || day > today
            paint.style = Paint.Style.FILL
            paint.color = if (inactive) {
                if (dark) 0xFF393939.toInt() else 0xFFECECEC.toInt()
            } else 0xFF4D83CF.toInt()
            paint.alpha = if (inactive) 255 else
                (RecordingHeatmap.intensity(totals[day.toEpochDay()]?.caloriesKcal ?: 0.0,
                    settings.dailyCalorieTarget) * 255).toInt()
            canvas.drawRoundRect(x, y, x + cellWidth, y + cellHeight, radius, radius, paint)

        }
        return bitmap
    }

    internal fun calendarDays(today: LocalDate): List<LocalDate> {
        val first = today.withDayOfMonth(1)
        val offset = first.dayOfWeek.value - 1
        val monthStart = first.minusDays(offset.toLong())
        // Keep at most five weeks, moving forward only to include today's week.
        val start = if (today >= monthStart.plusDays(35)) monthStart.plusWeeks(1) else monthStart
        return List(35) { start.plusDays(it.toLong()) }
    }

    fun intake(width: Int, height: Int, total: Nutrition, settings: AppSettings, dark: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun bar(x: Float, y: Float, w: Float, h: Float, value: Double, target: Float) {
            paint.color = if (dark) 0xFF35414D.toInt() else 0xFFE4ECF3.toInt()
            canvas.drawRoundRect(x, y, x + w, y + h, h / 2, h / 2, paint)
            paint.color = 0xFF007FFF.toInt()
            val fraction = (value / target.coerceAtLeast(1f)).coerceIn(0.0, 1.0).toFloat()
            if (fraction > 0) canvas.drawRoundRect(x, y, x + w * fraction, y + h, h / 2, h / 2, paint)
        }
        val barHeight = minOf(width * .05f, height * .086f)
        bar(0f, 0f, width.toFloat(), barHeight, total.caloriesKcal, settings.dailyCalorieTarget)
        val rows = listOf(
            total.proteinG to (settings.effectiveProteinG.takeIf { it > 0 } ?: 120f),
            total.carbsG to (settings.effectiveCarbsG.takeIf { it > 0 } ?: 250f),
            total.fatG to (settings.effectiveFatG.takeIf { it > 0 } ?: 60f),
        )
        rows.forEachIndexed { index, (value, target) ->
            val center = height * (.34f + index * .26f)
            bar(width * .34f, center - barHeight / 2, width * .66f, barHeight, value, target)
        }
        return bitmap
    }
}
