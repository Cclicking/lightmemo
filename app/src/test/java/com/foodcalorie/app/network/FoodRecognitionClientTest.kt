package com.foodcalorie.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodRecognitionClientTest {
    private val client = FoodRecognitionClient()

    @Test
    fun parsesItemsJson() {
        val payload = """
            {"items":[
              {"name":"米饭","grams":150,"caloriesKcal":174,"proteinG":3.9,"carbsG":38.9,"fatG":0.5}
            ]}
        """.trimIndent()
        val items = client.parseItemsJson(payload)
        assertEquals(1, items.size)
        assertEquals("米饭", items[0].name)
        assertEquals(150.0, items[0].grams, 0.01)
        assertEquals(174.0, items[0].nutrition.caloriesKcal, 0.01)
    }

    @Test
    fun extractsJsonFromFencedContent() {
        val raw = """
            {"choices":[{"message":{"content":"```json\n{\"items\":[{\"name\":\"苹果\",\"grams\":180,\"caloriesKcal\":90,\"proteinG\":0.5,\"carbsG\":24,\"fatG\":0.2}]}\n```"}}]}
        """.trimIndent()
        val items = client.parseChatCompletion(raw)
        assertEquals(1, items.size)
        assertEquals("苹果", items[0].name)
        assertTrue(items[0].nutrition.caloriesKcal > 0)
    }
}
