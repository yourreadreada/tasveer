package com.read.photoeditor

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.read.photoeditor.data.EditLogDatabase
import com.read.photoeditor.data.EditLogRepository
import com.read.photoeditor.data.PhotoRepository
import com.read.photoeditor.data.TripClusterer
import com.read.photoeditor.data.model.EditLogEntry
import com.read.photoeditor.data.model.Photo
import com.read.photoeditor.data.model.PhotoEditParams
import com.read.photoeditor.data.model.StyleDescription
import com.read.photoeditor.data.model.Trip
import com.read.photoeditor.editing.ImageProcessor
import com.read.photoeditor.ui.EditScreen
import com.read.photoeditor.ui.GalleryScreen
import com.read.photoeditor.ui.TripScreen
import com.read.photoeditor.vlm.VLMClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Represents an edited photo in the trip review list.
 */
data class EditedPhotoItem(
    val photo: Photo,
    val initialBitmap: Bitmap,
    var editedBitmap: Bitmap,
    var params: PhotoEditParams,
    var wasCorrected: Boolean = false,
    var correctionNote: String? = null,
    var isReevaluating: Boolean = false
)

/**
 * App entry point. Handles runtime permissions, clustering, and the full
 * preview-first AI photo editing workflow:
 * Gallery -> Trip -> Edit (Reference) -> [Orchestrated Trip Apply] -> Review & Correct
 */
class MainActivity : ComponentActivity() {

    private lateinit var photoRepository: PhotoRepository
    private lateinit var editLogRepository: EditLogRepository
    private lateinit var vlmClient: VLMClient

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadAndShowPhotos()
    }

    private var tripsState: MutableState<List<Trip>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        photoRepository = PhotoRepository(this)

        val db = Room.databaseBuilder(this, EditLogDatabase::class.java, "edit_log.db").build()
        editLogRepository = EditLogRepository(db.editLogDao())

        // Gemini-powered VLM client
        vlmClient = VLMClient(apiKey = "YOUR_GEMINI_API_KEY")

        setContent {
            val trips = remember { mutableStateOf<List<Trip>>(emptyList()) }
            tripsState = trips
            val navController = rememberNavController()

            Surface {
                AppNavHost(
                    navController = navController,
                    trips = trips.value,
                    vlmClient = vlmClient,
                    editLogRepository = editLogRepository
                )
            }
        }

        val permission = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        requestPermission.launch(permission)
    }

    private fun loadAndShowPhotos() {
        val photos = photoRepository.loadAllPhotos()
        val trips = TripClusterer().cluster(photos)
        tripsState?.value = trips
    }
}

/**
 * Navigation graph managing the four app stages:
 * 1. "gallery" -> Cluster of trips
 * 2. "trip"    -> Grid of photos in selected trip to pick 1-2 reference photos
 * 3. "edit"    -> Reference photo calibration and one-call AI style extraction
 * 4. "review"  -> Review of all edited trip photos with per-photo correction loop
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    trips: List<Trip>,
    vlmClient: VLMClient,
    editLogRepository: EditLogRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Navigation and flow state
    var selectedTrip by remember { mutableStateOf<Trip?>(null) }
    var selectedReferences by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var currentStyleDescription by remember { mutableStateOf<StyleDescription?>(null) }
    val editedPhotoItems = remember { mutableStateListOf<EditedPhotoItem>() }

    var isProcessingBatch by remember { mutableStateOf(false) }
    var batchProgressMessage by remember { mutableStateOf("") }

    NavHost(navController = navController, startDestination = "gallery") {
        // SCREEN 1: GALLERY
        composable("gallery") {
            GalleryScreen(trips = trips) { trip ->
                selectedTrip = trip
                navController.navigate("trip")
            }
        }

        // SCREEN 2: TRIP SCREEN (PICK 1-2 REFERENCE PHOTOS)
        composable("trip") {
            val trip = selectedTrip
            if (trip != null) {
                TripScreen(trip = trip) { references ->
                    selectedReferences = references
                    navController.navigate("edit")
                }
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }

        // SCREEN 3: EDIT REFERENCE & APPLY TO TRIP
        composable("edit") {
            val trip = selectedTrip
            val referencePhoto = selectedReferences.firstOrNull() ?: trip?.photos?.firstOrNull()

            if (trip != null && referencePhoto != null) {
                val refBitmap = remember(referencePhoto.id) {
                    loadBitmap(context, referencePhoto.uri)
                }

                if (isProcessingBatch) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = batchProgressMessage,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    EditScreen(
                        photo = referencePhoto,
                        sourceBitmap = refBitmap,
                        onConfirmReference = { referenceParams ->
                            // FIX 1 ORCHESTRATION:
                            // 1. Analyze style ONCE with VLM
                            // 2. Parse conditional rules
                            // 3. Classify lighting and apply rules locally for all other photos
                            // 4. Log each edit to Room
                            // 5. Navigate to review screen
                            coroutineScope.launch {
                                isProcessingBatch = true
                                batchProgressMessage = "Analyzing calibration style with Gemini..."

                                try {
                                    val afterRefBitmap = withContext(Dispatchers.Default) {
                                        ImageProcessor.apply(refBitmap, referenceParams)
                                    }

                                    val beforeB64 = bitmapToBase64(refBitmap)
                                    val afterB64 = bitmapToBase64(afterRefBitmap)

                                    // Step 1: Call vlmClient.analyzeStyle() ONCE
                                    val style = withContext(Dispatchers.IO) {
                                        vlmClient.analyzeStyle(beforeB64, afterB64)
                                    }
                                    currentStyleDescription = style

                                    // Step 2: Call vlmClient.parseConditionalRules(style)
                                    val rules = vlmClient.parseConditionalRules(style)

                                    val items = mutableListOf<EditedPhotoItem>()

                                    // Reference photo itself
                                    items.add(
                                        EditedPhotoItem(
                                            photo = referencePhoto,
                                            initialBitmap = refBitmap,
                                            editedBitmap = afterRefBitmap,
                                            params = referenceParams
                                        )
                                    )

                                    // Step 4: Log reference photo
                                    withContext(Dispatchers.IO) {
                                        editLogRepository.logEdit(
                                            EditLogEntry(
                                                photoId = referencePhoto.id,
                                                styleSummary = style.summary,
                                                initialParams = referenceParams.toString(),
                                                finalParams = referenceParams.toString(),
                                                wasCorrected = false,
                                                timestampMillis = System.currentTimeMillis()
                                            )
                                        )
                                    }

                                    // Step 3: Loop every other photo in trip locally without calling model
                                    val remainingPhotos = trip.photos.filter { it.id != referencePhoto.id }
                                    for ((index, photo) in remainingPhotos.withIndex()) {
                                        batchProgressMessage = "Locally classifying & rendering photo ${index + 1} of ${remainingPhotos.size}..."

                                        val origBitmap = loadBitmap(context, photo.uri)

                                        // Offline lighting classification (no API call)
                                        val lighting = withContext(Dispatchers.Default) {
                                            ImageProcessor.classifyLighting(origBitmap)
                                        }

                                        // Match rule & apply parameters
                                        val photoParams = ImageProcessor.matchAndApplyRules(
                                            photoId = photo.id,
                                            lighting = lighting,
                                            rules = rules,
                                            fallbackParams = referenceParams
                                        )

                                        val editedBitmap = withContext(Dispatchers.Default) {
                                            ImageProcessor.apply(origBitmap, photoParams)
                                        }

                                        // Log edit
                                        withContext(Dispatchers.IO) {
                                            editLogRepository.logEdit(
                                                EditLogEntry(
                                                    photoId = photo.id,
                                                    styleSummary = style.summary,
                                                    initialParams = photoParams.toString(),
                                                    finalParams = photoParams.toString(),
                                                    wasCorrected = false,
                                                    timestampMillis = System.currentTimeMillis()
                                                )
                                            )
                                        }

                                        items.add(
                                            EditedPhotoItem(
                                                photo = photo,
                                                initialBitmap = origBitmap,
                                                editedBitmap = editedBitmap,
                                                params = photoParams
                                            )
                                        )
                                    }

                                    editedPhotoItems.clear()
                                    editedPhotoItems.addAll(items)
                                    isProcessingBatch = false
                                    navController.navigate("review")
                                } catch (e: Exception) {
                                    isProcessingBatch = false
                                    batchProgressMessage = "Error: ${e.message}"
                                }
                            }
                        }
                    )
                }
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }

        // SCREEN 4: REVIEW & PER-PHOTO CORRECTION LOOP
        composable("review") {
            val trip = selectedTrip
            val style = currentStyleDescription

            if (trip != null && style != null) {
                TripReviewScreen(
                    trip = trip,
                    style = style,
                    items = editedPhotoItems,
                    onCorrectionRequested = { item, note ->
                        // Re-query model ONLY for the flagged photo
                        coroutineScope.launch {
                            item.isReevaluating = true
                            try {
                                val b64 = bitmapToBase64(item.initialBitmap)
                                val correctedParams = withContext(Dispatchers.IO) {
                                    vlmClient.replanWithCorrection(
                                        photoId = item.photo.id,
                                        photoBase64 = b64,
                                        style = style,
                                        previousParams = item.params,
                                        correctionNote = note
                                    )
                                }

                                val updatedBitmap = withContext(Dispatchers.Default) {
                                    ImageProcessor.apply(item.initialBitmap, correctedParams)
                                }

                                withContext(Dispatchers.IO) {
                                    editLogRepository.logEdit(
                                        EditLogEntry(
                                            photoId = item.photo.id,
                                            styleSummary = style.summary,
                                            initialParams = item.params.toString(),
                                            finalParams = correctedParams.toString(),
                                            wasCorrected = true,
                                            correctionNote = note,
                                            timestampMillis = System.currentTimeMillis()
                                        )
                                    )
                                }

                                item.editedBitmap = updatedBitmap
                                item.params = correctedParams
                                item.wasCorrected = true
                                item.correctionNote = note
                            } finally {
                                item.isReevaluating = false
                            }
                        }
                    },
                    onDone = {
                        navController.navigate("gallery") {
                            popUpTo("gallery") { inclusive = true }
                        }
                    }
                )
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }
    }
}

/**
 * Simple review screen / list of the edited photos.
 * Allows the user to inspect any photo that is wrong and trigger
 * vlmClient.replanWithCorrection() on JUST that photo.
 */
@Composable
fun TripReviewScreen(
    trip: Trip,
    style: StyleDescription,
    items: List<EditedPhotoItem>,
    onCorrectionRequested: (EditedPhotoItem, String) -> Unit,
    onDone: () -> Unit
) {
    var inspectingItem by remember { mutableStateOf<EditedPhotoItem?>(null) }
    var correctionText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            text = "Trip Review: ${trip.id}",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Style: \"${style.summary}\"",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Text(
            text = "Tap any photo that needs adjustment to explain what's off.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            inspectingItem = item
                            correctionText = ""
                        }
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Image(
                            bitmap = item.editedBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Photo #${item.photo.id}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (item.wasCorrected) {
                            Text(
                                text = "Corrected: ${item.correctionNote}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = item.params.reasoning,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Accept All Edits & Finish")
        }
    }

    // Modal dialog to trigger replanWithCorrection on a specific photo
    inspectingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { inspectingItem = null },
            title = { Text("Flag Photo #${item.photo.id}") },
            text = {
                Column {
                    Image(
                        bitmap = item.editedBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Current params: Exp=${item.params.exposureShift}, WB=${item.params.whiteBalanceShiftK}K, Sat=${item.params.saturationShift}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = correctionText,
                        onValueChange = { correctionText = it },
                        label = { Text("What went wrong? (e.g. Too dark, warm up skin)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val note = correctionText.trim()
                        if (note.isNotEmpty()) {
                            onCorrectionRequested(item, note)
                            inspectingItem = null
                        }
                    },
                    enabled = correctionText.isNotBlank() && !item.isReevaluating
                ) {
                    Text(if (item.isReevaluating) "Re-evaluating..." else "Re-edit with Gemini")
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectingItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Helpers for Bitmap loading and Base64 conversion
private fun loadBitmap(context: Context, uriString: String): Bitmap {
    return try {
        val uri = Uri.parse(uriString)
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        } ?: createPlaceholderBitmap(400, 300)
    } catch (e: Exception) {
        createPlaceholderBitmap(400, 300)
    }
}

private fun createPlaceholderBitmap(width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { color = Color.DKGRAY }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    return bitmap
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
}
