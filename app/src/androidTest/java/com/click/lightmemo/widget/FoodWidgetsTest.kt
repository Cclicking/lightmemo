package com.click.lightmemo.widget

import android.content.Context
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.click.lightmemo.R
import com.click.lightmemo.data.AppSettings
import com.click.lightmemo.domain.Nutrition
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class FoodWidgetsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun systemHostCanBindAndRefreshOnlyRequestedSuggestion() = runBlocking {
        val host = AppWidgetHost(context, 48690)
        val manager = AppWidgetManager.getInstance(context)
        val prefs = WidgetPreferences(context)
        val first = host.allocateAppWidgetId()
        val second = host.allocateAppWidgetId()
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
        try {
            assertTrue(manager.bindAppWidgetIdIfAllowed(first, ComponentName(context, SuggestionWidget::class.java)))
            assertTrue(manager.bindAppWidgetIdIfAllowed(second, ComponentName(context, SuggestionWidget::class.java)))
            FoodWidgets.updateAll(context)
            val day = LocalDate.now().toEpochDay()
            val firstDish = prefs.dish(first, day)
            val secondDish = prefs.dish(second, day)
            assertNotNull(firstDish)
            FoodWidgets.updateAll(context, first)
            assertNotEquals(firstDish, prefs.dish(first, day))
            assertEquals(secondDish, prefs.dish(second, day))
        } finally {
            host.deleteAppWidgetId(first); host.deleteAppWidgetId(second)
            prefs.delete(first); prefs.delete(second)
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test fun instanceActionsAreIndependentAndRestoreWithNewIds() {
        val prefs = WidgetPreferences(context)
        val first = 90001; val second = 90002; val restored = 90003
        try {
            prefs.setAction(first, "camera", WidgetAction.MANUAL)
            assertEquals(WidgetAction.CAMERA, prefs.action(second, "camera", WidgetAction.CAMERA))
            assertEquals(WidgetAction.GALLERY, prefs.action(first, "gallery", WidgetAction.GALLERY))
            prefs.setDish(first, 1, "米饭")
            prefs.setDish(first, 2, "面条")
            assertNull(prefs.dish(first, 1))
            prefs.restore(first, restored)
            assertEquals(WidgetAction.MANUAL, prefs.action(restored, "camera", WidgetAction.CAMERA))
            assertEquals("面条", prefs.dish(restored, 2))
            assertNotEquals(FoodWidgets.pendingAction(context, first, "camera", WidgetAction.CAMERA),
                FoodWidgets.pendingAction(context, second, "camera", WidgetAction.CAMERA))
        } finally { listOf(first, second, restored).forEach(prefs::delete) }
    }

    @Test fun allRemoteViewsInflateInBothThemesAndCompactSizes() {
        instrumentation.runOnMainSync {
            for (dark in listOf(false, true)) for (dp in listOf(160, 200)) {
                val configuration = Configuration(context.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                configuration.setLocale(java.util.Locale.SIMPLIFIED_CHINESE)
                val themed = context.createConfigurationContext(configuration)
                renderSheet(themed, dark, dp)
            }
        }
    }

    @Test fun heatmapUsesHostThemeWithoutRegeneratingRemoteViews() {
        instrumentation.runOnMainSync {
            val today = LocalDate.of(2026, 9, 22)
            val rv = FoodWidgets.render(context, 92000, WidgetKind.STREAK,
                SizeF(160f, 186f), AppSettings(), emptyMap(), Nutrition(), today, "")
            for (dark in listOf(false, true, false)) {
                val config = Configuration(context.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                val host = context.createConfigurationContext(config)
                val view = rv.apply(host, FrameLayout(host))
                val drawable = view.findViewById<ImageView>(R.id.widget_chart).drawable
                val bitmap = Bitmap.createBitmap(420, 300, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, 420, 300)
                drawable.draw(Canvas(bitmap))
                // Last cell is a future day; sample its opaque center.
                assertEquals(if (dark) 0xFF393939.toInt() else 0xFFECECEC.toInt(),
                    bitmap.getPixel(395, 275))
            }
        }
    }

    @Test fun calendarAlwaysShowsFiveWeeksIncludingToday() {
        for (month in 1..12) {
            val first = LocalDate.of(2026, month, 1)
            for (day in 1..first.lengthOfMonth()) {
                val today = first.withDayOfMonth(day)
                val days = WidgetCharts.calendarDays(today)
                assertEquals(35, days.size)
                assertEquals(java.time.DayOfWeek.MONDAY, days.first().dayOfWeek)
                assertTrue(today in days)
                assertEquals(34L, java.time.temporal.ChronoUnit.DAYS.between(days.first(), days.last()))
            }
        }
    }

    @Test fun quickBackgroundKeepsRoundedCornersAndSameColorInBothThemes() {
        val images = listOf(false, true).map { dark ->
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val themed = context.createConfigurationContext(config)
            Bitmap.createBitmap(480, 180, Bitmap.Config.ARGB_8888).also { bitmap ->
                themed.getDrawable(R.drawable.widget_quick_background)!!.apply {
                    setBounds(0, 0, 480, 180)
                    draw(Canvas(bitmap))
                }
            }
        }
        assertTrue(images[0].sameAs(images[1]))
        for (image in images) {
            assertEquals(0, Color.alpha(image.getPixel(0, 0)))
            assertEquals(0, Color.alpha(image.getPixel(479, 179)))
            assertEquals(0xFF5D9FDD.toInt(), image.getPixel(240, 90))
        }
    }

    private fun renderSheet(context: Context, dark: Boolean, dp: Int) {
        val scale = context.resources.displayMetrics.density
        val width = (dp * scale).toInt()
        val gap = (20 * scale).toInt()
        val sheet = Bitmap.createBitmap(width * 2 + gap * 3, width * 2 + gap * 3, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet); canvas.drawColor(if (dark) Color.DKGRAY else Color.LTGRAY)
        val today = LocalDate.of(2026, 9, 22)
        val total = Nutrition(675.0, 43.0, 100.0, 30.0)
        val totals = (0L..9L).associate { today.minusDays(it).toEpochDay() to total }
        val settings = AppSettings(dailyCalorieTarget = 1732f)
        WidgetKind.entries.forEach { kind ->
            val height = if (kind == WidgetKind.QUICK || kind == WidgetKind.SUGGESTION) (width * .53f).toInt() else width + 78
            val rv = FoodWidgets.render(context, 91000 + kind.ordinal, kind, SizeF(dp.toFloat(), height / scale), settings, totals, total, today, "鸡腿饭饭饭")
            val view = rv.apply(context, FrameLayout(context))
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            if (kind == WidgetKind.STREAK) assertEquals("10 天", view.findViewById<TextView>(R.id.widget_value).text.toString())
            if (kind == WidgetKind.INTAKE) assertEquals("675 / 1732 Kcal", view.findViewById<TextView>(R.id.widget_value).text.toString())
            val x = when(kind) {
                WidgetKind.STREAK, WidgetKind.SUGGESTION -> gap
                WidgetKind.INTAKE, WidgetKind.QUICK -> width + gap * 2
            }
            val card = view.findViewById<View>(R.id.widget_root)
            if (kind != WidgetKind.QUICK) {
                assertEquals(card.paddingLeft, card.paddingRight)
                assertEquals(card.paddingLeft, card.paddingBottom)
                val expectedTitle = if (kind == WidgetKind.SUGGESTION) 36f else 22 * dp * scale / 283f
                assertEquals(expectedTitle, view.findViewById<TextView>(R.id.widget_title).textSize, 0.01f)
            }
            val y = when(kind) {
                WidgetKind.STREAK, WidgetKind.INTAKE -> gap
                WidgetKind.SUGGESTION, WidgetKind.QUICK -> width + 78 + gap * 2
            }
            canvas.save(); canvas.translate(x.toFloat(), y.toFloat()); view.draw(canvas); canvas.restore()
        }
        File(context.getExternalFilesDir(null), "widgets-${if (dark) "dark" else "light"}-$dp.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
