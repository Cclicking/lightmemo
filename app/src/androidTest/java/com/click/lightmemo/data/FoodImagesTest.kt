package com.click.lightmemo.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodImagesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun jpeg(width: Int, height: Int): File {
        val file = File.createTempFile("food-image-test", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try { file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) } }
        finally { bitmap.recycle() }
        return file
    }

    private suspend fun size(file: File): Pair<Int, Int> {
        val bytes = Base64.decode(FoodImages.encode(context, Uri.fromFile(file)), Base64.NO_WRAP)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outWidth to options.outHeight
    }

    @Test fun largeCameraImageIsDownsampled() = runBlocking {
        val file = jpeg(6000, 4000)
        try {
            val (width, height) = size(file)
            assertEquals(1024, width)
            assertTrue(height in 682..683)
        } finally { file.delete() }
    }

    @Test fun rotatedCameraImageHasCorrectOrientation() = runBlocking {
        val file = jpeg(80, 40)
        try {
            ExifInterface(file.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            assertEquals(40 to 80, size(file))
        } finally { file.delete() }
    }
}
