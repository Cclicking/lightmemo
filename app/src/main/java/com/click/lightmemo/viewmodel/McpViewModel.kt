package com.click.lightmemo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.click.lightmemo.mcp.McpRuntime
import com.click.lightmemo.mcp.McpPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class McpCheckState(val checking: Boolean = false, val message: String? = null)
data class McpPreferenceState(val loaded: Boolean = false, val autoStart: Boolean = false, val busy: Boolean = false)

class McpViewModel(application: Application) : AndroidViewModel(application) {
    val state = McpRuntime.state
    private val mutableCheck = MutableStateFlow(McpCheckState())
    val check = mutableCheck.asStateFlow()
    private val preferences = McpPreferences(application)
    private val mutablePreferences = MutableStateFlow(McpPreferenceState())
    val preferenceState = mutablePreferences.asStateFlow()

    init {
        viewModelScope.launch {
            val enabled = withContext(Dispatchers.IO) { runCatching { preferences.autoStart() } }
            mutablePreferences.value = McpPreferenceState(loaded = enabled.isSuccess,
                autoStart = enabled.getOrDefault(false))
        }
    }

    fun setAutoStart(enabled: Boolean, onResult: (String) -> Unit) {
        if (!mutablePreferences.value.loaded || mutablePreferences.value.busy) return
        mutablePreferences.value = mutablePreferences.value.copy(busy = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { preferences.setAutoStart(enabled) } }
            mutablePreferences.value = mutablePreferences.value.copy(busy = false,
                autoStart = if (result.isSuccess) enabled else mutablePreferences.value.autoStart)
            onResult(if (result.isSuccess) {
                if (enabled) "已开启，下次打开轻食记时自动启动 MCP" else "已关闭自动启动，当前服务可手动停止"
            } else "设置保存失败，请重试")
        }
    }

    fun resetCredential(onResult: (String) -> Unit) {
        if (state.value.running || state.value.starting || mutablePreferences.value.busy) return
        mutablePreferences.value = mutablePreferences.value.copy(busy = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                check(!state.value.running && !state.value.starting)
                preferences.resetToken()
            } }
            mutablePreferences.value = mutablePreferences.value.copy(busy = false)
            mutableCheck.value = McpCheckState()
            onResult(if (result.isSuccess) "连接地址已重置，开启服务后请更新小爱中的地址" else "重置失败，请关闭服务后重试")
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (mutablePreferences.value.busy) return
        mutableCheck.value = McpCheckState()
        if (enabled) McpRuntime.start(getApplication()) else McpRuntime.stop(getApplication())
    }

    fun checkConnection() {
        val address = state.value.address
        if (!state.value.running || mutableCheck.value.checking) return
        mutableCheck.value = McpCheckState(checking = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // Raw sockets avoid changing the application's cleartext client policy.
                    val endpoint = java.net.URI(address)
                    fun request(json: String, notification: Boolean = false): JSONObject {
                        java.net.Socket().use { socket ->
                            socket.connect(java.net.InetSocketAddress("127.0.0.1", 8765), 2_000)
                            socket.soTimeout = 7_000
                            val bytes = json.toByteArray(Charsets.UTF_8)
                            val headers = "POST ${endpoint.rawPath}?${endpoint.rawQuery} HTTP/1.1\r\nHost: 127.0.0.1:8765\r\nContent-Type: application/json\r\nAccept: application/json, text/event-stream\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                            socket.getOutputStream().apply { write(headers.toByteArray()); write(bytes); flush() }
                            val response = socket.getInputStream().bufferedReader(Charsets.UTF_8).readText()
                            if (notification) {
                                check(response.startsWith("HTTP/1.1 202 "))
                                check(response.substringAfter("\r\n\r\n").isEmpty())
                                return JSONObject()
                            }
                            check(response.startsWith("HTTP/1.1 200 "))
                            val body = JSONObject(response.substringAfter("\r\n\r\n"))
                            check(!body.has("error"))
                            return body.getJSONObject("result")
                        }
                    }
                    check(request("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"lightmemo-check","version":"1"}}}""").has("serverInfo"))
                    request("""{"jsonrpc":"2.0","method":"notifications/initialized"}""", notification = true)
                    check(request("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""").getJSONArray("tools").getJSONObject(0).getString("name") == "get_today_nutrition")
                    val tool = request("""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_today_nutrition","arguments":{}}}""")
                    if (tool.optBoolean("isError")) "服务可连接，但营养记录暂时无法读取，请稍后重试。"
                    else {
                        check(!JSONObject(tool.getJSONArray("content").getJSONObject(0).getString("text")).getBoolean("is_mock"))
                        "连接检查通过，营养查询可用。"
                    }
                }.getOrDefault("连接检查失败，请重新开启服务后重试。")
            }
            mutableCheck.value = if (state.value.address == address && state.value.running) McpCheckState(message = result) else McpCheckState()
        }
    }
}
