package com.click.lightmemo.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.recognition.RecognitionImageCache
import com.click.lightmemo.recognition.RecognitionRequest
import com.click.lightmemo.recognition.RecognitionRequestType
import com.click.lightmemo.recognition.RecognitionTaskStatus
import com.click.lightmemo.recognition.RecognitionTaskStore
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreparedFoodImageTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun uploadCacheAndPersistenceUseTheSameBytesAndKeepExistingFingerprint() = runBlocking {
        // A known 1x1 PNG is sufficient to exercise byte reuse without depending on a device codec.
        val bytes = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=", Base64.NO_WRAP)
        val image = PreparedFoodImage(bytes)
        assertArrayEquals(bytes, Base64.decode(image.base64, Base64.NO_WRAP))
        assertEquals(RecognitionImageCache.digest(image.base64), image.recognitionFingerprint)
        val oldFileHash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(oldFileHash, image.contentHash)
        // FileProvider's canonical-path checks assume Android '/' paths; use a registered
        // content stream on the Windows JVM. The real provider round trip is in FoodImagesTest.
        val file = File(context.filesDir, "images/${image.contentHash}.jpg").apply { parentFile?.mkdirs() }
        image.writeTo(file)
        val uri = Uri.parse("content://${context.packageName}.fileprovider/internal_images/${file.name}")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { file.inputStream() }
        try {
            assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            val preparedAgain = FoodImages.prepare(context, uri)
            // This would change under JPEG re-encoding, so equality verifies the stored-file path.
            assertEquals(image.base64, preparedAgain.base64)
            assertEquals(image.recognitionFingerprint, preparedAgain.recognitionFingerprint)
            assertEquals(uri, FoodImages.persist(context, uri))
        } finally { file.delete() }
    }

    @Test fun cacheConfirmationPersistsPreparedUriAndPreservesOtherRequestFieldsAcrossReload() = runBlocking {
        val image = PreparedFoodImage(byteArrayOf(1, 2, 3, 4))
        val uri = Uri.parse("content://${context.packageName}.fileprovider/internal_images/${image.contentHash}.jpg")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { byteArrayOf(1, 2, 3, 4).inputStream() }
        val store = RecognitionTaskStore(context)
        val request = RecognitionRequest(type = RecognitionRequestType.IMAGE, imageUri = "content://original/photo",
            mealType = MealType.LUNCH, targetDateEpochDay = 20000, mealMinuteOfDay = 720,
            note = "备注", selectedTags = listOf("两人份"), plateSize = "20cm")
        try {
            store.begin(request)
            store.awaitCacheConfirmation("outdated-id", uri.toString())
            assertEquals(request, store.state.value!!.request)
            store.awaitCacheConfirmation(request.id, uri.toString())
            val restored = RecognitionTaskStore(context).state.value!!
            assertEquals(RecognitionTaskStatus.AWAITING_CACHE_CONFIRMATION, restored.status)
            assertEquals(request.copy(imageUri = uri.toString()), restored.request)
            assertEquals(image.base64, FoodImages.encode(context, Uri.parse(restored.request.imageUri)))
        } finally {
            store.clear()
        }
    }
}
