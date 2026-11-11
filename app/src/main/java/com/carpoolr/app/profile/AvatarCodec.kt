package com.carpoolr.app.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

/**
 * Center-crops and encodes rider profile avatars into compact (~8–16 KB) 192x192 JPEG data URIs
 * so avatars load and persist without external image libraries or public storage buckets.
 */
object AvatarCodec {
    private const val TARGET_PX = 192

    fun decodeUriToBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxDim = maxOf(info.size.width, info.size.height)
                if (maxDim > 1024) decoder.setTargetSampleSize(maxDim / 512)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            val sample = if (maxDim > 1024) (maxDim / 512).coerceAtLeast(1) else 1
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@runCatching null
            val orient = context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            rotateIfNeeded(raw, orient)
        }
    }.getOrNull()

    fun encodeUri(context: Context, uri: Uri): String? =
        decodeUriToBitmap(context, uri)?.let(::encodeBitmap)

    fun encodeBitmap(src: Bitmap): String {
        val side = minOf(src.width, src.height).coerceAtLeast(1)
        val x = ((src.width - side) / 2).coerceAtLeast(0)
        val y = ((src.height - side) / 2).coerceAtLeast(0)
        val square = Bitmap.createBitmap(src, x, y, side, side)
        val scaled = if (side == TARGET_PX) square else Bitmap.createScaledBitmap(square, TARGET_PX, TARGET_PX, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 76, out)
        var bytes = out.toByteArray()
        if (bytes.size > 45_000) {
            out.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, 58, out)
            bytes = out.toByteArray()
        }
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun decodeImageBitmap(dataUri: String?): ImageBitmap? {
        if (dataUri.isNullOrBlank()) return null
        return runCatching {
            val payload = dataUri.substringAfter("base64,", dataUri)
            val bytes = Base64.decode(payload, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    private fun rotateIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }
}
