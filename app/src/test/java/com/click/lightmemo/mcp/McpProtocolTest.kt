package com.click.lightmemo.mcp

import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class McpProtocolTest {
    private val protocol = McpProtocol { JSONObject().put("source", "qingshiji").put("is_mock", false) }
    private fun request(method: String, params: JSONObject = JSONObject()) = JSONObject()
        .put("jsonrpc", "2.0").put("id", 7).put("method", method).put("params", params).toString()
    private fun result(method: String, params: JSONObject = JSONObject()) = runBlocking {
        JSONObject(protocol.handle(request(method, params)).body).getJSONObject("result")
    }
    private fun code(raw: String): Int = runBlocking { JSONObject(protocol.handle(raw).body).getJSONObject("error").getInt("code") }

    @Test fun negotiatesKnownVersionAndFallsBackForUnknown() {
        assertEquals("2025-11-25", result("initialize", JSONObject().put("protocolVersion", "2025-11-25")).getString("protocolVersion"))
        assertEquals("2025-03-26", result("initialize", JSONObject().put("protocolVersion", "unknown")).getString("protocolVersion"))
        assertEquals("qingshiji", result("initialize").getJSONObject("serverInfo").getString("name"))
    }
    @Test fun listsOnlyReadOnlyNutritionToolWithoutWater() {
        val tools = result("tools/list").getJSONArray("tools")
        assertEquals(1, tools.length())
        assertEquals(McpProtocol.TOOL, tools.getJSONObject(0).getString("name"))
        assertFalse(tools.toString().contains("饮水"))
        assertFalse(tools.getJSONObject(0).getJSONObject("inputSchema").getBoolean("additionalProperties"))
    }
    @Test fun serializesRealDataInsideTextContent() {
        val result = result("tools/call", JSONObject().put("name", McpProtocol.TOOL))
        assertFalse(result.getBoolean("isError"))
        val summary = JSONObject(result.getJSONArray("content").getJSONObject(0).getString("text"))
        assertFalse(summary.getBoolean("is_mock"))
    }
    @Test fun rejectsUnknownToolExtraArgumentsAndInvalidArgumentType() {
        assertEquals(-32602, code(request("tools/call", JSONObject().put("name", "delete_food"))))
        assertEquals(-32602, code(request("tools/call", JSONObject().put("name", McpProtocol.TOOL).put("arguments", JSONObject().put("userId", 1)))))
        assertEquals(-32602, code(request("tools/call", JSONObject().put("name", McpProtocol.TOOL).put("arguments", "bad"))))
    }
    @Test fun rejectsMalformedJsonAndInvalidEnvelope() {
        assertEquals(-32700, code("{oops}"))
        assertEquals(-32700, code("{} trailing"))
        assertEquals(-32600, code("{}"))
        assertEquals(-32600, code("[]"))
        assertEquals(-32600, code("""{"jsonrpc":"2.0","id":true,"method":"ping"}"""))
        assertEquals(-32601, code(request("unknown")))
    }
    @Test fun acknowledgesNotificationsWithoutReadingData() = runBlocking {
        var calls = 0
        val p = McpProtocol { calls++; JSONObject() }
        val reply = p.handle("""{"jsonrpc":"2.0","method":"tools/call","params":{"name":"get_today_nutrition"}}""")
        assertEquals(202, reply.status)
        assertEquals("", reply.body)
        assertEquals(0, calls)
    }
    @Test fun supportsMixedBatchAndOmitsNotificationResponses() = runBlocking {
        val reply = protocol.handle("[${request("ping")},{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}]")
        assertEquals(1, org.json.JSONArray(reply.body).length())
        val invalid = org.json.JSONArray(protocol.handle("[\"invalid\",[]]").body)
        assertEquals(-32600, invalid.getJSONObject(0).getJSONObject("error").getInt("code"))
        assertEquals(-32600, invalid.getJSONObject(1).getJSONObject("error").getInt("code"))
    }
    @Test fun databaseFailureIsToolErrorWithoutLeakingException() = runBlocking {
        val p = McpProtocol { throw IllegalStateException("secret database path") }
        val reply = p.handle(request("tools/call", JSONObject().put("name", McpProtocol.TOOL)))
        assertTrue(JSONObject(reply.body).getJSONObject("result").getBoolean("isError"))
        assertFalse(reply.body.contains("secret"))
    }
    @Test fun sumsStoredPortionsWithoutScalingAgain() {
        val logs = listOf(
            FoodLog(name = "米饭", mealType = MealType.LUNCH, grams = 200.0, nutrition = Nutrition(232.0, 5.2, 51.8, 0.6)),
            FoodLog(name = "鸡蛋", mealType = MealType.LUNCH, grams = 50.0, nutrition = Nutrition(70.0, 6.0, 1.0, 5.0)),
        )
        val summary = nutritionResult(logs, LocalDate.of(2026, 10, 13), ZoneId.of("Asia/Shanghai"))
        assertEquals(302.0, summary.getDouble("calories_kcal"), 0.0001)
        assertEquals(11.2, summary.getDouble("protein_g"), 0.0001)
        assertEquals(52.8, summary.getDouble("carbohydrates_g"), 0.0001)
        assertEquals(5.6, summary.getDouble("fat_g"), 0.0001)
        assertEquals("ok", summary.getString("status"))
        assertFalse(summary.has("water_ml"))
    }
    @Test fun emptyDayIsNoRecordsAndKeepsCapturedDateAndTimezone() {
        val summary = nutritionResult(emptyList(), LocalDate.of(2026, 10, 13), ZoneId.of("Asia/Shanghai"))
        assertEquals("no_records", summary.getString("status"))
        assertEquals("2026-10-13", summary.getString("date"))
        assertEquals("Asia/Shanghai", summary.getString("timezone"))
        assertEquals(0.0, summary.getDouble("calories_kcal"), 0.0)
        assertFalse(summary.has("water_ml"))
    }
}
