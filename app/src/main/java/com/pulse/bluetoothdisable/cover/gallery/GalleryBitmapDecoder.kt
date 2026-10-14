package com.pulse.bluetoothdisable.cover.gallery

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Display a bounded preview; the full sanitized encrypted photo and its metadata stay intact. */
internal fun decodeGalleryPreview(bytes: ByteArray, maxSide: Int): ImageBitmap? {
    require(maxSide > 0)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val longest = maxOf(bounds.outWidth, bounds.outHeight).toLong()
    var sample = 1
    while ((longest + sample - 1) / sample > maxSide) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inScaled = false
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}
