package com.click.lightmemo.widget

import android.content.Context

enum class WidgetAction(val title: String, val route: String) {
    TODAY("打开今日摄入", "today"), STATS("打开统计", "stats"),
    RECOMMEND("打开饮食推荐", "recommend"), CAMERA("拍照识别", "camera"),
    GALLERY("从相册选择", "gallery"), MANUAL("文字录入", "manual"),
    SETTINGS("打开小组件设置", "settings"),
}

class WidgetPreferences(context: Context) {
    private val store = context.getSharedPreferences("desktop_widgets", Context.MODE_PRIVATE)

    fun action(id: Int, slot: String, fallback: WidgetAction): WidgetAction =
        WidgetAction.entries.firstOrNull { it.name == store.getString("$id.action.$slot", null) } ?: fallback

    fun setAction(id: Int, slot: String, action: WidgetAction) {
        store.edit().putString("$id.action.$slot", action.name).apply()
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
