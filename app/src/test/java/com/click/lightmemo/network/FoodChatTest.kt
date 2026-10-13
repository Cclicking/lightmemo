package com.click.lightmemo.network

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodChatTest {
    @Test fun sendsConversationInOrderAndReturnsSeparateMealSummary() = runBlocking {
        var requestBody = ""
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = okio.Buffer()
            chain.request().body!!.writeTo(buffer)
            requestBody = buffer.readUtf8()
            val reply = """{"reply":"已整理牛肉和土豆的份量。","summary":"牛肉十片，土豆五片","ready":false,"question":{"prompt":"蘸料是什么？","options":["油碟","麻酱","没有蘸料"]}}"""
            val body = kotlinx.serialization.json.buildJsonObject {
                put("choices", kotlinx.serialization.json.buildJsonArray {
                    add(kotlinx.serialization.json.buildJsonObject {
                        put("message", kotlinx.serialization.json.buildJsonObject {
                            put("content", kotlinx.serialization.json.JsonPrimitive(reply))
                        })
                    })
                })
            }.toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        val result = FoodRecognitionClient(httpClient = http).continueFoodChat(
            "https://example.test/v1", "test", "test", listOf(
                FoodChatMessage("吃了牛肉和土豆", true),
                FoodChatMessage("先确认份量。", false,
                    FoodChatQuestion("各吃了多少？", listOf("各五片", "各十片"))),
                FoodChatMessage("牛肉十片，土豆五片", true),
            ), "晚餐",
        )
        val messages = kotlinx.serialization.json.Json.parseToJsonElement(requestBody)
            .jsonObject["messages"]!!.jsonArray
        assertEquals(listOf("system", "user", "assistant", "user"),
            messages.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        assertEquals("蘸料是什么？", result.question!!.prompt)
        assertEquals(listOf("油碟", "麻酱", "没有蘸料", "记不清，按常见份量估算"), result.question.answerOptions)
        assertEquals("牛肉十片，土豆五片", result.summary)
        assertTrue(messages[2].jsonObject["content"]!!.jsonArray[0].jsonObject["text"]!!
            .jsonPrimitive.content.contains("各吃了多少？"))
    }

    @Test fun invalidRepliesCannotBecomeMealDrafts() {
        val client = FoodRecognitionClient()
        listOf("{}", "不是 JSON", """{"reply":" ","summary":"米饭","ready":true}""").forEach {
            assertTrue(runCatching { client.parseFoodChatReply(it) }.exceptionOrNull() is RecognitionException)
        }
        assertEquals("", client.parseFoodChatReply("""{"reply":"先描述这餐。","summary":"","ready":false,"question":{"prompt":"吃了哪类食物？","options":["米饭和菜","火锅","面食"]}}""").summary)
    }

    @Test fun finalConfirmationRequiresFoodAndNoUnansweredQuestion() {
        val client = FoodRecognitionClient()
        val ready = client.parseFoodChatReply("""{"reply":"已整理这餐。","summary":"牛肉约200克（估算），米饭半碗","ready":true}""")
        assertTrue(ready.ready)
        assertEquals(null, ready.question)
        listOf(
            """{"reply":"好了","summary":"","ready":true}""",
            """{"reply":"好了","summary":"米饭半碗","ready":true,"question":{"prompt":"多少？","options":["半碗","一碗"]}}""",
            """{"reply":"继续","summary":"米饭","ready":false}""",
            """{"reply":"继续","summary":"米饭","ready":false,"question":{"prompt":"多少？","options":["半碗","半碗"]}}""",
        ).forEach { assertTrue(runCatching { client.parseFoodChatReply(it) }.isFailure) }
    }
}
