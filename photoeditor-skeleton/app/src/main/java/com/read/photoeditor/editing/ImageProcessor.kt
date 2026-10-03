package com.read.photoeditor.editing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.read.photoeditor.data.model.PhotoEditParams

/**
 * Executes PhotoEditParams on an actual Bitmap. This is the "hands" — it never
 * decides WHAT to do (that's VLMClient's job), only HOW to apply it to pixels.
 *
 * Current implementation covers exposure, white balance (approximated), saturation,
 * contrast, and cropping using Android's built-in ColorMatrix — no external deps needed
 * for these. Blur, chromatic aberration, and lens correction are stubbed below with
 * notes on the recommended next step for each.
 */
object ImageProcessor {

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

        // Exposure: simple brightness multiply (a proper version would work in linear light)
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

        // White balance approximation: shift R/B channels opposite directions.
        // TODO: this is a crude tint, not a true Kelvin-based white balance.
        // For accurate results, convert to a working color space (e.g. LMS) and
        // apply a proper von Kries chromatic adaptation transform.
        val wbShift = params.whiteBalanceShiftK / 2000f // crude scale
        cm.postConcat(ColorMatrix(floatArrayOf(
            1f + wbShift, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f - wbShift, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bmp, 0f, 0f, paint)

        // TODO shadowsLift: ColorMatrix can't do tonal-range-selective adjustments
        // (lifting only shadows while leaving highlights alone). That needs a per-pixel
        // curve pass — either a LUT applied manually over the pixel buffer, or RenderScript/
        // RenderEffect equivalent. Worth doing once the basic pipeline is confirmed working.

        return result
    }

    private fun applyBackgroundBlur(bmp: Bitmap): Bitmap {
        // TODO: real subject/background separation needs a segmentation model.
        // Recommended: ML Kit's Selfie Segmentation (on-device, free, fast) to get a
        // subject mask, then RenderEffect.createBlurEffect() (API 31+) on the background
        // only, composited back with the sharp subject on top.
        return bmp // no-op placeholder for now
    }

    // TODO chromaticAberrationCorrection / lensCorrection:
    // Both are geometric/per-channel-offset problems, not color problems.
    // Recommended path: use OpenCV's Android bindings (org.opencv:opencv) —
    //   - lens correction: cv2.undistort() with a lens profile (can start with
    //     generic phone-sensor profiles, refine later)
    //   - chromatic aberration: estimate R/B channel misalignment near image edges
    //     and apply a small per-channel geometric warp to realign them
    // This is a good "phase 2" addition once the core color/crop pipeline is solid.
}
