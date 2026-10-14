package com.click.lightmemo.mcp

import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.click.lightmemo.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.Socket
import java.net.URI

@RunWith(AndroidJUnit4::class)
class McpLifecycleTest {
    @Test fun launchAutoStartRestartAndCredentialReset(): Unit = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk") || Build.HARDWARE.contains("ranchu"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext
        val preferences = McpPreferences(app)
        val originalAutoStart = preferences.autoStart()
        instrumentation.uiAutomation.executeShellCommand("pm grant com.click.lightmemo android.permission.POST_NOTIFICATIONS").close()
        suspend fun stopped() { withTimeout(5_000) { McpRuntime.state.first { !it.running && !it.starting } } }
        suspend fun running(): String = withTimeout(10_000) { McpRuntime.state.first { it.running }.address }
        fun ping(address: String): String {
            val uri = URI(address)
            val body = """{"jsonrpc":"2.0","id":1,"method":"ping"}"""
            return Socket("127.0.0.1", 8765).use { socket ->
                socket.soTimeout = 3_000
                socket.getOutputStream().apply {
                    write(("POST ${uri.rawPath}?${uri.rawQuery} HTTP/1.1\r\nHost: 127.0.0.1:8765\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\n\r\n$body").toByteArray())
                    flush()
                }
                socket.getInputStream().bufferedReader().readText().substringBefore("\r\n")
            }
        }
        McpRuntime.stop(app)
        stopped()
        preferences.setAutoStart(true)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val address = running()
            assertTrue(ping(address).startsWith("HTTP/1.1 200 "))
            McpRuntime.stop(app)
            stopped()
            scenario.recreate()
            assertTrue("Restart must keep the saved connection address", address == running())
            assertTrue(ping(address).startsWith("HTTP/1.1 200 "))
            preferences.setAutoStart(false)
            McpRuntime.stop(app)
            stopped()
            scenario.recreate()
            delay(1_000)
            assertFalse(McpRuntime.state.value.running)
            assertFalse(McpRuntime.state.value.starting)
            preferences.resetToken()
            scenario.onActivity { McpRuntime.start(it) }
            val resetAddress = running()
            assertTrue("Reset must change the address", address != resetAddress)
            assertTrue(ping(address).startsWith("HTTP/1.1 401 "))
            assertTrue(ping(resetAddress).startsWith("HTTP/1.1 200 "))
        } finally {
            preferences.setAutoStart(originalAutoStart)
            McpRuntime.stop(app)
            stopped()
            scenario.close()
        }
    }
}
