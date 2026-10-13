package com.click.lightmemo.mcp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class RpcReply(val status: Int, val body: String = "")

class McpProtocol(private val provider: NutritionProvider) {
    private var textRecording: TextRecordingTool? = null

    constructor(provider: NutritionProvider, textRecording: TextRecordingTool) : this(provider) {
        this.textRecording = textRecording
    }
    suspend fun handle(raw: String): RpcReply {
        val value = try {
            kotlinx.serialization.json.Json.parseToJsonElement(raw)
            val tokener = JSONTokener(raw)
            val parsed = tokener.nextValue()
            if (tokener.nextClean() != '\u0000') return error(JSONObject.NULL, -32700, "无法解析 JSON")
            parsed
        } catch (_: Exception) {
            return error(JSONObject.NULL, -32700, "无法解析 JSON")
        }
        if (value is JSONArray) {
            if (value.length() == 0) return error(JSONObject.NULL, -32600, "请求格式不正确")
            val replies = JSONArray()
            for (index in 0 until value.length()) {
                val item = value.get(index)
                val reply = if (item !is JSONObject) error(JSONObject.NULL, -32600, "请求格式不正确")
                    else handle(item.toString())
                if (reply.body.isNotEmpty()) replies.put(JSONObject(reply.body))
            }
            return if (replies.length() == 0) RpcReply(202) else RpcReply(200, replies.toString())
        }
        if (value !is JSONObject) return error(JSONObject.NULL, -32600, "请求格式不正确")
        val id = value.opt("id") ?: JSONObject.NULL
        val method = value.opt("method") as? String
        if (value.opt("jsonrpc") != "2.0" || method == null ||
            (value.has("id") && id !is String && id !is Number) ||
            (value.has("params") && value.opt("params") !is JSONObject)
        ) return error(JSONObject.NULL, -32600, "请求格式不正确")
        // Notifications never execute a tool or return a JSON-RPC response.
        if (!value.has("id")) return RpcReply(202)
        val params = value.optJSONObject("params") ?: JSONObject()
        val result = when (method) {
            "initialize" -> {
                val requested = params.optString("protocolVersion")
                JSONObject().apply {
                    put("protocolVersion", requested.takeIf { it in versions } ?: "2025-03-26")
                    put("capabilities", JSONObject().put("tools", JSONObject().put("listChanged", false)))
                    put("serverInfo", JSONObject().put("name", "qingshiji").put("version", "1.0.0"))
                    put("instructions", "查询今天已记录的营养摄入。记录饮食时直接把用户的完整描述原文提交文字识别工具，复用轻食记原有识别及实时通知流程。提交识别不代表已保存，用户在原有结果页确认后才记入统计。")
                }
            }
            "ping" -> JSONObject()
            "tools/list" -> JSONObject().put("tools", JSONArray().put(JSONObject().apply {
                put("name", TOOL)
                put("description", "查询轻食记今天已记录的热量、蛋白质、碳水化合物和脂肪摄入汇总，以及对应的每日目标 targets。目标为 null 表示尚未设置且无推荐值，不代表目标为零。摄入仅包含已记录的饮食。")
                put("inputSchema", JSONObject().put("type", "object")
                    .put("properties", JSONObject()).put("additionalProperties", false))
            }).apply { if (textRecording != null) put(ExistingTextRecordingTool.definition()) })
            "tools/call" -> {
                if (params.has("arguments") && params.opt("arguments") !is JSONObject)
                    return error(id, -32602, "工具参数必须为对象")
                val arguments = params.optJSONObject("arguments") ?: JSONObject()
                val name = params.opt("name") as? String
                if (name != TOOL && (textRecording == null || name != ExistingTextRecordingTool.NAME))
                    return error(id, -32602, "工具不存在或参数不正确")
                if (name == TOOL && arguments.length() != 0) return error(id, -32602, "营养查询不接受额外参数")
                try {
                    withTimeout(5_000L) {
                        val data = when (name) {
                            ExistingTextRecordingTool.NAME -> requireNotNull(textRecording).record(arguments)
                            else -> provider.today()
                        }
                        content(data.toString(), false)
                    }
                } catch (e: TextRecordingInputException) {
                    return error(id, -32602, e.message ?: "文字录入参数不正确")
                } catch (e: TextRecordingStartException) {
                    content(e.message ?: "文字识别暂时无法启动", true)
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    content("处理超时，请稍后重试。", true)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    content("暂时无法处理请求，请打开轻食记检查后重试。", true)
                }
            }
            else -> return error(id, -32601, "不支持的方法")
        }
        return RpcReply(200, JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result).toString())
    }

    private fun content(text: String, isError: Boolean) = JSONObject()
        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
        .put("isError", isError)

    private fun error(id: Any, code: Int, message: String) = RpcReply(200,
        JSONObject().put("jsonrpc", "2.0").put("id", id)
            .put("error", JSONObject().put("code", code).put("message", message)).toString())

    companion object {
        const val TOOL = "get_today_nutrition"
        private val versions = setOf("2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25")
    }
}
