package com.click.lightmemo.mcp

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

/** Local connection identity is excluded from cloud backup and device transfer. */
class McpPreferences(context: Context) {
    private val file = File(context.noBackupFilesDir, "mcp-preferences.json")

    fun token(): String = synchronized(lock) {
        val values = read()
        val stored = values.optString("token")
        if (stored.isNotEmpty()) {
            check(tokenPattern.matches(stored)) { "Invalid MCP credential" }
            stored
        } else {
            newToken().also { write(values.put("token", it)) }
        }
    }

    fun resetToken(): Unit = synchronized(lock) {
        write(read().put("token", newToken()))
    }

    fun autoStart(): Boolean = synchronized(lock) { read().optBoolean("auto_start", false) }

    fun setAutoStart(enabled: Boolean): Unit = synchronized(lock) {
        write(read().put("auto_start", enabled))
    }

    private fun read(): JSONObject = try {
        file.inputStream().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
    } catch (_: FileNotFoundException) { JSONObject() }

    private fun write(values: JSONObject) {
        val pending = File(file.parentFile, "${file.name}.new")
        try {
            FileOutputStream(pending).use { stream ->
                stream.write(values.toString().toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            // Replace only after a durable write. Unlike File.renameTo, this also replaces on Windows tests.
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Exception) {
            pending.delete()
            throw error
        }
    }

    private fun newToken(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    companion object {
        private val lock = Any()
        private val tokenPattern = Regex("[0-9a-f]{64}")
    }
}
