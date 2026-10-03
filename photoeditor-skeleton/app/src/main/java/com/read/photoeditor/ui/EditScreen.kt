package com.read.photoeditor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.read.photoeditor.data.model.Photo
import com.read.photoeditor.data.model.PhotoEditParams
import com.read.photoeditor.editing.ImageProcessor

/**
 * Where the user manually defines the "reference edit" on 1-2 photos.
 * Features ADAPTIVE starting point filters that dynamically adjust sliders
 * based on the photo's actual lighting condition (via ImageProcessor.classifyLighting).
 */
@Composable
fun EditScreen(
    photo: Photo,
    sourceBitmap: Bitmap,
    onConfirmReference: (PhotoEditParams) -> Unit
) {
    var exposure by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var warmth by remember { mutableFloatStateOf(0f) } // maps to whiteBalanceShiftK
    var feedbackNote by remember { mutableStateOf<String?>(null) }

    // Offline on-device lighting analysis for adaptive presets
    val lighting = remember(sourceBitmap) {
        ImageProcessor.classifyLighting(sourceBitmap)
    }

    val params = PhotoEditParams(
        photoId = photo.id,
        exposureShift = exposure,
        saturationShift = saturation,
        contrastShift = contrast,
        whiteBalanceShiftK = (warmth * 2000).toInt()
    )

    val preview = remember(exposure, saturation, contrast, warmth) {
        ImageProcessor.apply(sourceBitmap, params)
    }

    fun applyAdaptivePreset(name: String) {
        val luma = lighting.averageLuminance
        val rOverB = lighting.redToBlueRatio

        when (name) {
            "auto" -> {
                exposure = if (luma < 0.36f) 0.22f else if (luma > 0.68f) -0.12f else 0.06f
                warmth = if (rOverB > 1.25f) -0.12f else if (rOverB < 0.88f) 0.15f else 0.04f
                saturation = 0.08f
                contrast = 0.08f
                feedbackNote = "Auto-balanced based on ${lighting.condition}"
            }
            "golden" -> {
                exposure = if (luma > 0.65f) -0.04f else 0.12f
                warmth = if (rOverB > 1.22f) 0.12f else 0.28f
                saturation = if (rOverB > 1.22f) 0.06f else 0.15f
                contrast = 0.10f
                feedbackNote = "Golden hour warmth adapted to ambient tint"
            }
            "cinematic" -> {
                exposure = if (luma < 0.38f) 0.06f else -0.08f
                warmth = if (rOverB < 0.90f) -0.05f else -0.14f
                saturation = -0.08f
                contrast = 0.18f
                feedbackNote = "Filmic contrast without crushing dark shadows"
            }
            "airy" -> {
                exposure = if (luma > 0.65f) 0.06f else 0.24f
                warmth = if (rOverB > 1.20f) -0.08f else 0.04f
                saturation = 0.06f
                contrast = -0.06f
                feedbackNote = "High-key presentation protecting highlight detail"
            }
            "reset" -> {
                exposure = 0f
                saturation = 0f
                contrast = 0f
                warmth = 0f
                feedbackNote = "Reset to zero baseline"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Image(
            bitmap = preview.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(280.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Adaptive Starting Points Row
        Text(
            text = "Adaptive Starting Points (${lighting.condition}):",
            style = MaterialTheme.typography.labelSmall
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(onClick = { applyAdaptivePreset("auto") }) { Text("Auto", style = MaterialTheme.typography.labelSmall) }
            Button(onClick = { applyAdaptivePreset("golden") }) { Text("Golden", style = MaterialTheme.typography.labelSmall) }
            Button(onClick = { applyAdaptivePreset("cinematic") }) { Text("Cinematic", style = MaterialTheme.typography.labelSmall) }
            Button(onClick = { applyAdaptivePreset("airy") }) { Text("Airy", style = MaterialTheme.typography.labelSmall) }
            OutlinedButton(onClick = { applyAdaptivePreset("reset") }) { Text("Reset", style = MaterialTheme.typography.labelSmall) }
        }

        feedbackNote?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text("Exposure (${String.format("%.2f", exposure)})")
        Slider(value = exposure, onValueChange = { exposure = it; feedbackNote = null }, valueRange = -1f..1f)

        Text("Warmth (${(warmth * 2000).toInt()}K)")
        Slider(value = warmth, onValueChange = { warmth = it; feedbackNote = null }, valueRange = -1f..1f)

        Text("Saturation (${String.format("%.2f", saturation)})")
        Slider(value = saturation, onValueChange = { saturation = it; feedbackNote = null }, valueRange = -1f..1f)

        Text("Contrast (${String.format("%.2f", contrast)})")
        Slider(value = contrast, onValueChange = { contrast = it; feedbackNote = null }, valueRange = -1f..1f)

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onConfirmReference(params) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("This is the look — apply across the trip")
        }
    }
}
