package com.read.photoeditor.data

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BlurCheckResult(
    val variance: Double,
    val isBlurry: Boolean
)

object BlurDetector {

    // Threshold for downscaled 160px image; lower values signify smooth, out-of-focus blur
    const val DEFAULT_BLUR_THRESHOLD = 110.0

    /**
     * Computes the variance of the Laplacian filter on a downscaled grayscale version of the bitmap.
     * Low variance (< threshold) flags the image as blurry.
     * Fully offline and on-device without external libraries.
     */
    suspend fun checkBlur(
        bitmap: Bitmap,
        threshold: Double = DEFAULT_BLUR_THRESHOLD
    ): BlurCheckResult = withContext(Dispatchers.Default) {
        val targetWidth = 160
        val targetHeight = ((bitmap.height.toFloat() / bitmap.width.toFloat()) * targetWidth).toInt().coerceAtLeast(1)

        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        val width = scaled.width
        val height = scaled.height

        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled != bitmap) {
            scaled.recycle()
        }

        // Convert to grayscale values
        val gray = IntArray(width * height)
        for (i in 0 until width * height) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            // Luminance: 0.299R + 0.587G + 0.114B
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        val variance = computeLaplacianVariance(gray, width, height)
        BlurCheckResult(
            variance = variance,
            isBlurry = variance < threshold
        )
    }

    /**
     * Pure Laplacian variance computation over a grayscale pixel array.
     * Can be tested without Android Bitmap mocks.
     */
    fun computeLaplacianVariance(gray: IntArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3 || gray.size < width * height) return 0.0

        // Apply 3x3 Laplacian filter kernel:
        //  0  1  0
        //  1 -4  1
        //  0  1  0
        var sum = 0.0
        var count = 0
        val laplacianResponses = DoubleArray((width - 2) * (height - 2))

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = gray[y * width + x]
                val top = gray[(y - 1) * width + x]
                val bottom = gray[(y + 1) * width + x]
                val left = gray[y * width + (x - 1)]
                val right = gray[y * width + (x + 1)]

                val lap = (top + bottom + left + right - 4 * center).toDouble()
                laplacianResponses[count] = lap
                sum += lap
                count++
            }
        }

        if (count == 0) return 0.0

        val mean = sum / count
        var varianceSum = 0.0
        for (i in 0 until count) {
            val diff = laplacianResponses[i] - mean
            varianceSum += diff * diff
        }

        return varianceSum / count
    }
}
