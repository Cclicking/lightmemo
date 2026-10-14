package com.click.lightmemo.data

import org.junit.Assert.*
import org.junit.Test

class StatsCardLayoutTest {
    @Test fun movingCardsBetweenGroupsPreservesOrderAndUniqueness() {
        val initial = StatsCardLayout()
        val hidden = initial.move(StatsCard.CALORIE_BALANCE.id, true, 0)
        assertFalse(StatsCard.CALORIE_BALANCE.id in hidden.visible)
        assertEquals(listOf(StatsCard.CALORIE_BALANCE.id), hidden.hidden)
        val restored = hidden.move(StatsCard.CALORIE_BALANCE.id, false, 0)
        assertEquals(StatsCard.CALORIE_BALANCE.id, restored.visible.first())
        assertEquals(StatsCard.entries.size, restored.visible.distinct().size)
        assertTrue(restored.hidden.isEmpty())
    }

    @Test fun supportsReorderingAndHidingEveryCard() {
        val reordered = StatsCardLayout().move(StatsCard.SUMMARY.id, false, 5)
        assertEquals(StatsCard.SUMMARY.id, reordered.visible.last())
        val allHidden = StatsCard.entries.fold(reordered) { layout, card -> layout.move(card.id, true, 100) }
        assertTrue(allHidden.visible.isEmpty())
        assertEquals(StatsCard.entries.size, allHidden.hidden.size)
        assertEquals(allHidden, allHidden.normalized())
    }

    @Test fun unknownAndDuplicateIdsAreRemovedAndNewCardsRemainVisible() {
        val layout = StatsCardLayout(listOf("unknown", "summary", "summary"), listOf("meal_timing", "summary", "summary")).normalized()
        assertEquals(listOf("meal_timing", "summary"), layout.hidden)
        assertEquals(StatsCard.entries.size, (layout.visible + layout.hidden).distinct().size)
        assertFalse("unknown" in layout.visible)
        assertTrue(layout.visible.intersect(layout.hidden.toSet()).isEmpty())
    }
}
