package com.click.lightmemo.recognition

import androidx.test.core.app.ApplicationProvider
import com.click.lightmemo.FoodApp
import com.click.lightmemo.domain.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = FoodApp::class)
class RecognitionCancellationTest {
    private fun request() = RecognitionRequest(
        type = RecognitionRequestType.TEXT,
        text = "一个鸡蛋",
        mealType = MealType.BREAKFAST,
        targetDateEpochDay = 20000,
        mealMinuteOfDay = 480,
    )

    @Test
    fun cancellationClearsTaskAndIgnoresLateCompletion() {
        val app = ApplicationProvider.getApplicationContext<FoodApp>()
        val store = app.recognitionTaskStore
        val request = request()
        store.begin(request)
        RecognitionService.cancel(app, request.id)
        val intent = shadowOf(app).nextStartedService
        val service = Robolectric.buildService(RecognitionService::class.java).create()
        service.get().onStartCommand(intent, 0, 1)

        assertNull(store.state.value)
        store.complete(request.id)
        assertNull(store.state.value)
        assertNull(RecognitionTaskStore(app).state.value)
        service.destroy()
    }

    @Test
    fun staleCancellationPreservesNewTask() {
        val app = ApplicationProvider.getApplicationContext<FoodApp>()
        val store = app.recognitionTaskStore
        val previous = request()
        val current = request()
        store.begin(previous)
        RecognitionService.cancel(app, previous.id)
        val intent = shadowOf(app).nextStartedService
        store.begin(current)
        val service = Robolectric.buildService(RecognitionService::class.java).create()
        service.get().onStartCommand(intent, 0, 1)

        assertEquals(current.id, store.state.value?.request?.id)
        assertEquals(RecognitionTaskStatus.RUNNING, store.state.value?.status)
        service.destroy()
        store.clear()
    }
}
