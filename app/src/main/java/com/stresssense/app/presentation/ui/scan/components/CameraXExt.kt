package com.stresssense.app.presentation.ui.scan.components

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy

/**
 * Convert an RGBA_8888 ImageProxy (ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
 * to a Bitmap. The pixel buffer maps directly onto ARGB_8888 with no YUV work.
 */
fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    buffer.rewind()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(buffer)
    return bitmap
}
