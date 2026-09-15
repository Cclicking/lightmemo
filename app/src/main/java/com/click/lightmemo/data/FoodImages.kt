package com.click.lightmemo.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object FoodImages {
    /** ImageDecoder applies EXIF orientation while decoding only the requested size. */
    suspend fun encode(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val scale = (1024.0 / maxOf(info.size.width, info.size.height)).coerceAtMost(1.0)
            decoder.setTargetSize(
                (info.size.width * scale).toInt().coerceAtLeast(1),
                (info.size.height * scale).toInt().coerceAtLeast(1),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            val output = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) { "图片压缩失败" }
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Store the normalized JPEG in app-private storage and return a stable provider URI.
     * Existing app-private image URIs are returned as-is so saving a recognized meal does
     * not encode and write the same image twice.
     */
    suspend fun persist(context: Context, uri: Uri): Uri {
        if (isStored(context, uri)) return uri
        return persistEncoded(context, encode(context, uri))
    }

    /** Copy a captured image into the system gallery and return its MediaStore URI. */
    suspend fun saveToGallery(
        context: Context,
        sourceUri: Uri,
        location: GallerySaveLocation = GallerySaveLocation.PICTURES,
    ): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "轻食记_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, location.relativePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val galleryUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建相册图片")

        try {
            resolver.openInputStream(sourceUri)?.use { input ->
                resolver.openOutputStream(galleryUri)?.use { output ->
                    input.copyTo(output)
                } ?: error("无法写入相册图片")
            } ?: error("无法读取拍摄的图片")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                check(
                    resolver.update(
                        galleryUri,
                        ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                        null,
                        null,
                    ) == 1,
                ) { "无法完成相册保存" }
            }
            galleryUri
        } catch (error: Throwable) {
            resolver.delete(galleryUri, null, null)
            throw error
        }
    }

    suspend fun persistEncoded(context: Context, encoded: String): Uri = withContext(Dispatchers.IO) {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val directory = File(context.filesDir, INTERNAL_DIRECTORY).apply { mkdirs() }
        val file = File(directory, "$digest.jpg")
        if (!file.exists()) file.writeBytes(bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Snapshot of clearable photo cache: temporary camera captures and app-private meal
     * photos. Gallery copies are untouched.
     */
    data class PhotoCacheStats(
        val cameraBytes: Long,
        val internalImageBytes: Long,
    ) {
        val totalBytes: Long get() = cameraBytes + internalImageBytes
    }

    suspend fun photoCacheStats(context: Context): PhotoCacheStats = withContext(Dispatchers.IO) {
        PhotoCacheStats(
            cameraBytes = directoryBytes(cameraCacheDir(context)),
            internalImageBytes = directoryBytes(internalImagesDir(context)),
        )
    }

    /** Deletes temporary camera captures and app-private meal photos. */
    suspend fun clearPhotoCache(context: Context): PhotoCacheStats = withContext(Dispatchers.IO) {
        val stats = photoCacheStats(context)
        cameraCacheDir(context).listFiles()?.forEach { it.delete() }
        internalImagesDir(context).listFiles()?.forEach { it.delete() }
        stats
    }

    private fun cameraCacheDir(context: Context): File =
        File(context.cacheDir, CAMERA_DIRECTORY).apply { mkdirs() }

    private fun internalImagesDir(context: Context): File =
        File(context.filesDir, INTERNAL_DIRECTORY).apply { mkdirs() }

    private fun directoryBytes(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun isStored(context: Context, uri: Uri): Boolean =
        uri.scheme == "content" &&
            uri.authority == "${context.packageName}.fileprovider" &&
            uri.pathSegments.firstOrNull() == INTERNAL_PATH_NAME

    private const val INTERNAL_DIRECTORY = "images"
    private const val INTERNAL_PATH_NAME = "internal_images"
    private const val CAMERA_DIRECTORY = "camera"
}
