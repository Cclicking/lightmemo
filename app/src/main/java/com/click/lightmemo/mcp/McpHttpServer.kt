package com.click.lightmemo.mcp

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Stateless JSON responses over Streamable HTTP; intentionally bound to IPv4 loopback. */
class McpHttpServer(
    private val protocol: McpProtocol,
    private val token: String,
    port: Int = 8765,
    private val onFailure: () -> Unit = {},
) : AutoCloseable {
    private val job = SupervisorJob()
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val workers = createWorkers()
    private val tokenBytes = token.toByteArray(Charsets.UTF_8)
    private val listener = ServerSocket().apply {
        try {
            reuseAddress = true
            bind(java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 16)
        } catch (e: Exception) {
            close()
            workers.shutdownNow()
            job.cancel()
            throw e
        }
    }
    val port: Int get() = listener.localPort
    private val acceptThread = Thread({
        try {
            while (!listener.isClosed) {
                val socket = listener.accept()
                clients.add(socket)
                if (listener.isClosed) {
                    clients.remove(socket)
                    socket.close()
                    break
                }
                try {
                    workers.execute {
                        try { socket.use { serve(it) } } catch (_: Exception) { /* No sensitive request logging. */ }
                        finally { clients.remove(socket) }
                    }
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    clients.remove(socket)
                    socket.close()
                }
            }
        } catch (_: Exception) {
            if (!listener.isClosed) onFailure()
        }
    }, "lightmemo-mcp").apply { isDaemon = true; start() }

    private fun serve(socket: Socket) {
        socket.soTimeout = 10_000
        val input = BufferedInputStream(socket.getInputStream())
        var budget = 16 * 1024
        fun line(): String {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b == -1 || --budget < 0) throw HttpError(if (budget < 0) 431 else 400)
                if (b == 10) break
                bytes.write(b)
            }
            return bytes.toString(Charsets.US_ASCII.name()).removeSuffix("\r")
        }
        val reply = try {
            val start = line().split(' ')
            if (start.size != 3 || start[2] != "HTTP/1.1") throw HttpError(400)
            val uri = try { URI(start[1]) } catch (_: Exception) { throw HttpError(400) }
            if (uri.isAbsolute || uri.rawFragment != null) throw HttpError(400)
            val headers = mutableMapOf<String, String>()
            while (true) {
                val row = line()
                if (row.isEmpty()) break
                val colon = row.indexOf(':')
                if (colon <= 0) throw HttpError(400)
                val key = row.substring(0, colon).lowercase(java.util.Locale.ROOT)
                if (headers.put(key, row.substring(colon + 1).trim()) != null) throw HttpError(400)
            }
            val host = headers["host"] ?: throw HttpError(400)
            if (host !in setOf("127.0.0.1:$port", "localhost:$port")) throw HttpError(403)
            headers["origin"]?.let { origin ->
                if (origin !in setOf("http://127.0.0.1:$port", "http://localhost:$port")) throw HttpError(403)
            }
            if (uri.rawPath == "/health" && start[0] == "GET") {
                RpcReply(200, "{\"status\":\"ok\"}")
            } else {
                if (uri.rawPath != "/mcp") throw HttpError(404)
                val queryToken = uri.rawQuery?.split('&')?.singleOrNull { it.startsWith("token=") }?.removePrefix("token=")
                val bearer = headers["authorization"]?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
                if (!MessageDigest.isEqual(tokenBytes, (bearer ?: queryToken ?: "").toByteArray(Charsets.UTF_8))) throw HttpError(401)
                if (start[0] != "POST") throw HttpError(405)
                if (headers["content-type"]?.substringBefore(';')?.trim()?.lowercase() != "application/json") throw HttpError(415)
                if (headers.containsKey("transfer-encoding")) throw HttpError(400)
                val length = headers["content-length"]?.toIntOrNull() ?: throw HttpError(400)
                if (length > 65536) throw HttpError(413)
                if (length < 0) throw HttpError(400)
                val body = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = input.read(body, read, length - read)
                    if (n == -1) throw HttpError(400)
                    read += n
                }
                // Parse and dispatch on this worker; suspending repositories choose their own IO context.
                runBlocking(job) { protocol.handle(body.toString(Charsets.UTF_8)) }
            }
        } catch (e: HttpError) { RpcReply(e.status) }
        val body = reply.body.toByteArray(Charsets.UTF_8)
        val reason = when (reply.status) {
            200 -> "OK"; 202 -> "Accepted"; 401 -> "Unauthorized"; 403 -> "Forbidden"
            404 -> "Not Found"; 405 -> "Method Not Allowed"; 413 -> "Content Too Large"
            415 -> "Unsupported Media Type"; 431 -> "Request Header Fields Too Large"; else -> "Bad Request"
        }
        val extra = if (reply.status == 405) "Allow: POST\r\n" else ""
        socket.getOutputStream().apply {
            write(("HTTP/1.1 ${reply.status} $reason\r\nContent-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\nConnection: close\r\nCache-Control: no-store\r\n$extra\r\n").toByteArray(Charsets.US_ASCII))
            write(body)
            flush()
        }
    }

    override fun close() {
        listener.close()
        job.cancel()
        clients.forEach { runCatching { it.close() } }
        workers.shutdownNow()
    }

    private class HttpError(val status: Int) : Exception()

    companion object {
        internal fun createWorkers(idleTimeoutMillis: Long = 30_000): ThreadPoolExecutor {
            val sequence = AtomicInteger()
            return ThreadPoolExecutor(4, 4, idleTimeoutMillis, TimeUnit.MILLISECONDS,
                ArrayBlockingQueue(16), { task ->
                    Thread(task, "lightmemo-mcp-worker-${sequence.incrementAndGet()}").apply { isDaemon = true }
                }).apply { allowCoreThreadTimeOut(true) }
        }
    }
}
