package com.click.lightmemo.mcp

import com.click.lightmemo.domain.MealType
import com.click.lightmemo.recognition.RecognitionRequest
import com.click.lightmemo.recognition.RecognitionRequestType
import com.click.lightmemo.recognition.RecognitionTaskRecord
import com.click.lightmemo.recognition.RecognitionTaskStatus
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class TextRecordingToolTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-13T04:30:00Z"), ZoneId.of("Asia/Shanghai"))
    private val input = "午饭吃了 200 克米饭、两个鸡蛋，米饭只吃了一半"

    @Test fun forwardsFullTextIntoTheExistingTextRequest() = runBlocking {
        var started: RecognitionRequest? = null
        var opened = false
        val tool = ExistingTextRecordingTool({ null }, { started = it }, { opened = true }, clock)
        val result = tool.record(JSONObject().put("text", input))
        val request = requireNotNull(started)
        assertEquals(RecognitionRequestType.TEXT, request.type)
        assertEquals(input, request.text)
        assertEquals(input, request.foodName)
        assertEquals(MealType.LUNCH, request.mealType)
        assertEquals(java.time.LocalDate.of(2026, 10, 13).toEpochDay(), request.targetDateEpochDay)
        assertEquals(750, request.mealMinuteOfDay)
        assertTrue(opened)
        assertEquals("recognizing", result.getString("status"))
        assertEquals(request.id, result.getString("task_id"))
    }

    @Test fun optionalMealTypeOverridesTimeDefaultWithoutParsingFoods() = runBlocking {
        var request: RecognitionRequest? = null
        ExistingTextRecordingTool({ null }, { request = it }, {}, clock)
            .record(JSONObject().put("text", "吃了一碗面").put("meal_type", "dinner"))
        assertEquals(MealType.DINNER, request?.mealType)
        assertEquals("吃了一碗面", request?.text)
        assertNull(request?.grams)
    }

    @Test fun rejectsEmptyMalformedAndExtraArgumentsBeforeStarting() = runBlocking {
        var starts = 0
        val tool = ExistingTextRecordingTool({ null }, { starts++ }, {}, clock)
        listOf(JSONObject(), JSONObject().put("text", " "), JSONObject().put("text", 123),
            JSONObject().put("text", "x".repeat(4001)), JSONObject().put("text", input).put("foods", "x"),
            JSONObject().put("text", input).put("meal_type", "unknown")).forEach {
            try { tool.record(it); fail("Invalid text must not start recognition") } catch (_: TextRecordingInputException) { }
        }
        assertEquals(0, starts)
    }

    @Test fun retriesReuseCurrentRunningOrCompletedTextTask() = runBlocking {
        var record: RecognitionTaskRecord? = null
        var starts = 0
        val tool = ExistingTextRecordingTool({ record }, { starts++; record = RecognitionTaskRecord(it) }, {}, clock)
        val args = JSONObject().put("text", input)
        val first = tool.record(args)
        assertEquals(first.getString("task_id"), tool.record(args).getString("task_id"))
        record = record?.copy(status = RecognitionTaskStatus.COMPLETED)
        assertEquals("ready_for_review", tool.record(args).getString("status"))
        assertEquals(1, starts)
        // Clearing a saved/discarded result permits a genuinely new recording.
        record = null
        tool.record(args)
        assertEquals(2, starts)
    }

    @Test fun blockedActivityLaunchKeepsTheExistingRecognitionAndNotificationPath() = runBlocking {
        var started = false
        val result = ExistingTextRecordingTool({ null }, { started = true }, { throw SecurityException("blocked") }, clock)
            .record(JSONObject().put("text", input))
        assertTrue(started)
        assertEquals("recognizing", result.getString("status"))
    }

    @Test fun protocolExposesOnlyNutritionAndTextRecordingAndReportsStartFailure() = runBlocking {
        val tool = ExistingTextRecordingTool({ null }, { throw TextRecordingStartException("请打开轻食记后重试。") }, {}, clock)
        val protocol = McpProtocol(NutritionProvider { JSONObject() }, tool)
        val list = JSONObject(protocol.handle("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""").body)
            .getJSONObject("result").getJSONArray("tools")
        assertEquals(2, list.length())
        assertEquals(ExistingTextRecordingTool.NAME, list.getJSONObject(1).getString("name"))
        val params = JSONObject().put("name", ExistingTextRecordingTool.NAME).put("arguments", JSONObject().put("text", input))
        val response = protocol.handle(JSONObject().put("jsonrpc", "2.0").put("id", 2).put("method", "tools/call").put("params", params).toString())
        assertTrue(JSONObject(response.body).getJSONObject("result").getBoolean("isError"))
        assertFalse(list.toString().contains("prepare_food_record"))
    }
}
