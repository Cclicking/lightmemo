package com.click.lightmemo.data

import kotlinx.serialization.Serializable

enum class StatsCard(val id: String, val title: String) {
    SUMMARY("summary", "日均摄入"),
    MEAL_STRUCTURE("meal_structure", "饮食结构"),
    MEAL_TIMING("meal_timing", "饮食规律"),
    CALORIE_BALANCE("calorie_balance", "热量差"),
    ADVICE("advice", "饮食建议"),
    NUTRITION("nutrition", "营养摄入"),
}

@Serializable
data class StatsCardLayout(
    val visible: List<String> = StatsCard.entries.map { it.id },
    val hidden: List<String> = emptyList(),
) {
    fun normalized(): StatsCardLayout {
        val known = StatsCard.entries.map { it.id }
        val safeHidden = hidden.filter { it in known }.distinct()
        val safeVisible = visible.filter { it in known && it !in safeHidden }.distinct()
        return StatsCardLayout(safeVisible + known.filter { it !in safeVisible && it !in safeHidden }, safeHidden)
    }

    fun move(id: String, toHidden: Boolean, index: Int): StatsCardLayout {
        if (StatsCard.entries.none { it.id == id }) return this
        val current = normalized()
        val shown = current.visible.filterNot { it == id }.toMutableList()
        val concealed = current.hidden.filterNot { it == id }.toMutableList()
        val destination = if (toHidden) concealed else shown
        destination.add(index.coerceIn(0, destination.size), id)
        return StatsCardLayout(shown, concealed)
    }
}
