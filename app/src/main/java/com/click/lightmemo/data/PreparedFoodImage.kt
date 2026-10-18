package com.click.lightmemo.data

import android.util.Base64
import java.io.File
import java.security.MessageDigest

/** One normalized JPEG shared by upload, cache identity and persistence. */
internal class PreparedFoodImage(private val jpegBytes: ByteArray) {
    val base64: String by lazy { Base64.encodeToString(jpegBytes, Base64.NO_WRAP) }
    val contentHash: String by lazy { digest(jpegBytes) }
    // Preserve the existing result-cache key so previously recognized photos still hit.
    val recognitionFingerprint: String by lazy { digest(base64.toByteArray(Charsets.UTF_8)) }

    fun writeTo(file: File) { file.writeBytes(jpegBytes) }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
