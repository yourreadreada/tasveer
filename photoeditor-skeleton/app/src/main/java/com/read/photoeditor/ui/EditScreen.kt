package com.read.photoeditor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.read.photoeditor.data.model.Photo
import com.read.photoeditor.data.model.PhotoEditParams
import com.read.photoeditor.editing.ImageProcessor
import android.graphics.Bitmap

/**
 * Where the user manually defines the "reference edit" on 1-2 photos — this
 * becomes the ground truth that VLMClient.analyzeStyle() turns into a style
 * description, which then gets reasoned against every other photo in the trip.
 *
 * Sliders here are a placeholder UI for the MVP. Crop is shown as a simple
 * inset for now — for a real crop interaction (drag handles), consider a
 * library like Android's own ImageDecoder + a custom gesture-based crop view,
 * or 'canhub/Android-Image-Cropper' if a third-party lib is acceptable.
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

    Column(modifier = Modifier.padding(16.dp)) {
        Image(
            bitmap = preview.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(320.dp)
        )

        Text("Exposure")
        Slider(value = exposure, onValueChange = { exposure = it }, valueRange = -1f..1f)

        Text("Warmth")
        Slider(value = warmth, onValueChange = { warmth = it }, valueRange = -1f..1f)

        Text("Saturation")
        Slider(value = saturation, onValueChange = { saturation = it }, valueRange = -1f..1f)

        Text("Contrast")
        Slider(value = contrast, onValueChange = { contrast = it }, valueRange = -1f..1f)

        // TODO: crop handles UI, blur/CA/lens-correction toggles once those are built out

        Button(
            onClick = { onConfirmReference(params) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("This is the look — apply across the trip")
        }
    }
}
