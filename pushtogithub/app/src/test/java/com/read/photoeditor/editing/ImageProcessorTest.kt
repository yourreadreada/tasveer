package com.read.photoeditor.editing

import com.read.photoeditor.data.model.PhotoEditParams
import org.junit.Assert.*
import org.junit.Test

class ImageProcessorTest {

    private fun packRgb(r: Int, g: Int, b: Int): Int {
        return (0xff shl 24) or ((r and 0xff) shl 16) or ((g and 0xff) shl 8) or (b and 0xff)
    }

    @Test
    fun `analyzePixels on pure dark image classifies as underexposed_or_backlit`() {
        val darkPixels = IntArray(100) { packRgb(20, 20, 20) }
        val analysis = ImageProcessor.analyzePixels(darkPixels)

        assertEquals("underexposed_or_backlit", analysis.condition)
        assertTrue(analysis.averageLuminance < 0.36f)
    }

    @Test
    fun `analyzePixels on bright daylight image classifies as overexposed_or_bright_daylight`() {
        val brightPixels = IntArray(100) { packRgb(240, 240, 240) }
        val analysis = ImageProcessor.analyzePixels(brightPixels)

        assertEquals("overexposed_or_bright_daylight", analysis.condition)
        assertTrue(analysis.averageLuminance > 0.68f)
    }

    @Test
    fun `analyzePixels on warm incandescent light classifies as indoor_warm_light`() {
        // High Red, Medium Green, Low Blue -> Warm Tungsten
        val warmPixels = IntArray(100) { packRgb(160, 110, 80) }
        val analysis = ImageProcessor.analyzePixels(warmPixels)

        assertEquals("indoor_warm_light", analysis.condition)
        assertTrue(analysis.redToBlueRatio > 1.20f)
    }

    @Test
    fun `analyzePixels on cool overcast sky classifies as cool_overcast`() {
        // Low Red, Medium Green, High Blue -> Cool Overcast
        val coolPixels = IntArray(100) { packRgb(90, 110, 150) }
        val analysis = ImageProcessor.analyzePixels(coolPixels)

        assertEquals("cool_overcast", analysis.condition)
        assertTrue(analysis.redToBlueRatio < 0.88f)
    }

    @Test
    fun `analyzePixels on balanced neutral scene classifies as neutral_balanced`() {
        val neutralPixels = IntArray(100) { packRgb(128, 128, 128) }
        val analysis = ImageProcessor.analyzePixels(neutralPixels)

        assertEquals("neutral_balanced", analysis.condition)
    }

    @Test
    fun `matchAndApplyRules correctly selects corresponding conditional rule`() {
        val analysis = LightingAnalysis(
            averageLuminance = 0.20f,
            redToBlueRatio = 1.0f,
            shadowFraction = 0.50f,
            highlightFraction = 0.05f,
            condition = "underexposed_or_backlit",
            reason = "Test dark scene"
        )

        val rules = listOf(
            ConditionalRule(condition = "underexposed_or_backlit", exposureShift = 0.25f, shadowsLift = 0.30f),
            ConditionalRule(condition = "neutral_balanced", exposureShift = 0.05f)
        )

        val fallback = PhotoEditParams(photoId = 42L)
        val result = ImageProcessor.matchAndApplyRules(42L, analysis, rules, fallback)

        assertEquals(0.25f, result.exposureShift, 0.001f)
        assertEquals(0.30f, result.shadowsLift, 0.001f)
        assertTrue(result.reasoning.contains("underexposed_or_backlit"))
    }

    @Test
    fun `computeAutoEnhanceFromPixels prevents highlight clipping`() {
        // Scene with very bright highlights (p98 close to 255)
        val pixels = IntArray(1000) { idx ->
            if (idx > 950) packRgb(250, 250, 250) else packRgb(100, 100, 100)
        }

        val result = ImageProcessor.computeAutoEnhanceFromPixels(pixels)

        // Highlight headroom ceiling should strictly limit positive exposure gain
        assertTrue("Exposure must not be aggressively boosted when highlights are near 255", result.exposureShift <= 0.10f)
    }

    @Test
    fun `computeAutoEnhanceFromPixels boosts contrast on flat washed out scenes`() {
        // Flat scene with low dynamic range (all pixels between 110 and 130)
        val flatPixels = IntArray(1000) { idx -> packRgb(115 + (idx % 10), 115 + (idx % 10), 115 + (idx % 10)) }
        val result = ImageProcessor.computeAutoEnhanceFromPixels(flatPixels)

        assertTrue("Flat low-contrast scene should receive positive contrast expansion", result.contrastShift > 0.08f)
    }
}
