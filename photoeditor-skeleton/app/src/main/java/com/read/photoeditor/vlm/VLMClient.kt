package com.read.photoeditor.vlm

import com.read.photoeditor.data.model.PhotoEditParams
import com.read.photoeditor.data.model.StyleDescription
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
 * This is "Path A" — the VLM acts as the editing brain in two stages:
 *   1) analyzeStyle(): look at your 1-2 reference before/afters, describe the SEMANTIC style
 *   2) planEdit(): given that style + one new photo, reason out concrete parameters for
 *      THAT photo specifically — this is what makes it "personalized", not a copy-paste preset
 *
 * ImageProcessor then executes whatever parameters come back. This class never touches pixels.
 *
 * IMPORTANT TODO before shipping/publishing this app to anyone else:
 * Do NOT ship your raw Anthropic API key inside the app binary — anyone could extract it.
 * For personal use on your own device this is fine to test with directly, but before
 * publishing, route these calls through a small backend you control (even a free-tier
 * Cloud Run/Render endpoint) that holds the key server-side.
 */
class VLMClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val endpoint = "https://api.anthropic.com/v1/messages"

    suspend fun analyzeStyle(
        beforeImageBase64: String,
        afterImageBase64: String
    ): StyleDescription = withContext(Dispatchers.IO) {
        val prompt = """
            You are a professional photo retoucher's assistant. Compare the BEFORE and AFTER
            image. Describe, in plain semantic terms (not pixel values), what editing style
            was applied: tone/white balance direction, shadow/highlight handling, saturation,
            contrast, cropping philosophy, and any other consistent "taste" signals a human
            editor would carry across an entire photo set. Be specific but describe INTENT,
            not literal numbers, since this style must be reapplied to very different photos.
        """.trimIndent()

        val responseText = callClaude(prompt, listOf(beforeImageBase64, afterImageBase64))

        StyleDescription(
            summary = responseText.take(400), // TODO: replace with a structured JSON response
            toneNotes = responseText,
            cropNotes = responseText,
            rawModelResponse = responseText
        )
    }

    suspend fun planEdit(
        photoId: Long,
        photoBase64: String,
        style: StyleDescription
    ): PhotoEditParams = withContext(Dispatchers.IO) {
        val prompt = """
            Target style (learned from the user's own reference edits):
            ${style.summary}

            Look at THIS specific photo's own lighting, exposure, and composition.
            Decide what adjustments THIS photo needs to reach the target style's look —
            not the same numbers as any other photo, but whatever gets THIS one there.

            Respond ONLY with JSON in this exact shape, no other text:
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

        val responseText = callClaude(prompt, listOf(photoBase64))
        parseParams(photoId, responseText)
    }

    /** Re-run planning for one photo with the user's correction folded into the prompt. */
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

        val responseText = callClaude(prompt, listOf(photoBase64))
        parseParams(photoId, responseText)
    }

    private fun callClaude(prompt: String, imagesBase64: List<String>): String {
        val contentArray = JSONArray()
        imagesBase64.forEach { b64 ->
            contentArray.put(
                JSONObject()
                    .put("type", "image")
                    .put(
                        "source", JSONObject()
                            .put("type", "base64")
                            .put("media_type", "image/jpeg")
                            .put("data", b64)
                    )
            )
        }
        contentArray.put(JSONObject().put("type", "text").put("text", prompt))

        val body = JSONObject()
            .put("model", "claude-sonnet-4-6")
            .put("max_tokens", 1024)
            .put(
                "messages", JSONArray().put(
                    JSONObject().put("role", "user").put("content", contentArray)
                )
            )

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val json = JSONObject(resp.body?.string() ?: "{}")
            val contentArr = json.optJSONArray("content") ?: return ""
            val texts = StringBuilder()
            for (i in 0 until contentArr.length()) {
                val block = contentArr.getJSONObject(i)
                if (block.optString("type") == "text") texts.append(block.optString("text"))
            }
            return texts.toString()
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
            // TODO: add a retry-with-stricter-prompt fallback instead of silently returning no-op params
            PhotoEditParams(photoId = photoId, reasoning = "parse failed: ${e.message}")
        }
    }
}
