package com.click.lightmemo.mcp

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpPreferencesTest {
    @get:Rule val folder = TemporaryFolder()
    private fun context(): Context {
        val root = folder.newFolder()
        return object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getNoBackupFilesDir(): File = root
        }
    }

    @Test fun newStoreInstanceReusesPersistedTokenAndPreference() {
        val context = context()
        val store = McpPreferences(context)
        assertFalse(store.autoStart())
        val token = store.token()
        assertTrue(token.matches(Regex("[0-9a-f]{64}")))
        store.setAutoStart(true)
        val reopened = McpPreferences(context)
        assertTrue("Credential must survive store recreation", token == reopened.token())
        assertTrue(reopened.autoStart())
        reopened.setAutoStart(false)
        assertFalse(McpPreferences(context).autoStart())
        assertTrue("Preference writes must preserve the token", token == store.token())
    }

    @Test fun resetReplacesTokenWithoutChangingAutoStart() {
        val context = context()
        val store = McpPreferences(context)
        store.setAutoStart(true)
        val before = store.token()
        store.resetToken()
        val after = store.token()
        assertTrue("Reset must replace the old credential", before != after)
        assertTrue("Reset must be persisted", after == McpPreferences(context).token())
        assertTrue(store.autoStart())
    }

    @Test fun concurrentFirstReadsCreateOneCredential() {
        val context = context()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = (1..20).map { executor.submit<String> { McpPreferences(context).token() } }
                .map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, results.distinct().size)
        } finally { executor.shutdownNow() }
    }
}
