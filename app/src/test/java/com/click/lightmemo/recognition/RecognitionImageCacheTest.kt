package com.click.lightmemo.recognition

import com.click.lightmemo.domain.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecognitionImageCacheTest {
    @get:Rule val folder = TemporaryFolder()
    private val result = MealRecognition(true, "午餐", emptyList(), 0.95, emptyList())
    private fun entry(fingerprint: String = "hash", created: Long = 1000) =
        CachedRecognition(fingerprint, "context", result, created, "model", "1.5", RecognitionPath.FAST)
    private fun file() = File(folder.root, "cache.json")

    @Test fun survivesRecreationAndKeepsOriginalPathWithoutImageData() {
        val file = file()
        RecognitionImageCache(file) { 1000 }.put(entry())
        assertEquals(entry(), RecognitionImageCache(file) { 2000 }.find("hash", "context"))
        assertFalse(file.readText().contains("imageUri"))
        assertFalse(file.readText().contains("base64"))
    }

    @Test fun expiredAndFutureEntriesAreRejectedAndRemoved() {
        val file = file()
        RecognitionImageCache(file) { 1000 }.put(entry())
        assertNull(RecognitionImageCache(file) { 1000 + RecognitionImageCache.TTL_MILLIS }.find("hash", "context"))
        assertEquals("[]", file.readText())
        RecognitionImageCache(file) { 1000 }.put(entry(created = 2000))
        assertNull(RecognitionImageCache(file) { 1000 }.find("hash", "context"))
    }

    @Test fun contextAndFingerprintMustBothMatch() {
        val cache = RecognitionImageCache(file()) { 1000 }
        cache.put(entry())
        assertNull(cache.find("different", "context"))
        assertNull(cache.find("hash", "changed-model-or-description"))
        assertNotNull(cache.find("hash", "context"))
    }

    @Test fun boundedCacheEvictsOldestAndReplacesDuplicate() {
        val cache = RecognitionImageCache(file()) { 1000 }
        repeat(RecognitionImageCache.MAX_ENTRIES + 1) { cache.put(entry("$it")) }
        assertNull(cache.find("0", "context"))
        assertNotNull(cache.find("24", "context"))
        cache.put(entry("24").copy(model = "replacement"))
        assertEquals("replacement", cache.find("24", "context")!!.model)
    }

    @Test fun brokenOrMissingCacheDegradesToMiss() {
        val file = file()
        val cache = RecognitionImageCache(file) { 1000 }
        assertNull(cache.find("hash", "context"))
        file.writeText("broken json")
        assertNull(cache.find("hash", "context"))
        cache.put(entry())
        assertNotNull(cache.find("hash", "context"))
    }

    @Test fun digestIsStableAndDetectsChangedImage() {
        assertEquals(RecognitionImageCache.digest("image"), RecognitionImageCache.digest("image"))
        assertNotEquals(RecognitionImageCache.digest("image"), RecognitionImageCache.digest("another image"))
        assertEquals(64, RecognitionImageCache.digest("image").length)
    }

    @Test fun reusedHierarchicalResultsReceiveFreshDishAndComponentIds() {
        val component = FoodComponent("c", "米饭", "rice", source = ComponentSource.VISIBLE,
            estimatedWeightG = 200.0, weightMinG = 180.0, weightMaxG = 220.0, confidence = 0.9)
        val child = RecognizedDish("child", "米饭", DishType.SINGLE_FOOD, 0.9, listOf(component))
        val initial = result.copy(dishes = listOf(child.copy(id = "parent", children = listOf(child))))
        val fresh = initial.withFreshIds()
        assertNotEquals(initial.dishes.first().id, fresh.dishes.first().id)
        assertNotEquals(child.id, fresh.dishes.first().children.first().id)
        assertNotEquals(component.id, fresh.dishes.first().children.first().components.first().id)
        assertEquals(initial.nutrition, fresh.nutrition)
    }
}
