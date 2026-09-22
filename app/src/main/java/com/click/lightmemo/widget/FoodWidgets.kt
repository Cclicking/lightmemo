package com.click.lightmemo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Paint
import android.util.TypedValue
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.util.SizeF
import android.widget.RemoteViews
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import com.click.lightmemo.EXTRA_SHORTCUT
import com.click.lightmemo.FoodApp
import com.click.lightmemo.MainActivity
import com.click.lightmemo.R
import com.click.lightmemo.data.AppSettings
import com.click.lightmemo.data.PresetFood
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.Nutrition
import com.click.lightmemo.domain.RecordingHeatmap
import com.click.lightmemo.ui.screens.buildRecommendations
import com.click.lightmemo.viewmodel.StatsViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

enum class WidgetKind(val title: String, val layout: Int, val provider: Class<out FoodWidgetProvider>, val defaultAction: WidgetAction) {
    STREAK("连续记录 · 2×2", R.layout.widget_streak, StreakWidget::class.java, WidgetAction.STATS),
    INTAKE("今日摄入 · 2×2", R.layout.widget_intake, IntakeWidget::class.java, WidgetAction.TODAY),
    SUGGESTION("饮食建议 · 2×1", R.layout.widget_suggestion, SuggestionWidget::class.java, WidgetAction.RECOMMEND),
    QUICK("快捷记录 · 2×1", R.layout.widget_quick, QuickWidget::class.java, WidgetAction.CAMERA),
}

class StreakWidget : FoodWidgetProvider()
class IntakeWidget : FoodWidgetProvider()
class SuggestionWidget : FoodWidgetProvider()
class QuickWidget : FoodWidgetProvider()

open class FoodWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in setOf(REFRESH, Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_CONFIGURATION_CHANGED)) {
            update(context, if (intent.action == REFRESH) intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) else null)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = update(context)
    override fun onDeleted(context: Context, ids: IntArray) { ids.forEach { WidgetPreferences(context).delete(it) } }
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val preferences = WidgetPreferences(context)
        oldWidgetIds.zip(newWidgetIds).forEach { (old, new) -> preferences.restore(old, new) }
        update(context)
    }

    private fun update(context: Context, refresh: Int? = null) {
        val pending = goAsync()
        (context.applicationContext as FoodApp).appScope.launch {
            try { withTimeout(8_000) { FoodWidgets.updateAll(context, refresh) } }
            catch (error: Exception) { android.util.Log.w("FoodWidgets", "Widget update deferred", error) }
            finally { pending.finish() }
        }
    }

    companion object { const val REFRESH = "com.click.lightmemo.widget.REFRESH" }
}

object FoodWidgets {
    const val EXTRA_DESTINATION = "com.click.lightmemo.widget.DESTINATION"
    const val EXTRA_DISH = "com.click.lightmemo.widget.DISH"
    private val mutex = Mutex()

    fun instances(context: Context): List<Pair<Int, WidgetKind>> {
        val manager = AppWidgetManager.getInstance(context)
        return WidgetKind.entries.flatMap { kind ->
            manager.getAppWidgetIds(ComponentName(context, kind.provider)).map { it to kind }
        }
    }

    suspend fun updateAll(context: Context, refreshId: Int? = null) = mutex.withLock {
        val instances = instances(context)
        if (instances.isEmpty()) return@withLock
        val app = context.applicationContext as FoodApp
        app.foodLogRepository.ensureMigrated()
        val logs = app.foodLogRepository.allLogs().first()
        val settings = app.settingsRepository.settings.first()
        val presets = app.settingsRepository.foodPresets.first()
        val today = LocalDate.now()
        val totals = logs.groupBy { it.dateEpochDay }.mapValues { (_, items) -> items.fold(Nutrition()) { a, b -> a + b.nutrition } }
        val total = totals[today.toEpochDay()] ?: Nutrition()
        val candidates = recommendations(logs, settings, presets, total)
        val manager = AppWidgetManager.getInstance(context)
        val preferences = WidgetPreferences(context)
        instances.forEach { (id, kind) ->
            var dish = preferences.dish(id, today.toEpochDay())
            if (kind == WidgetKind.SUGGESTION && (refreshId == id || dish !in candidates)) {
                dish = candidates.filterNot { it == dish }.randomOrNull() ?: candidates.firstOrNull() ?: "打开饮食推荐"
                preferences.setDish(id, today.toEpochDay(), dish)
            }
            val options = manager.getAppWidgetOptions(id)
            val supplied = BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java).orEmpty()
            val sizes = supplied.distinct().take(4).ifEmpty {
                val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110).coerceAtLeast(110)
                val minHeight = if (kind == WidgetKind.QUICK || kind == WidgetKind.SUGGESTION) 40 else 110
                val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minHeight).coerceAtLeast(minHeight)
                listOf(SizeF(w.toFloat(), h.toFloat()))
            }
            val views = sizes.associateWith { size -> render(context, id, kind, size, settings, totals, total, today, dish.orEmpty()) }
            manager.updateAppWidget(id, if (views.size == 1) views.values.first() else RemoteViews(views))
        }
    }

    private fun recommendations(logs: List<FoodLog>, settings: AppSettings, presets: List<PresetFood>, total: Nutrition): List<String> {
        val recorded = logs.filter { it.name.isNotBlank() }.groupBy { it.name.trim() }.map { (name, items) ->
            val latest = items.maxBy { it.createdAtMillis }
            PresetFood("recorded:$name", name, latest.grams.coerceAtLeast(1.0), "历史记录", latest.nutrition, latest.components)
        }
        return buildRecommendations(StatsViewModel.StatsUiState(
            todayNutrition = total, target = settings.dailyCalorieTarget,
            proteinTarget = settings.effectiveProteinG.takeIf { it > 0 } ?: 120f,
            carbsTarget = settings.effectiveCarbsG.takeIf { it > 0 } ?: 250f,
            fatTarget = settings.effectiveFatG.takeIf { it > 0 } ?: 60f,
            foodPresets = presets, recordedFoods = recorded,
            foodFrequency = logs.groupingBy { it.name.trim() }.eachCount(),
        )).map { it.preset.name }
    }

    internal fun render(context: Context, id: Int, kind: WidgetKind, size: SizeF, settings: AppSettings,
        totals: Map<Long, Nutrition>, total: Nutrition, today: LocalDate, dish: String): RemoteViews {
        val views = RemoteViews(context.packageName, kind.layout)
        WidgetTextSizes.apply(views, kind)
        val preferences = WidgetPreferences(context)
        val density = context.resources.displayMetrics.density
        val cardWidth = size.width * density
        val chartCard = kind == WidgetKind.STREAK || kind == WidgetKind.INTAKE
        // Design reference: 283 px card, 28 px inset, 22 px title, 40 px value.
        val designScale = cardWidth / 283f
        val padding = if (chartCard) (28 * designScale).toInt() else (12 * density).toInt()
        if (chartCard) {
            views.setViewPadding(R.id.widget_root, padding, padding, padding, padding)
            views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_PX, 22 * designScale)
            views.setTextViewTextSize(R.id.widget_value, TypedValue.COMPLEX_UNIT_PX, 40 * designScale)
            if (kind == WidgetKind.INTAKE) {
                listOf(R.id.widget_protein_label, R.id.widget_carbs_label, R.id.widget_fat_label).forEach {
                    views.setTextViewTextSize(it, TypedValue.COMPLEX_UNIT_PX, 22 * designScale)
                }
            }
        }
        fun textHeight(px: Float, bold: Boolean = false): Int {
            val paint = Paint().apply {
                textSize = px
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            return paint.fontMetricsInt.let { it.descent - it.ascent }
        }
        // Render at the actual display size; reserve the fixed outer launcher spacing.
        val width = (cardWidth - padding * 2).toInt().coerceAtLeast(1)
        val height = (size.height * density - 78 - padding * 2 -
            textHeight(22 * designScale) - textHeight(40 * designScale, true) - 4 * density)
            .toInt().coerceAtLeast(1)
        fun click(view: Int, slot: String, default: WidgetAction) {
            val action = preferences.action(id, slot, default)
            views.setOnClickPendingIntent(view, pendingAction(context, id, slot, action))
            views.setContentDescription(view, action.title)
        }
        when (kind) {
            WidgetKind.STREAK -> {
                val streak = RecordingHeatmap.streak(totals.keys, today)
                views.setTextViewText(R.id.widget_value, valueText("$streak", " 天"))
                // The launcher resolves day/night itself, even when our process is stopped.
                views.setIcon(R.id.widget_chart, "setImageIcon",
                    Icon.createWithBitmap(WidgetCharts.heatmap(width, height, totals, settings, today, false)),
                    Icon.createWithBitmap(WidgetCharts.heatmap(width, height, totals, settings, today, true)))
                click(R.id.widget_root, "body", kind.defaultAction)
                views.setContentDescription(R.id.widget_root, "连续记录 $streak 天，本月记录。${preferences.action(id, "body", kind.defaultAction).title}")
            }
            WidgetKind.INTAKE -> {
                views.setTextViewText(R.id.widget_value, valueText("${total.caloriesKcal.toInt()}", " / ${settings.dailyCalorieTarget.toInt()} Kcal"))
                views.setIcon(R.id.widget_chart, "setImageIcon",
                    Icon.createWithBitmap(WidgetCharts.intake(width, height, total, settings, false)),
                    Icon.createWithBitmap(WidgetCharts.intake(width, height, total, settings, true)))
                click(R.id.widget_root, "body", kind.defaultAction)
                views.setContentDescription(R.id.widget_root, "今日摄入 ${total.caloriesKcal.toInt()} 千卡，目标 ${settings.dailyCalorieTarget.toInt()} 千卡，蛋白质 ${total.proteinG.toInt()} 克，碳水 ${total.carbsG.toInt()} 克，脂肪 ${total.fatG.toInt()} 克。${preferences.action(id, "body", kind.defaultAction).title}")
            }
            WidgetKind.SUGGESTION -> {
                views.setTextViewText(R.id.widget_value, dish)
                click(R.id.widget_body, "body", kind.defaultAction)
                if (preferences.action(id, "body", kind.defaultAction) == WidgetAction.RECOMMEND) {
                    views.setOnClickPendingIntent(R.id.widget_body, pendingAction(context, id, "body", WidgetAction.RECOMMEND, dish))
                }
                views.setContentDescription(R.id.widget_body, "建议 $dish。${preferences.action(id, "body", kind.defaultAction).title}")
                val intent = Intent(context, SuggestionWidget::class.java).setAction(FoodWidgetProvider.REFRESH)
                    .setData("lightmemo-widget://refresh/$id".toUri())
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                views.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            WidgetKind.QUICK -> {
                click(R.id.widget_camera, "camera", WidgetAction.CAMERA)
                click(R.id.widget_gallery, "gallery", WidgetAction.GALLERY)
                click(R.id.widget_manual, "manual", WidgetAction.MANUAL)
            }
        }
        return views
    }

    private fun valueText(value: String, suffix: String) = SpannableString(value + suffix).apply {
        setSpan(StyleSpan(Typeface.BOLD), 0, value.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(RelativeSizeSpan(.60f), value.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun pendingAction(context: Context, id: Int, slot: String, action: WidgetAction, dish: String? = null): PendingIntent {
        val intent = Intent(context, if (action == WidgetAction.SETTINGS) WidgetSettingsActivity::class.java else MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW).setData("lightmemo-widget://action/$id/$slot/${action.route}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (action == WidgetAction.SETTINGS) intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        else if (action in listOf(WidgetAction.CAMERA, WidgetAction.GALLERY, WidgetAction.MANUAL)) {
            intent.putExtra(EXTRA_SHORTCUT, action.route).putExtra(EXTRA_DESTINATION, "today")
        } else intent.putExtra(EXTRA_DESTINATION, action.route)
        dish?.let { intent.putExtra(EXTRA_DISH, it) }
        return PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
