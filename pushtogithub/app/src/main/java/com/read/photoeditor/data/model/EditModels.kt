package com.read.photoeditor.data.model

/**
 * A photo pulled from MediaStore. `takenAt` and `latLng` drive trip clustering.
 */
data class Photo(
    val id: Long,
    val uri: String,
    val takenAtMillis: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isFrontCamera: Boolean = false, // best-effort guess, see PhotoRepository
    val isScreenshot: Boolean = false,
    val folderName: String = "",
    val title: String? = null
)

/** A cluster of photos the app has grouped into one "trip" or "folder album". */
data class Trip(
    val id: String,
    val photos: List<Photo>,
    val startMillis: Long,
    val endMillis: Long,
    val locationName: String? = null,
    val title: String? = null
)

/**
 * The SEMANTIC style extracted from your 1-2 reference edits — not raw numbers,
 * but a description a model can reason from for any new, different photo.
 * This is the output of VLMClient.analyzeStyle().
 */
data class StyleDescription(
    val summary: String,           // e.g. "Warm tones, lifted shadows, tight subject-focused crops, slight desaturation in greens, subtle vignette"
    val toneNotes: String,
    val cropNotes: String,
    val rawModelResponse: String   // keep the full response for later model training (Path B)
)

/**
 * Concrete, executable parameters for ONE photo, produced by reasoning about
 * that photo's own content against the StyleDescription.
 * This is what ImageProcessor actually applies to pixels.
 */
data class PhotoEditParams(
    val photoId: Long,
    val exposureShift: Float = 0f,      // -1.0 .. 1.0
    val whiteBalanceShiftK: Int = 0,    // kelvin shift, e.g. +300 = warmer
    val saturationShift: Float = 0f,    // -1.0 .. 1.0
    val shadowsLift: Float = 0f,        // 0 .. 1.0
    val contrastShift: Float = 0f,      // -1.0 .. 1.0
    val cropLeft: Float = 0f,           // normalized 0..1 crop box
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val blurBackground: Boolean = false,
    val reasoning: String = ""          // model's own explanation — useful for debugging + training data
)

/**
 * One row of the training dataset we're silently building (Path A -> Path B bridge).
 * Logged every time a photo is edited, including any manual correction round.
 */
data class EditLogEntry(
    val id: Long = 0,
    val photoId: Long,
    val styleSummary: String,
    val initialParams: String,      // JSON-serialized PhotoEditParams (first AI attempt)
    val finalParams: String,        // JSON-serialized PhotoEditParams (after any correction)
    val wasCorrected: Boolean,
    val correctionNote: String? = null, // what you told it was wrong, if corrected
    val timestampMillis: Long
)
