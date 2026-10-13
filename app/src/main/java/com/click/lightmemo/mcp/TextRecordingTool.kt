package com.click.lightmemo.mcp

import com.click.lightmemo.domain.MealType
import com.click.lightmemo.recognition.RecognitionRequest
import com.click.lightmemo.recognition.RecognitionRequestType
import com.click.lightmemo.recognition.RecognitionTaskRecord
import com.click.lightmemo.recognition.RecognitionTaskStatus
import com.click.lightmemo.viewmodel.mealTypeForMinuteOfDay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.time.LocalDateTime

fun interface TextRecordingTool {
    suspend fun record(arguments: JSONObject): JSONObject
}

/** A handoff to the existing TEXT request, without another recognition or persistence pipeline. */
class ExistingTextRecordingTool(
    private val currentTask: () -> RecognitionTaskRecord?,
    private val start: (RecognitionRequest) -> Unit,
    private val openResult: () -> Unit,
    private val clock: Clock = Clock.systemDefaultZone(),
) : TextRecordingTool {
    private val mutex = Mutex()

    override suspend fun record(arguments: JSONObject): JSONObject = mutex.withLock {
        if (arguments.keys().asSequence().any { it !in setOf("text", "meal_type") })
            throw TextRecordingInputException("仅接受 text 和可选的 meal_type，请直接提交饮食描述原文。")
        val text = (arguments.opt("text") as? String)?.trim()
            ?: throw TextRecordingInputException("text 必须为饮食描述文字。")
        if (text.isEmpty() || text.length > 4000) throw TextRecordingInputException("饮食描述不能为空或超过 4000 字符。")
        val now = LocalDateTime.now(clock)
        val minute = now.hour * 60 + now.minute
        val mealType = if (arguments.has("meal_type")) {
            val value = arguments.opt("meal_type") as? String
            meals[value] ?: throw TextRecordingInputException("meal_type 必须为 breakfast、lunch、dinner 或 snack。")
        } else mealTypeForMinuteOfDay(minute)
        val current = currentTask()
        val same = current?.request?.let {
            it.type == RecognitionRequestType.TEXT && it.text == text && it.mealType == mealType &&
                it.targetDateEpochDay == now.toLocalDate().toEpochDay()
        } == true && current?.status in setOf(RecognitionTaskStatus.RUNNING, RecognitionTaskStatus.COMPLETED)
        val request = if (same) requireNotNull(current).request else RecognitionRequest(
            type = RecognitionRequestType.TEXT, text = text, foodName = text, mealType = mealType,
            targetDateEpochDay = now.toLocalDate().toEpochDay(), mealMinuteOfDay = minute,
        ).also(start)
        // Existing notification already opens this same result. A blocked background Activity
        // launch must not discard the recognition job or create a separate notification path.
        runCatching { openResult() }
        JSONObject().put("task_id", request.id)
            .put("status", if (same && current?.status == RecognitionTaskStatus.COMPLETED) "ready_for_review" else "recognizing")
            .put("message", "已提交轻食记原有文字识别流程，进度见实时识别通知。识别完成后请在原有结果页核对并保存；当前尚未保存饮食记录。")
    }

    companion object {
        const val NAME = "record_food_from_text"
        private val meals = mapOf("breakfast" to MealType.BREAKFAST, "lunch" to MealType.LUNCH,
            "dinner" to MealType.DINNER, "snack" to MealType.SNACK)

        fun definition(): JSONObject = JSONObject().put("name", NAME)
            .put("description", "用户要求记录饮食时，把用户提供的完整饮食描述原文作为 text 提交轻食记。直接进入原有文字录入识别、营养匹配和实时活动通知流程，不需要先拆分食物、估重或计算营养。可指定餐次；识别结束后用户在轻食记原有结果页确认保存。返回 recognizing 或 ready_for_review 均不代表已保存，请勿回答已记入饮食记录。")
            .put("inputSchema", JSONObject().put("type", "object").put("properties", JSONObject()
                .put("text", JSONObject().put("type", "string").put("minLength", 1).put("maxLength", 4000)
                    .put("description", "用户的完整饮食描述，例如：午饭吃了 200 克米饭和两个鸡蛋"))
                .put("meal_type", JSONObject().put("type", "string").put("enum", JSONArray(meals.keys.toList()))
                    .put("description", "可选餐次；未指定时沿用轻食记按当前时间选择餐次的规则")))
                .put("required", JSONArray().put("text")).put("additionalProperties", false))
            .put("annotations", JSONObject().put("readOnlyHint", false).put("destructiveHint", false).put("idempotentHint", false))
    }
}

class TextRecordingInputException(message: String) : IllegalArgumentException(message)
class TextRecordingStartException(message: String) : IllegalStateException(message)
