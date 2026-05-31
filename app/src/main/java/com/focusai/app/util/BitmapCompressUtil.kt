package com.focusai.app.util

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

object BitmapCompressUtil {

    private const val MAX_EDGE = 800
    private const val JPEG_QUALITY = 50

    fun toJpegBase64(source: Bitmap): String {
        val bytes = compressToJpegBytes(source)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** 缩放 + JPEG 压缩，供截屏上传与 Base64 编码复用。 */
    fun compressToJpegBytes(source: Bitmap): ByteArray {
        val scaled = scaleDown(source, MAX_EDGE)
        val needRecycleScaled = scaled !== source
        val jpegBytes = ByteArrayOutputStream(64 * 1024).use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            output.toByteArray()
        }
        if (needRecycleScaled) scaled.recycle()
        return jpegBytes
    }

    private fun scaleDown(source: Bitmap, maxEdge: Int): Bitmap {
        val w = source.width
        val h = source.height
        val longest = maxOf(w, h)
        if (longest <= maxEdge) return source
        val ratio = maxEdge.toFloat() / longest
        val targetW = (w * ratio).toInt().coerceAtLeast(1)
        val targetH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }
}
