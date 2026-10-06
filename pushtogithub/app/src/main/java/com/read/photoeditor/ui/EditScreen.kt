package com.read.photoeditor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.read.photoeditor.data.model.Photo
import com.read.photoeditor.data.model.PhotoEditParams
import com.read.photoeditor.editing.ImageProcessor
import com.read.photoeditor.ui.theme.*

/**
 * Fast, hardware-accelerated photo calibration editor.
 * Uses Compose ColorFilter with GPU shaders for 60/120 FPS slider dragging without UI thread CPU lag.
 * Includes a real computational photography Auto-Enhance engine that balances dynamic range and chromaticity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    photo: Photo,
    sourceBitmap: Bitmap,
    onBack: () -> Unit = {},
    onConfirmReference: (PhotoEditParams) -> Unit
) {
    var exposure by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var warmth by remember { mutableFloatStateOf(0f) } // maps to whiteBalanceShiftK (-1..1 -> -2000K..+2000K)
    var feedbackNote by remember { mutableStateOf<String?>(null) }
    var showOriginal by remember { mutableStateOf(false) }

    // Pre-analyze lighting once
    val lighting = remember(sourceBitmap) {
        ImageProcessor.classifyLighting(sourceBitmap)
    }

    // Hardware-accelerated GPU ColorMatrix for 60fps smooth preview
    val composeColorMatrix = remember(exposure, saturation, contrast, warmth, showOriginal) {
        if (showOriginal) {
            null
        } else {
            androidx.compose.ui.graphics.ColorMatrix(
                ImageProcessor.createColorMatrix(
                    exposureShift = exposure,
                    contrastShift = contrast,
                    saturationShift = saturation,
                    whiteBalanceShiftK = (warmth * 2000).toInt()
                ).array
            )
        }
    }

    val params = PhotoEditParams(
        photoId = photo.id,
        exposureShift = exposure,
        saturationShift = saturation,
        contrastShift = contrast,
        whiteBalanceShiftK = (warmth * 2000).toInt(),
        reasoning = feedbackNote ?: "Manual calibration"
    )

    fun applyPreset(name: String) {
        when (name) {
            "auto" -> {
                val autoResult = ImageProcessor.computeAutoEnhance(sourceBitmap)
                exposure = autoResult.exposureShift
                warmth = autoResult.whiteBalanceShiftK / 2000f
                saturation = autoResult.saturationShift
                contrast = autoResult.contrastShift
                feedbackNote = autoResult.summary
            }
            "golden" -> {
                exposure = 0.08f
                warmth = 0.20f
                saturation = 0.12f
                contrast = 0.08f
                feedbackNote = "Golden Hour: Warm sunlight glow with rich amber warmth"
            }
            "cinematic" -> {
                exposure = -0.04f
                warmth = -0.06f
                saturation = -0.08f
                contrast = 0.16f
                feedbackNote = "Cinematic: High contrast film curve with cool shadows"
            }
            "vibrant" -> {
                exposure = 0.05f
                warmth = 0.02f
                saturation = 0.18f
                contrast = 0.10f
                feedbackNote = "Vibrant: Crisp punchy colors for landscape and travel"
            }
            "moody" -> {
                exposure = -0.14f
                warmth = 0.08f
                saturation = -0.10f
                contrast = 0.20f
                feedbackNote = "Moody: Deep dramatic shadows and intimate tones"
            }
            "monochrome" -> {
                exposure = 0.04f
                warmth = 0f
                saturation = -1.0f
                contrast = 0.18f
                feedbackNote = "B&W Fine Art: Rich tonal monochrome conversion"
            }
            "reset" -> {
                exposure = 0f
                warmth = 0f
                saturation = 0f
                contrast = 0f
                feedbackNote = "Reset to original camera RAW baseline"
            }
        }
    }

    Scaffold(
        containerColor = Stone950,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Calibrate Look",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Stone100
                        )
                        Text(
                            text = "${photo.title ?: "Photo #${photo.id}"} • ${lighting.condition}",
                            fontSize = 11.sp,
                            color = Stone400
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Stone300
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { applyPreset("reset") }) {
                        Text("Reset", color = Stone400, fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Stone900)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            // Interactive Preview Card with Before/After toggle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Stone900)
                    .border(BorderStroke(1.dp, Stone800), RoundedCornerShape(16.dp))
            ) {
                Image(
                    bitmap = sourceBitmap.asImageBitmap(),
                    contentDescription = null,
                    colorFilter = composeColorMatrix?.let { ColorFilter.colorMatrix(it) },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                // Before / After Indicator Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Stone950.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, if (showOriginal) Amber500 else Stone800),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clickable { showOriginal = !showOriginal }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (showOriginal) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = if (showOriginal) Amber400 else Stone400,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (showOriginal) "Original" else "Tap for Original",
                            color = if (showOriginal) Amber300 else Stone300,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Presets Horizontal Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Auto Enhance Chip (Highlighted)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Amber500.copy(alpha = 0.20f),
                    border = BorderStroke(1.dp, Amber500.copy(alpha = 0.50f)),
                    modifier = Modifier.clickable { applyPreset("auto") }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Amber400, modifier = Modifier.size(14.dp))
                        Text("✨ Auto", color = Amber300, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                PresetChip(name = "Golden", icon = Icons.Default.WbSunny) { applyPreset("golden") }
                PresetChip(name = "Cinematic", icon = Icons.Default.Movie) { applyPreset("cinematic") }
                PresetChip(name = "Vibrant", icon = Icons.Default.Landscape) { applyPreset("vibrant") }
                PresetChip(name = "Moody", icon = Icons.Default.NightsStay) { applyPreset("moody") }
                PresetChip(name = "B&W", icon = Icons.Default.Contrast) { applyPreset("monochrome") }
            }

            // Status / Feedback Explanation Note
            feedbackNote?.let { note ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Stone900,
                    border = BorderStroke(1.dp, Amber500.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = Amber400, modifier = Modifier.size(15.dp))
                        Text(
                            text = note,
                            color = Stone300,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SLIDER 1: Exposure
            FineTuneSlider(
                title = "Exposure",
                icon = Icons.Default.Brightness6,
                value = exposure,
                valueText = "${if (exposure >= 0) "+" else ""}${String.format("%.2f", exposure)}",
                range = -1f..1f,
                step = 0.05f,
                onValueChange = { exposure = it; feedbackNote = null },
                onReset = { exposure = 0f }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // SLIDER 2: Warmth (White Balance)
            val wbK = (warmth * 2000).toInt()
            FineTuneSlider(
                title = "Warmth",
                icon = Icons.Default.DeviceThermostat,
                value = warmth,
                valueText = "${if (wbK >= 0) "+" else ""}${wbK}K",
                range = -1f..1f,
                step = 0.05f,
                onValueChange = { warmth = it; feedbackNote = null },
                onReset = { warmth = 0f }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // SLIDER 3: Saturation / Vibrancy
            FineTuneSlider(
                title = "Saturation",
                icon = Icons.Default.Palette,
                value = saturation,
                valueText = "${if (saturation >= 0) "+" else ""}${String.format("%.2f", saturation)}",
                range = -1f..1f,
                step = 0.05f,
                onValueChange = { saturation = it; feedbackNote = null },
                onReset = { saturation = 0f }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // SLIDER 4: Contrast
            FineTuneSlider(
                title = "Contrast",
                icon = Icons.Default.Contrast,
                value = contrast,
                valueText = "${if (contrast >= 0) "+" else ""}${String.format("%.2f", contrast)}",
                range = -1f..1f,
                step = 0.05f,
                onValueChange = { contrast = it; feedbackNote = null },
                onReset = { contrast = 0f }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button: Confirm & Apply
            Button(
                onClick = { onConfirmReference(params) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber500,
                    contentColor = Stone950
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = "This is the Look — Apply to Folder",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PresetChip(
    name: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Stone900,
        border = BorderStroke(1.dp, Stone800),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Stone400, modifier = Modifier.size(13.dp))
            Text(name, color = Stone200, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * High-precision slider with instant +/- fine-tuning buttons and tap-to-reset.
 */
@Composable
private fun FineTuneSlider(
    title: String,
    icon: ImageVector,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Stone900)
            .border(BorderStroke(1.dp, Stone800), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = null, tint = Amber400, modifier = Modifier.size(16.dp))
                Text(
                    text = title,
                    color = Stone100,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Value badge (tap to reset)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (value != 0f) Amber500.copy(alpha = 0.15f) else Stone950,
                border = BorderStroke(1.dp, if (value != 0f) Amber500.copy(alpha = 0.4f) else Stone800),
                modifier = Modifier.clickable { onReset() }
            ) {
                Text(
                    text = valueText,
                    color = if (value != 0f) Amber300 else Stone400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Decrement button
            IconButton(
                onClick = { onValueChange((value - step).coerceIn(range.start, range.endInclusive)) },
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Stone800)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Stone300, modifier = Modifier.size(14.dp))
            }

            // Slider
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = Amber500,
                    activeTrackColor = Amber500,
                    inactiveTrackColor = Stone800
                ),
                modifier = Modifier.weight(1f)
            )

            // Increment button
            IconButton(
                onClick = { onValueChange((value + step).coerceIn(range.start, range.endInclusive)) },
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Stone800)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase", tint = Stone300, modifier = Modifier.size(14.dp))
            }
        }
    }
}
