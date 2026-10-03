package com.read.photoeditor.editing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.read.photoeditor.data.model.PhotoEditParams

/**
 * Result of the local, offline image analysis.
 * Computed entirely on-device without calling any AI model.
 */
data class LightingAnalysis(
    val averageLuminance: Float,       // 0..1 (0 = pure black, 1 = pure white)
    val redToBlueRatio: Float,         // >1.2 = warm incandescent/indoor, <0.88 = cool overcast
    val shadowFraction: Float,         // proportion of pixels in deep shadow (< 64/255)
    val highlightFraction: Float,      // proportion of pixels near blown highlights (> 215/255)
    val condition: String,             // "underexposed_or_backlit", "indoor_warm_light", "overexposed_or_bright_daylight", "cool_overcast", "neutral_balanced"
    val reason: String
)

/**
 * A conditional rule inferred by the VLM once per trip during reference calibration.
 */
data class ConditionalRule(
    val condition: String,
    val exposureShift: Float = 0f,
    val whiteBalanceShiftK: Int = 0,
    val saturationShift: Float = 0f,
    val shadowsLift: Float = 0f,
    val contrastShift: Float = 0f,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val blurBackground: Boolean = false,
    val reasoning: String = ""
)

/**
 * Executes PhotoEditParams on an actual Bitmap. This is the "hands" — it never
 * decides WHAT to do (that's VLMClient's job), only HOW to apply it to pixels.
 *
 * Also hosts the local, offline lighting classifier and rule-matcher to eliminate
 * per-photo API calls during bulk trip editing.
 */
object ImageProcessor {

    /**
     * LOCAL, OFFLINE CLASSIFIER (Requirement 2):
     * Analyzes a photo's basic lighting condition completely on-device without calling any AI model.
     * Uses a fast subsampled pixel grid (~64x64 samples) to measure average luminance, RGB histograms,
     * and shadow/highlight concentrations in under 2ms.
     */
    fun classifyLighting(bitmap: Bitmap): LightingAnalysis {
        val width = bitmap.width
        val height = bitmap.height

        val stepX = (width / 64).coerceAtLeast(1)
        val stepY = (height / 64).coerceAtLeast(1)

        var totalLum = 0.0
        var totalR = 0.0
        var totalG = 0.0
        var totalB = 0.0
        var shadowCount = 0
        var highlightCount = 0
        var sampleCount = 0

        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff

                // Relative luminance (ITU-R BT.709)
                val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
                totalLum += lum
                totalR += r
                totalG += g
                totalB += b

                if (lum < 64f) shadowCount++
                if (lum > 215f) highlightCount++
                sampleCount++

                x += stepX
            }
            y += stepY
        }

        if (sampleCount == 0) {
            return LightingAnalysis(0.5f, 1.0f, 0f, 0f, "neutral_balanced", "Empty image sample fallback")
        }

        val avgLumNorm = (totalLum / sampleCount / 255.0).toFloat().coerceIn(0f, 1f)
        val avgR = (totalR / sampleCount).toFloat()
        val avgB = (totalB / sampleCount).toFloat()
        val rOverB = avgR / (avgB.coerceAtLeast(1f))
        val shadowFraction = shadowCount.toFloat() / sampleCount
        val highlightFraction = highlightCount.toFloat() / sampleCount

        // RULE MATCHING DECISION TREE (Offline Local Heuristics)
        val condition: String
        val reason: String

        // 1. Backlit or Underexposed:
        if (avgLumNorm < 0.36f || (shadowFraction > 0.38f && avgLumNorm < 0.55f)) {
            condition = "underexposed_or_backlit"
            reason = "Low average luminance (${(avgLumNorm * 100).toInt()}%) or deep shadows (${(shadowFraction * 100).toInt()}%)."
        }
        // 2. Overexposed or Harsh Direct Daylight:
        else if (avgLumNorm > 0.68f || highlightFraction > 0.28f) {
            condition = "overexposed_or_bright_daylight"
            reason = "High average luminance (${(avgLumNorm * 100).toInt()}%) or strong highlights (${(highlightFraction * 100).toInt()}%)."
        }
        // 3. Indoor Warm / Incandescent Light:
        else if (rOverB > 1.20f && avgLumNorm < 0.65f) {
            condition = "indoor_warm_light"
            reason = "Warm indoor color temperature (R/B ratio = ${(rOverB * 100).toInt() / 100f})."
        }
        // 4. Cool Overcast Sky:
        else if (rOverB < 0.88f) {
            condition = "cool_overcast"
            reason = "Cool overcast / shaded cast (R/B ratio = ${(rOverB * 100).toInt() / 100f})."
        }
        // 5. Neutral Balanced Ambient Scene:
        else {
            condition = "neutral_balanced"
            reason = "Balanced ambient scene (luminance: ${(avgLumNorm * 100).toInt()}%)."
        }

        return LightingAnalysis(
            averageLuminance = avgLumNorm,
            redToBlueRatio = rOverB,
            shadowFraction = shadowFraction,
            highlightFraction = highlightFraction,
            condition = condition,
            reason = reason
        )
    }

    /**
     * RULE MATCHER (Requirement 3):
     * Pulls the matching conditional rule parameters inferred from the 1-2 reference photos.
     * ZERO AI API calls are made here.
     */
    fun matchAndApplyRules(
        photoId: Long,
        lighting: LightingAnalysis,
        rules: List<ConditionalRule>,
        fallbackParams: PhotoEditParams
    ): PhotoEditParams {
        val matchedRule = rules.find { it.condition.equals(lighting.condition, ignoreCase = true) }
            ?: rules.find { it.condition.equals("neutral_balanced", ignoreCase = true) }

        return if (matchedRule != null) {
            PhotoEditParams(
                photoId = photoId,
                exposureShift = matchedRule.exposureShift,
                whiteBalanceShiftK = matchedRule.whiteBalanceShiftK,
                saturationShift = matchedRule.saturationShift,
                shadowsLift = matchedRule.shadowsLift,
                contrastShift = matchedRule.contrastShift,
                cropLeft = if (matchedRule.cropRight > matchedRule.cropLeft) matchedRule.cropLeft else fallbackParams.cropLeft,
                cropTop = if (matchedRule.cropBottom > matchedRule.cropTop) matchedRule.cropTop else fallbackParams.cropTop,
                cropRight = if (matchedRule.cropRight > matchedRule.cropLeft) matchedRule.cropRight else fallbackParams.cropRight,
                cropBottom = if (matchedRule.cropBottom > matchedRule.cropTop) matchedRule.cropBottom else fallbackParams.cropBottom,
                blurBackground = matchedRule.blurBackground,
                reasoning = "[Offline Rule: ${lighting.condition}] ${lighting.reason} Applied: ${matchedRule.reasoning}"
            )
        } else {
            fallbackParams.copy(
                photoId = photoId,
                reasoning = "[Offline Local Analysis: ${lighting.condition}] ${lighting.reason}"
            )
        }
    }

    fun apply(source: Bitmap, params: PhotoEditParams): Bitmap {
        var bmp = cropTo(source, params)
        bmp = applyColorAdjustments(bmp, params)
        if (params.blurBackground) {
            bmp = applyBackgroundBlur(bmp)
        }
        return bmp
    }

    private fun cropTo(bmp: Bitmap, params: PhotoEditParams): Bitmap {
        val left = (params.cropLeft * bmp.width).toInt().coerceIn(0, bmp.width - 1)
        val top = (params.cropTop * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        val right = (params.cropRight * bmp.width).toInt().coerceIn(left + 1, bmp.width)
        val bottom = (params.cropBottom * bmp.height).toInt().coerceIn(top + 1, bmp.height)
        return Bitmap.createBitmap(bmp, left, top, right - left, bottom - top)
    }

    private fun applyColorAdjustments(bmp: Bitmap, params: PhotoEditParams): Bitmap {
        val result = Bitmap.createBitmap(bmp.width, bmp.height, bmp.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Chain matrices: exposure/contrast (brightness scale) -> saturation -> white balance tint
        val cm = ColorMatrix()

        // Exposure: simple brightness multiply
        val exposureScale = 1f + params.exposureShift
        cm.postConcat(ColorMatrix(floatArrayOf(
            exposureScale, 0f, 0f, 0f, 0f,
            0f, exposureScale, 0f, 0f, 0f,
            0f, 0f, exposureScale, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))

        // Contrast
        val c = 1f + params.contrastShift
        val o = (1f - c) * 128f
        cm.postConcat(ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, o,
            0f, c, 0f, 0f, o,
            0f, 0f, c, 0f, o,
            0f, 0f, 0f, 1f, 0f
        )))

        // Saturation
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(1f + params.saturationShift)
        cm.postConcat(satMatrix)

        // White balance approximation: shift R/B channels opposite directions
        val wbShift = params.whiteBalanceShiftK / 2000f
        cm.postConcat(ColorMatrix(floatArrayOf(
            1f + wbShift, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f - wbShift, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bmp, 0f, 0f, paint)

        return result
    }

    private fun applyBackgroundBlur(bmp: Bitmap): Bitmap {
        return bmp // placeholder
    }
}
