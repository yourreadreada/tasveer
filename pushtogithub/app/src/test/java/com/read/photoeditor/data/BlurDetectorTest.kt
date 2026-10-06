package com.read.photoeditor.data

import org.junit.Assert.*
import org.junit.Test

class BlurDetectorTest {

    @Test
    fun `computeLaplacianVariance on solid uniform color returns zero`() {
        val width = 10
        val height = 10
        val uniformGray = IntArray(width * height) { 128 }

        val variance = BlurDetector.computeLaplacianVariance(uniformGray, width, height)
        assertEquals(0.0, variance, 0.001)
    }

    @Test
    fun `computeLaplacianVariance on smooth gradient yields low blur score`() {
        val width = 20
        val height = 20
        // Very slow, smooth gradient (low high-frequency details)
        val smoothGray = IntArray(width * height) { idx ->
            val x = idx % width
            (x * 5)
        }

        val variance = BlurDetector.computeLaplacianVariance(smoothGray, width, height)
        assertTrue("Smooth gradient should yield variance under default threshold (110.0), was $variance", variance < BlurDetector.DEFAULT_BLUR_THRESHOLD)
    }

    @Test
    fun `computeLaplacianVariance on high contrast checkerboard yields high sharpness score`() {
        val width = 20
        val height = 20
        // Alternating black and white pixels (extreme high frequencies)
        val checkerboard = IntArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            if ((x + y) % 2 == 0) 255 else 0
        }

        val variance = BlurDetector.computeLaplacianVariance(checkerboard, width, height)
        assertTrue("Checkerboard pattern should yield very high variance (> 110.0), was $variance", variance > BlurDetector.DEFAULT_BLUR_THRESHOLD)
    }

    @Test
    fun `computeLaplacianVariance handles small invalid dimensions safely`() {
        val small = IntArray(4) { 100 }
        val variance = BlurDetector.computeLaplacianVariance(small, 2, 2)
        assertEquals(0.0, variance, 0.001)
    }
}
