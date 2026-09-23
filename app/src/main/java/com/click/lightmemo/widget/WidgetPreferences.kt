package com.click.lightmemo.widget

import android.content.Context

enum class WidgetAction(val title: String, val route: String) {
    TODAY("打开今日摄入", "today"), STATS("打开统计", "stats"),
    RECOMMEND("打开饮食推荐", "recommend"), CAMERA("拍照识别", "camera"),
    GALLERY("从相册选择", "gallery"), MANUAL("文字录入", "manual"),
    SETTINGS("打开小组件设置", "settings"),
}

enum class WidgetMarginPreset(
    val title: String,
    val topDp: Int?,
    val bottomDp: Int?,
) {
    STANDARD("标准（不增加外边距）", 0, 0),
    DESKTOP_4X6("4×6 桌面（顶部 36px，底部 42px）", null, null),
    DESKTOP_4X7("4×7 桌面（顶部 12dp）", 12, 0),
    CUSTOM("自定义上下外边距", null, null),
}

class WidgetPreferences(context: Context) {
    private val store = context.getSharedPreferences("desktop_widgets", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_CHART_RADIUS_DP = 28f
        const val DEFAULT_STANDARD_RADIUS_DP = 22f
        const val MIN_RADIUS_DP = 0f
        const val MAX_RADIUS_DP = 48f
        const val MIN_MARGIN_DP = 0
        const val MAX_MARGIN_DP = 48
    }

    fun action(id: Int, slot: String, fallback: WidgetAction): WidgetAction =
        WidgetAction.entries.firstOrNull { it.name == store.getString("$id.action.$slot", null) } ?: fallback

    fun setAction(id: Int, slot: String, action: WidgetAction) {
        store.edit().putString("$id.action.$slot", action.name).apply()
    }

    fun cornerRadius(id: Int, kind: WidgetKind): Float = store
        .getFloat("$id.cornerRadius", kind.defaultCornerRadiusDp)
        .coerceIn(MIN_RADIUS_DP, MAX_RADIUS_DP)

    fun setCornerRadius(id: Int, radiusDp: Float) {
        store.edit().putFloat("$id.cornerRadius", radiusDp.coerceIn(MIN_RADIUS_DP, MAX_RADIUS_DP)).apply()
    }

    fun marginPreset(id: Int): WidgetMarginPreset = WidgetMarginPreset.entries.firstOrNull {
        it.name == store.getString("$id.marginPreset", null)
    } ?: WidgetMarginPreset.DESKTOP_4X6

    fun setMarginPreset(id: Int, preset: WidgetMarginPreset) {
        val edit = store.edit().putString("$id.marginPreset", preset.name)
        if (preset.topDp != null && preset.bottomDp != null) {
            edit.putInt("$id.marginTop", preset.topDp)
                .putInt("$id.marginBottom", preset.bottomDp)
        }
        edit.apply()
    }

    fun marginTop(id: Int): Int = store
        .getInt("$id.marginTop", 0)
        .coerceIn(MIN_MARGIN_DP, MAX_MARGIN_DP)

    fun marginBottom(id: Int): Int = store
        .getInt("$id.marginBottom", 0)
        .coerceIn(MIN_MARGIN_DP, MAX_MARGIN_DP)

    fun setMargins(id: Int, topDp: Int, bottomDp: Int) {
        store.edit()
            .putInt("$id.marginTop", topDp.coerceIn(MIN_MARGIN_DP, MAX_MARGIN_DP))
            .putInt("$id.marginBottom", bottomDp.coerceIn(MIN_MARGIN_DP, MAX_MARGIN_DP))
            .putString("$id.marginPreset", WidgetMarginPreset.CUSTOM.name)
            .apply()
    }

    fun dish(id: Int, day: Long): String? =
        store.getString("$id.dish.$day", null)

    fun setDish(id: Int, day: Long, name: String) {
        val edit = store.edit()
        store.all.keys.filter { it.startsWith("$id.dish.") }.forEach(edit::remove)
        edit.putString("$id.dish.$day", name).apply()
    }

    fun delete(id: Int) {
        val edit = store.edit()
        store.all.keys.filter { it.startsWith("$id.") }.forEach(edit::remove)
        edit.apply()
    }

    fun restore(oldId: Int, newId: Int) {
        if (oldId == newId) return
        val edit = store.edit()
        store.all.filterKeys { it.startsWith("$oldId.") }.forEach { (key, value) ->
            if (value is String) edit.putString("$newId.${key.substringAfter('.')}", value)
            edit.remove(key)
        }
        edit.apply()
    }
}
