package com.click.lightmemo.network

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.*
import okio.Timeout
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HttpAwaitTest {
    private class PendingCall : Call {
        var canceled = false
        var started = false
        lateinit var callback: Callback
        override fun request() = Request.Builder().url("https://example.invalid").build()
        override fun execute(): Response = error("Synchronous call must not be used")
        override fun enqueue(responseCallback: Callback) { started = true; callback = responseCallback }
        override fun cancel() { canceled = true }
        override fun isExecuted() = started
        override fun isCanceled() = canceled
        override fun timeout() = Timeout()
        override fun clone(): Call = PendingCall()
    }

    @Test fun cancelingCoroutineCancelsUnderlyingHttpCall() = runTest {
        val call = PendingCall()
        val job = launch { call.awaitResponse().close() }
        runCurrent()
        assertTrue(call.started)
        job.cancelAndJoin()
        assertTrue(call.canceled)
        // A late failure from OkHttp must not escape the canceled request.
        call.callback.onFailure(call, java.io.IOException("Canceled"))
    }
}
