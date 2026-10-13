package com.click.lightmemo.mcp

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.awaitCancellation

class McpHttpServerTest {
    private fun exchange(server: McpHttpServer, method: String = "POST", path: String = "/mcp?token=test-secret",
        body: String = """{"jsonrpc":"2.0","id":1,"method":"ping"}""",
        contentType: String = "application/json", extra: String = "", host: String = "127.0.0.1:${server.port}",
        length: Int = body.toByteArray().size): String {
        return Socket("127.0.0.1", server.port).use { socket ->
            socket.soTimeout = 3_000
            val raw = "$method $path HTTP/1.1\r\nHost: $host\r\nContent-Type: $contentType\r\nContent-Length: $length\r\n$extra\r\n$body"
            socket.getOutputStream().apply { write(raw.toByteArray()); flush() }
            socket.getInputStream().bufferedReader().readText()
        }
    }
    private fun withServer(block: (McpHttpServer, AtomicInteger) -> Unit) {
        val queries = AtomicInteger()
        McpHttpServer(McpProtocol { queries.incrementAndGet(); JSONObject().put("source", "qingshiji") }, "test-secret", 0)
            .use { block(it, queries) }
    }
    private fun assertStatus(code: Int, raw: String) { assertTrue(raw.take(80), raw.startsWith("HTTP/1.1 $code ")) }

    @Test fun authorizedQueriesAndBearerCredentialWork() = withServer { server, queries ->
        val body = """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_today_nutrition","arguments":{}}}"""
        assertStatus(200, exchange(server, body = body))
        assertStatus(200, exchange(server, path = "/mcp", extra = "Authorization: Bearer test-secret\r\n", body = body))
        assertEquals(2, queries.get())
    }
    @Test fun unauthorizedRequestsNeverReadData() = withServer { server, queries ->
        val body = """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_today_nutrition"}}"""
        assertStatus(401, exchange(server, path = "/mcp", body = body))
        assertStatus(401, exchange(server, path = "/mcp?token=wrong", body = body))
        assertEquals(0, queries.get())
    }
    @Test fun rejectsBrowserOriginAndReboundHost() = withServer { server, queries ->
        assertStatus(403, exchange(server, extra = "Origin: https://untrusted.example\r\n"))
        assertStatus(403, exchange(server, host = "untrusted.example:${server.port}"))
        assertEquals(0, queries.get())
    }
    @Test fun healthContainsOnlyAvailability() = withServer { server, queries ->
        val reply = exchange(server, method = "GET", path = "/health", body = "")
        assertStatus(200, reply)
        val health = JSONObject(reply.substringAfter("\r\n\r\n"))
        assertEquals(1, health.length())
        assertEquals("ok", health.getString("status"))
        assertEquals(0, queries.get())
    }
    @Test fun checksRoutesMethodsTypesAndSizeLimits() = withServer { server, _ ->
        assertStatus(405, exchange(server, method = "GET"))
        assertStatus(404, exchange(server, path = "/unknown"))
        assertStatus(415, exchange(server, contentType = "text/plain"))
        assertStatus(413, exchange(server, length = 65537))
        assertStatus(431, exchange(server, extra = "X-Large: ${"x".repeat(17000)}\r\n"))
        assertStatus(400, exchange(server, extra = "Content-Length: 0\r\n"))
        assertStatus(400, exchange(server, extra = "Transfer-Encoding: chunked\r\n"))
    }
    @Test fun notificationsReturnEmpty202AndParseErrorsKeepJsonRpcFormat() = withServer { server, _ ->
        val notification = exchange(server, body = """{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        assertStatus(202, notification)
        assertEquals("", notification.substringAfter("\r\n\r\n"))
        val invalid = exchange(server, body = "{invalid}")
        assertStatus(200, invalid)
        assertEquals(-32700, JSONObject(invalid.substringAfter("\r\n\r\n")).getJSONObject("error").getInt("code"))
    }
    @Test fun closingCancelsOpenClientsAndReleasesPort() {
        val server = McpHttpServer(McpProtocol { JSONObject() }, "test-secret", 0)
        val port = server.port
        val socket = Socket("127.0.0.1", port).apply { soTimeout = 3_000 }
        try {
            socket.getOutputStream().apply { write("POST /mcp HTTP/1.1\r\n".toByteArray()); flush() }
            server.close()
            // Depending on TCP timing the peer sees EOF or a reset; neither may stay open.
            assertTrue(runCatching { socket.getInputStream().read() == -1 }.getOrDefault(true))
            McpHttpServer(McpProtocol { JSONObject() }, "new-secret", port).use {
                assertStatus(401, exchange(it))
                assertStatus(200, exchange(it, path = "/mcp?token=new-secret"))
            }
        } finally { socket.close(); server.close() }
    }
    @Test fun occupiedPortFailsInsteadOfReportingRunning() = withServer { server, _ ->
        try {
            McpHttpServer(McpProtocol { JSONObject() }, "other-secret", server.port).use { fail("Must not bind twice") }
        } catch (_: java.net.BindException) { /* expected */ }
    }

    @Test fun idleWorkersAreReleasedAndNewRequestsCanRun() {
        val workers = McpHttpServer.createWorkers(idleTimeoutMillis = 100)
        try {
            val entered = CountDownLatch(4)
            val release = CountDownLatch(1)
            repeat(4) { workers.execute { entered.countDown(); release.await(3, TimeUnit.SECONDS) } }
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS))
                assertEquals(4, workers.poolSize)
            } finally { release.countDown() }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (workers.poolSize != 0 && System.nanoTime() < deadline) Thread.sleep(10)
            assertEquals("No request workers should remain idle", 0, workers.poolSize)
            val resumed = CountDownLatch(1)
            workers.execute { resumed.countDown() }
            assertTrue("Request after idle must still run", resumed.await(3, TimeUnit.SECONDS))
        } finally { workers.shutdownNow() }
    }

    @Test fun closingCancelsSuspendedToolOnRequestWorker() {
        val entered = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val server = McpHttpServer(McpProtocol {
            assertTrue(Thread.currentThread().name.startsWith("lightmemo-mcp-worker-"))
            entered.countDown()
            try { awaitCancellation() } finally { cancelled.countDown() }
        }, "test-secret", 0)
        val client = Thread {
            runCatching { exchange(server, body = """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_today_nutrition"}}""") }
        }
        try {
            client.start()
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            server.close()
            assertTrue(cancelled.await(3, TimeUnit.SECONDS))
            client.join(3_000)
            assertFalse(client.isAlive)
        } finally { server.close(); client.join(3_000) }
    }
}
