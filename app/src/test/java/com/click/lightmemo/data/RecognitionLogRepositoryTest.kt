package com.click.lightmemo.data

import com.click.lightmemo.recognition.RecognitionLogEvent
import com.click.lightmemo.recognition.RecognitionLogKind
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class RecognitionLogRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun event(number: Int) = RecognitionLogEvent("task-$number", RecognitionLogKind.TASK_START, elapsedMs = 0)
    private suspend fun snapshot(repository: RecognitionLogRepository) =
        Json.decodeFromString<RecognitionLogSnapshot>(repository.exportJson())

    @Test fun disabledByDefaultAndToggleAndClearSurviveRestart() = runTest {
        val file = File(temporary.root, "log.json")
        val repository = RecognitionLogRepository(file, backgroundScope)
        repository.record(event(0)) // Startup must load the saved toggle before deciding whether to append.
        assertTrue(snapshot(repository).events.isEmpty())
        assertFalse(repository.state.value.snapshot.enabled)
        repository.setEnabled(true)
        repository.record(event(1))
        repository.setEnabled(false)
        repository.record(event(2))
        assertEquals(listOf("task-1"), snapshot(repository).events.map { it.task })
        val restarted = RecognitionLogRepository(file, backgroundScope)
        assertFalse(snapshot(restarted).enabled)
        assertEquals(1, restarted.state.value.snapshot.events.size)
        restarted.clear()
        assertTrue(snapshot(RecognitionLogRepository(file, backgroundScope)).events.isEmpty())
    }

    @Test fun boundedRetentionExportsAllRetainedEventsAndRecoversAtStartup() = runTest {
        val file = File(temporary.root, "log.json")
        val repository = RecognitionLogRepository(file, backgroundScope)
        repository.setEnabled(true)
        repeat(RecognitionLogRepository.MAX_EVENTS + 5) { repository.record(event(it)) }
        val saved = snapshot(repository)
        assertEquals(RecognitionLogRepository.MAX_EVENTS, saved.events.size)
        assertEquals("task-5", saved.events.first().task)
        val restarted = RecognitionLogRepository(file, backgroundScope)
        restarted.record(event(999)) // Enabled flag hasn't finished loading yet.
        assertEquals("task-999", snapshot(restarted).events.last().task)
    }

    @Test fun clearSerializesWithPendingEventsAndKeepsLoggingEnabled() = runTest {
        val repository = RecognitionLogRepository(File(temporary.root, "log.json"), backgroundScope)
        repository.setEnabled(true)
        repository.record(event(1))
        repository.clear()
        repository.record(event(2))
        val result = snapshot(repository)
        assertTrue(result.enabled)
        assertEquals(listOf("task-2"), result.events.map { it.task })
    }

    @Test fun corruptFileAndStorageFailureAreVisibleAndMemoryExportRemainsAvailable() = runTest {
        val file = temporary.newFile("corrupt.json").apply { writeText("broken") }
        val repository = RecognitionLogRepository(file, backgroundScope)
        snapshot(repository)
        assertTrue(repository.state.value.storageError)
        repository.setEnabled(true)
        assertFalse(repository.state.value.storageError)
        val blocker = temporary.newFile("not-directory")
        val broken = RecognitionLogRepository(File(blocker, "log.json"), backgroundScope)
        assertTrue(runCatching { broken.setEnabled(true) }.isFailure)
        broken.record(event(3))
        assertEquals("task-3", snapshot(broken).events.single().task)
        assertTrue(broken.state.value.storageError)
    }
}
