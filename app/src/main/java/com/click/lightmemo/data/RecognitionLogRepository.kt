package com.click.lightmemo.data

import com.click.lightmemo.recognition.RecognitionLogEvent
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RecognitionLogSnapshot(
    val version: Int = 1,
    val enabled: Boolean = false,
    val events: List<RecognitionLogEvent> = emptyList(),
)

data class RecognitionLogState(
    val snapshot: RecognitionLogSnapshot = RecognitionLogSnapshot(),
    val loading: Boolean = true,
    val storageError: Boolean = false,
)

/** One IO worker serializes toggle/append/clear/export; recognition never waits for disk. */
class RecognitionLogRepository(private val file: File, scope: CoroutineScope) {
    companion object { const val MAX_EVENTS = 300 }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val mutableState = MutableStateFlow(RecognitionLogState())
    val state = mutableState.asStateFlow()
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private sealed interface Command {
        data class Append(val event: RecognitionLogEvent) : Command
        data class Change(val enabled: Boolean?, val clear: Boolean, val done: CompletableDeferred<Unit>) : Command
        data class Export(val done: CompletableDeferred<String>) : Command
    }

    init {
        scope.launch {
            var snapshot = RecognitionLogSnapshot()
            var storageError = false
            try {
                if (file.exists()) snapshot = json.decodeFromString<RecognitionLogSnapshot>(file.readText())
                    .let { it.copy(events = it.events.takeLast(MAX_EVENTS)) }
            } catch (_: Exception) { storageError = true }
            mutableState.value = RecognitionLogState(snapshot, loading = false, storageError = storageError)
            for (command in commands) {
                if (command is Command.Export) {
                    command.done.complete(json.encodeToString(snapshot))
                    continue
                }
                val next = when (command) {
                    is Command.Append -> if (snapshot.enabled) snapshot.copy(
                        events = (snapshot.events + command.event).takeLast(MAX_EVENTS)) else continue
                    is Command.Change -> snapshot.copy(enabled = command.enabled ?: snapshot.enabled,
                        events = if (command.clear) emptyList() else snapshot.events)
                }
                snapshot = next
                var failure: Exception? = null
                try {
                    persist(snapshot)
                    storageError = false
                } catch (error: Exception) {
                    storageError = true
                    failure = error
                }
                mutableState.value = RecognitionLogState(snapshot, loading = false, storageError = storageError)
                (command as? Command.Change)?.done?.let {
                    if (failure == null) it.complete(Unit) else it.completeExceptionally(failure)
                }
            }
        }
    }

    fun record(event: RecognitionLogEvent) {
        // A disabled logger does not allocate queued work. Commands already queued obey toggle/clear ordering.
        if (state.value.loading || state.value.snapshot.enabled) commands.trySend(Command.Append(event))
    }

    suspend fun setEnabled(enabled: Boolean) = change(enabled = enabled)
    suspend fun clear() = change(clear = true)
    private suspend fun change(enabled: Boolean? = null, clear: Boolean = false) {
        val done = CompletableDeferred<Unit>()
        commands.send(Command.Change(enabled, clear, done))
        done.await()
    }

    suspend fun exportJson(): String {
        val done = CompletableDeferred<String>()
        commands.send(Command.Export(done))
        return done.await()
    }

    private fun persist(snapshot: RecognitionLogSnapshot) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(json.encodeToString(snapshot))
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
