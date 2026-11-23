package com.example.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class CropOutput(
    val bitmap: Bitmap,
    val cropDurationMs: Long,
    val isMostlyUniformBlank: Boolean
)

object BitmapCropper {

    /**
     * Crops [source] strictly to [selectionRect] mapped from [overlayWidth] x [overlayHeight]
     * into the physical [source] dimensions without distorting aspect ratio.
     * Accounts for full-screen overlay offset [overlayTopOffsetPx] if the overlay window does not
     * start at physical screen y = 0.
     */
    fun cropScreenArea(
        source: Bitmap,
        selectionRect: RectF,
        overlayWidth: Int,
        overlayHeight: Int,
        overlayLeftOffsetPx: Int = 0,
        overlayTopOffsetPx: Int = 0
    ): CropOutput? {
        val startNs = System.nanoTime()
        if (overlayWidth <= 0 || overlayHeight <= 0 || source.width <= 0 || source.height <= 0) {
            return null
        }

        val scaleX = source.width.toFloat() / overlayWidth.toFloat()
        val scaleY = source.height.toFloat() / overlayHeight.toFloat()

        val left = ((selectionRect.left + overlayLeftOffsetPx) * scaleX).roundToInt()
            .coerceIn(0, source.width - 1)
        val top = ((selectionRect.top + overlayTopOffsetPx) * scaleY).roundToInt()
            .coerceIn(0, source.height - 1)
        val right = ((selectionRect.right + overlayLeftOffsetPx) * scaleX).roundToInt()
            .coerceIn(left + 1, source.width)
        val bottom = ((selectionRect.bottom + overlayTopOffsetPx) * scaleY).roundToInt()
            .coerceIn(top + 1, source.height)

        val width = right - left
        val height = bottom - top
        if (width < 12 || height < 12) return null

        val cropped = Bitmap.createBitmap(source, left, top, width, height)
        val isBlank = detectUniformBlankArea(cropped)
        val durationMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)

        return CropOutput(
            bitmap = cropped,
            cropDurationMs = durationMs,
            isMostlyUniformBlank = isBlank
        )
    }

    /**
     * Samples a grid of pixels across the cropped area to detect completely blank/solid regions
     * so NOVA can ignore empty captures and ask the user to select an area with text.
     */
    fun detectUniformBlankArea(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return true

        val stepX = max(1, w / 24)
        val stepY = max(1, h / 24)
        val basePixel = bitmap.getPixel(w / 2, h / 2)
        val baseR = Color.red(basePixel)
        val baseG = Color.green(basePixel)
        val baseB = Color.blue(basePixel)

        var sampled = 0
        var contrasting = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val p = bitmap.getPixel(x, y)
                val diff = abs(Color.red(p) - baseR) +
                    abs(Color.green(p) - baseG) +
                    abs(Color.blue(p) - baseB)
                if (diff > 36) {
                    contrasting++
                }
                sampled++
                x += stepX
            }
            y += stepY
        }
        return sampled == 0 || (contrasting.toFloat() / sampled.toFloat()) < 0.015f
    }

    /**
     * Decodes an image selected via Android Photo Picker safely with bounded dimensions
     * to prevent OOM while keeping sharp legibility for ML Kit OCR.
     */
    fun decodeSampledBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDimensionPx: Int = 2048
    ): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val rawW = boundsOptions.outWidth
            val rawH = boundsOptions.outHeight
            if (rawW <= 0 || rawH <= 0) return null

            var inSampleSize = 1
            while (max(rawW / inSampleSize, rawH / inSampleSize) > maxDimensionPx) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clampSelectionRect(rect: RectF, boundsWidth: Float, boundsHeight: Float, minSizePx: Float): RectF {
        val clampedWidth = min(max(rect.width(), minSizePx), boundsWidth)
        val clampedHeight = min(max(rect.height(), minSizePx), boundsHeight)
        val left = rect.left.coerceIn(0f, (boundsWidth - clampedWidth).coerceAtLeast(0f))
        val top = rect.top.coerceIn(0f, (boundsHeight - clampedHeight).coerceAtLeast(0f))
        return RectF(left, top, left + clampedWidth, top + clampedHeight)
    }

    fun toIntRect(rectF: RectF): Rect = Rect(
        rectF.left.roundToInt(),
        rectF.top.roundToInt(),
        rectF.right.roundToInt(),
        rectF.bottom.roundToInt()
    )
}
