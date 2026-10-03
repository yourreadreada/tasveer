package com.read.photoeditor.vlm

import com.read.photoeditor.data.model.PhotoEditParams
import com.read.photoeditor.data.model.StyleDescription
import com.read.photoeditor.editing.ConditionalRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * VLMClient handles calls to the vision model (Google Gemini).
 *
 * ARCHITECTURAL DESIGN:
 * 1. analyzeStyle() is called ONCE per calibration (on 1-2 reference photos).
 *    It returns BOTH a semantic style description and structured CONDITIONAL RULES.
 * 2. Bulk trip photo editing uses local, offline on-device classification (ImageProcessor.classifyLighting)
 *    and matches those rules — ZERO API calls for the bulk trip photos!
 * 3. replanWithCorrection() is called ONLY when the user flags a specific photo as wrong.
 */
class VLMClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Google Gemini API endpoint
    private val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    /**
     * Requirement 1:
     * One model call per calibration that returns BOTH:
     *   a) A semantic style description
     *   b) A set of structured CONDITIONAL RULES for local on-device application
     */
    suspend fun analyzeStyle(
        beforeImageBase64: String,
        afterImageBase64: String
    ): StyleDescription = withContext(Dispatchers.IO) {
        val prompt = """
            You are a professional photo retoucher's assistant. Compare the BEFORE and AFTER
            reference image. We need TWO things in ONE call:
            1. Describe, in plain semantic terms (not pixel values), what editing style
               was applied: tone/white balance direction, shadow/highlight handling, saturation,
               contrast, and cropping philosophy.
            2. Infer a set of structured CONDITIONAL RULES to adapt this style across different
               lighting conditions (so remaining trip photos can be edited locally without calling the API):
               - "underexposed_or_backlit" (lift shadows and increase exposure)
               - "indoor_warm_light" (pull saturation/Kelvin back to prevent orange skin)
               - "overexposed_or_bright_daylight" (protect highlights and keep contrast)
               - "cool_overcast" (counter cool cloudy daylight)
               - "neutral_balanced" (baseline calibration parameters)

            Respond ONLY with JSON in this exact structure:
            {
              "summary": "1-2 concise sentences summarizing the look",
              "toneNotes": "Description of tone curve and color temperature",
              "cropNotes": "Guidance on framing and subject emphasis",
              "rules": [
                {
                  "condition": "underexposed_or_backlit",
                  "exposureShift": float -1..1,
                  "whiteBalanceShiftK": int,
                  "saturationShift": float -1..1,
                  "shadowsLift": float 0..1,
                  "contrastShift": float -1..1,
                  "reasoning": "one sentence on why"
                },
                {
                  "condition": "indoor_warm_light",
                  "exposureShift": float -1..1,
                  "whiteBalanceShiftK": int,
                  "saturationShift": float -1..1,
                  "shadowsLift": float 0..1,
                  "contrastShift": float -1..1,
                  "reasoning": "one sentence on why"
                },
                {
                  "condition": "overexposed_or_bright_daylight",
                  "exposureShift": float -1..1,
                  "whiteBalanceShiftK": int,
                  "saturationShift": float -1..1,
                  "shadowsLift": float 0..1,
                  "contrastShift": float -1..1,
                  "reasoning": "one sentence on why"
                },
                {
                  "condition": "cool_overcast",
                  "exposureShift": float -1..1,
                  "whiteBalanceShiftK": int,
                  "saturationShift": float -1..1,
                  "shadowsLift": float 0..1,
                  "contrastShift": float -1..1,
                  "reasoning": "one sentence on why"
                },
                {
                  "condition": "neutral_balanced",
                  "exposureShift": float -1..1,
                  "whiteBalanceShiftK": int,
                  "saturationShift": float -1..1,
                  "shadowsLift": float 0..1,
                  "contrastShift": float -1..1,
                  "reasoning": "one sentence on why"
                }
              ]
            }
        """.trimIndent()

        val responseText = callGemini(prompt, listOf(beforeImageBase64, afterImageBase64))

        val summary = try {
            val j = JSONObject(responseText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```"))
            j.optString("summary", responseText.take(400))
        } catch (e: Exception) {
            responseText.take(400)
        }

        StyleDescription(
            summary = summary,
            toneNotes = responseText,
            cropNotes = responseText,
            rawModelResponse = responseText
        )
    }

    /**
     * Parses the conditional rules inferred during calibration from StyleDescription.rawModelResponse.
     */
    fun parseConditionalRules(style: StyleDescription): List<ConditionalRule> {
        return try {
            val cleaned = style.rawModelResponse.trim().removePrefix("```json").removePrefix("```").removeSuffix("```")
            val json = JSONObject(cleaned)
            val rulesArray = json.optJSONArray("rules") ?: return emptyList()
            val list = mutableListOf<ConditionalRule>()
            for (i in 0 until rulesArray.length()) {
                val r = rulesArray.getJSONObject(i)
                list.add(
                    ConditionalRule(
                        condition = r.optString("condition", "neutral_balanced"),
                        exposureShift = r.optDouble("exposureShift", 0.0).toFloat(),
                        whiteBalanceShiftK = r.optInt("whiteBalanceShiftK", 0),
                        saturationShift = r.optDouble("saturationShift", 0.0).toFloat(),
                        shadowsLift = r.optDouble("shadowsLift", 0.0).toFloat(),
                        contrastShift = r.optDouble("contrastShift", 0.0).toFloat(),
                        reasoning = r.optString("reasoning", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Individual planEdit call (optional fallback / single-photo inspector).
     */
    suspend fun planEdit(
        photoId: Long,
        photoBase64: String,
        style: StyleDescription
    ): PhotoEditParams = withContext(Dispatchers.IO) {
        val prompt = """
            Target style: ${style.summary}
            Look at this specific photo and decide what adjustments it needs.
            Respond ONLY with JSON:
            {
              "exposureShift": float -1..1,
              "whiteBalanceShiftK": int,
              "saturationShift": float -1..1,
              "shadowsLift": float 0..1,
              "contrastShift": float -1..1,
              "cropLeft": float 0..1, "cropTop": float 0..1,
              "cropRight": float 0..1, "cropBottom": float 0..1,
              "blurBackground": boolean,
              "reasoning": "one sentence on why"
            }
        """.trimIndent()

        val responseText = callGemini(prompt, listOf(photoBase64))
        parseParams(photoId, responseText)
    }

    /**
     * Requirement 4:
     * Interactive correction loop — ONLY called when the user flags a specific photo as wrong.
     */
    suspend fun replanWithCorrection(
        photoId: Long,
        photoBase64: String,
        style: StyleDescription,
        previousParams: PhotoEditParams,
        correctionNote: String
    ): PhotoEditParams = withContext(Dispatchers.IO) {
        val prompt = """
            Target style: ${style.summary}
            Your previous attempt on this photo: $previousParams
            The user says this was wrong: "$correctionNote"
            Produce corrected JSON parameters in the same shape as before, fixing that issue
            specifically while still honoring the target style.
        """.trimIndent()

        val responseText = callGemini(prompt, listOf(photoBase64))
        parseParams(photoId, responseText)
    }

    private fun callGemini(prompt: String, imagesBase64: List<String>): String {
        val partsArray = JSONArray()
        imagesBase64.forEach { b64 ->
            partsArray.put(
                JSONObject().put(
                    "inline_data", JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", b64)
                )
            )
        }
        partsArray.put(JSONObject().put("text", prompt))

        val body = JSONObject().put(
            "contents", JSONArray().put(
                JSONObject().put("parts", partsArray)
            )
        )

        val request = Request.Builder()
            .url("$endpoint?key=$apiKey")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val json = JSONObject(resp.body?.string() ?: "{}")
            val candidates = json.optJSONArray("candidates") ?: return ""
            if (candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts") ?: return ""
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    sb.append(parts.getJSONObject(i).optString("text"))
                }
                return sb.toString()
            }
            return ""
        }
    }

    private fun parseParams(photoId: Long, responseText: String): PhotoEditParams {
        return try {
            val cleaned = responseText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```")
            val j = JSONObject(cleaned)
            PhotoEditParams(
                photoId = photoId,
                exposureShift = j.optDouble("exposureShift", 0.0).toFloat(),
                whiteBalanceShiftK = j.optInt("whiteBalanceShiftK", 0),
                saturationShift = j.optDouble("saturationShift", 0.0).toFloat(),
                shadowsLift = j.optDouble("shadowsLift", 0.0).toFloat(),
                contrastShift = j.optDouble("contrastShift", 0.0).toFloat(),
                cropLeft = j.optDouble("cropLeft", 0.0).toFloat(),
                cropTop = j.optDouble("cropTop", 0.0).toFloat(),
                cropRight = j.optDouble("cropRight", 1.0).toFloat(),
                cropBottom = j.optDouble("cropBottom", 1.0).toFloat(),
                blurBackground = j.optBoolean("blurBackground", false),
                reasoning = j.optString("reasoning", "")
            )
        } catch (e: Exception) {
            PhotoEditParams(photoId = photoId, reasoning = "parse failed: ${e.message}")
        }
    }
}
