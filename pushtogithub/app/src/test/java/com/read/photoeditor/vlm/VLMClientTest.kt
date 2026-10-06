package com.read.photoeditor.vlm

import com.read.photoeditor.data.model.PhotoEditParams
import com.read.photoeditor.data.model.StyleDescription
import org.junit.Assert.*
import org.junit.Test

class VLMClientTest {

    private val client = VLMClient(apiKey = "")

    @Test
    fun `parseParams successfully parses valid JSON response`() {
        val json = """
            {
              "exposureShift": 0.25,
              "whiteBalanceShiftK": 350,
              "saturationShift": 0.10,
              "shadowsLift": 0.20,
              "contrastShift": 0.05,
              "cropLeft": 0.05,
              "cropTop": 0.05,
              "cropRight": 0.95,
              "cropBottom": 0.95,
              "blurBackground": false,
              "reasoning": "Golden hour lift"
            }
        """.trimIndent()

        val params = client.parseParams(photoId = 101L, responseText = json)

        assertEquals(101L, params.photoId)
        assertEquals(0.25f, params.exposureShift, 0.001f)
        assertEquals(350, params.whiteBalanceShiftK)
        assertEquals(0.10f, params.saturationShift, 0.001f)
        assertEquals(0.20f, params.shadowsLift, 0.001f)
        assertEquals(0.05f, params.contrastShift, 0.001f)
        assertEquals("Golden hour lift", params.reasoning)
    }

    @Test
    fun `parseParams handles markdown fenced code blocks cleanly`() {
        val markdownJson = """
            ```json
            {
              "exposureShift": -0.15,
              "whiteBalanceShiftK": -200,
              "saturationShift": 0.05,
              "reasoning": "Cooled tungsten"
            }
            ```
        """.trimIndent()

        val params = client.parseParams(photoId = 102L, responseText = markdownJson)

        assertEquals(-0.15f, params.exposureShift, 0.001f)
        assertEquals(-200, params.whiteBalanceShiftK)
        assertEquals("Cooled tungsten", params.reasoning)
    }

    @Test
    fun `parseParams returns fallback when response is malformed`() {
        val badJson = "Internal Server Error 500"
        val params = client.parseParams(photoId = 103L, responseText = badJson)

        assertTrue(params.reasoning.startsWith("parse failed"))
    }

    @Test
    fun `parseConditionalRules parses full five-tier rule set`() {
        val rulesJson = """
            {
              "summary": "Warm natural tones",
              "rules": [
                { "condition": "underexposed_or_backlit", "exposureShift": 0.25, "whiteBalanceShiftK": 200, "saturationShift": -0.05, "shadowsLift": 0.35, "contrastShift": 0.05, "reasoning": "Lift dark shadows" },
                { "condition": "indoor_warm_light", "exposureShift": 0.05, "whiteBalanceShiftK": -300, "saturationShift": -0.05, "shadowsLift": 0.1, "contrastShift": 0.1, "reasoning": "Counter tungsten" },
                { "condition": "overexposed_or_bright_daylight", "exposureShift": -0.15, "whiteBalanceShiftK": 100, "saturationShift": 0.05, "shadowsLift": 0.1, "contrastShift": 0.15, "reasoning": "Protect highlights" },
                { "condition": "cool_overcast", "exposureShift": 0.15, "whiteBalanceShiftK": 350, "saturationShift": 0.1, "shadowsLift": 0.15, "contrastShift": 0.05, "reasoning": "Warm overcast" },
                { "condition": "neutral_balanced", "exposureShift": 0.08, "whiteBalanceShiftK": 150, "saturationShift": 0.08, "shadowsLift": 0.1, "contrastShift": 0.08, "reasoning": "Baseline calibration" }
              ]
            }
        """.trimIndent()

        val style = StyleDescription(
            summary = "Warm natural tones",
            toneNotes = "Natural tones",
            cropNotes = "Centered",
            rawModelResponse = rulesJson
        )

        val rules = client.parseConditionalRules(style)
        assertEquals(5, rules.size)
        assertEquals("underexposed_or_backlit", rules[0].condition)
        assertEquals(0.25f, rules[0].exposureShift, 0.001f)
        assertEquals(200, rules[0].whiteBalanceShiftK)
        assertEquals("indoor_warm_light", rules[1].condition)
        assertEquals(-300, rules[1].whiteBalanceShiftK)
    }

    @Test
    fun `applyHeuristicCorrection responds appropriately to dark flag`() {
        val initial = PhotoEditParams(photoId = 55L, exposureShift = 0.0f)
        val corrected = client.applyHeuristicCorrection(55L, initial, "Too dark, please brighten up")

        assertTrue("Exposure should increase when user notes dark", corrected.exposureShift > initial.exposureShift)
        assertTrue(corrected.reasoning.contains("brightened exposure"))
    }

    @Test
    fun `applyHeuristicCorrection responds appropriately to warm flag`() {
        val initial = PhotoEditParams(photoId = 56L, whiteBalanceShiftK = 0)
        val corrected = client.applyHeuristicCorrection(56L, initial, "Way too warm and orange")

        assertTrue("White balance should cool down when user notes too warm", corrected.whiteBalanceShiftK < initial.whiteBalanceShiftK)
        assertTrue(corrected.reasoning.contains("cooled white balance"))
    }

    @Test
    fun `applyHeuristicCorrection responds appropriately to saturated flag`() {
        val initial = PhotoEditParams(photoId = 57L, saturationShift = 0.1f)
        val corrected = client.applyHeuristicCorrection(57L, initial, "Too saturated colors look fake")

        assertTrue("Saturation should decrease when user notes too saturated", corrected.saturationShift < initial.saturationShift)
        assertTrue(corrected.reasoning.contains("decreased saturation"))
    }
}
