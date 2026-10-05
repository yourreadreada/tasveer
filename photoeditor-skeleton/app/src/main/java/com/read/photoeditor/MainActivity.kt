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
import androidx.lifecycle.lifecycleScope
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
import com.read.photoeditor.data.PhotoSaver
import com.read.photoeditor.data.TripClusterer
import com.read.photoeditor.data.model.EditLogEntry
import com.read.photoeditor.data.model.Photo
import com.read.photoeditor.data.model.PhotoEditParams
import com.read.photoeditor.data.model.StyleDescription
import com.read.photoeditor.data.model.Trip
import com.read.photoeditor.editing.ImageProcessor
import com.read.photoeditor.ui.ApiKeyStorage
import com.read.photoeditor.ui.CleanupScreen
import com.read.photoeditor.ui.DatasetProgressScreen
import com.read.photoeditor.ui.EditScreen
import com.read.photoeditor.ui.GalleryScreen
import com.read.photoeditor.ui.SettingsScreen
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
 * Gallery -> Trip -> Edit (Reference) -> [Orchestrated Trip Apply] -> Review & Save
 */
class MainActivity : ComponentActivity() {

    private lateinit var photoRepository: PhotoRepository
    private lateinit var editLogRepository: EditLogRepository

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadAndShowPhotos()
    }

    private var tripsState: MutableState<List<Trip>>? = null
    private var screenshotCountState: MutableState<Int>? = null
    private var isLoadingState: MutableState<Boolean>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        photoRepository = PhotoRepository(this)

        val db = Room.databaseBuilder(this, EditLogDatabase::class.java, "edit_log.db").build()
        editLogRepository = EditLogRepository(db.editLogDao())

        setContent {
            val trips = remember { mutableStateOf<List<Trip>>(emptyList()) }
            val screenshotCount = remember { mutableIntStateOf(0) }
            val isLoading = remember { mutableStateOf(true) }
            tripsState = trips
            screenshotCountState = screenshotCount
            isLoadingState = isLoading
            val navController = rememberNavController()

            Surface {
                AppNavHost(
                    navController = navController,
                    trips = trips.value,
                    screenshotCount = screenshotCount.intValue,
                    isLoading = isLoading.value,
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
        isLoadingState?.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            val photos = photoRepository.loadAllPhotos()
            val screenshots = photos.filter { it.isScreenshot }

            // Group photos folder-wise (Google Photos device folders style) instead of auto-clustering trips
            val folders = photos.groupBy { it.folderName.ifBlank { "Camera" } }
                .map { (folderName, folderPhotos) ->
                    Trip(
                        id = folderName,
                        photos = folderPhotos,
                        startMillis = folderPhotos.minOfOrNull { it.takenAtMillis } ?: 0L,
                        endMillis = folderPhotos.maxOfOrNull { it.takenAtMillis } ?: 0L,
                        locationName = folderName
                    )
                }

            withContext(Dispatchers.Main) {
                screenshotCountState?.value = screenshots.size
                tripsState?.value = folders
                isLoadingState?.value = false
            }
        }
    }
}

/**
 * Navigation graph managing app destinations:
 * 1. "gallery"  -> Trip cluster overview with date filter & screenshot badge
 * 2. "settings" -> API Key input with EncryptedSharedPreferences (Feature 2)
 * 3. "dataset"  -> Dataset logging metrics toward 100-edit model (Feature 6)
 * 4. "trip"     -> Select 1-2 reference photos or navigate to Cleanup
 * 5. "cleanup"  -> Detect duplicates (dHash) & blurry photos (Laplacian) (Feature 3)
 * 6. "edit"     -> Calibrate look and run batch offline execution
 * 7. "review"   -> Review, flag corrections, and save to MediaStore (Feature 1)
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    trips: List<Trip>,
    screenshotCount: Int,
    isLoading: Boolean = false,
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
    var showApiKeyPromptDialog by remember { mutableStateOf(false) }

    NavHost(navController = navController, startDestination = "gallery") {
        // SCREEN 1: GALLERY
        composable("gallery") {
            GalleryScreen(
                trips = trips,
                screenshotCount = screenshotCount,
                isLoading = isLoading,
                onTripClick = { trip ->
                    selectedTrip = trip
                    navController.navigate("trip")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
                onDatasetClick = {
                    navController.navigate("dataset")
                }
            )
        }

        // SCREEN 2: SETTINGS (Feature 2)
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDatasetProgress = { navController.navigate("dataset") }
            )
        }

        // SCREEN 3: DATASET PROGRESS (Feature 6)
        composable("dataset") {
            DatasetProgressScreen(
                editLogRepository = editLogRepository,
                onBack = { navController.popBackStack() }
            )
        }

        // SCREEN 4: TRIP SCREEN (PICK 1-2 REFERENCE PHOTOS OR CLEANUP)
        composable("trip") {
            val trip = selectedTrip
            if (trip != null) {
                TripScreen(
                    trip = trip,
                    onReferenceChosen = { references ->
                        selectedReferences = references
                        // Feature 2: Verify API Key is stored before calibrating
                        val key = ApiKeyStorage.getApiKey(context)
                        if (key.isBlank()) {
                            showApiKeyPromptDialog = true
                        } else {
                            navController.navigate("edit")
                        }
                    },
                    onNavigateToCleanup = {
                        navController.navigate("cleanup")
                    }
                )
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }

        // SCREEN 5: CLEANUP SCREEN (Feature 3: Duplicates & Blurry Photos)
        composable("cleanup") {
            val trip = selectedTrip
            if (trip != null) {
                CleanupScreen(
                    trip = trip,
                    loadBitmap = { photo -> loadBitmap(context, photo.uri) },
                    onBack = { navController.popBackStack() }
                )
            } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }

        // SCREEN 6: EDIT REFERENCE & APPLY TO TRIP
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
                            // Use stored API key
                            val apiKey = ApiKeyStorage.getApiKey(context)
                            if (apiKey.isBlank()) {
                                showApiKeyPromptDialog = true
                                return@EditScreen
                            }

                            val vlmClient = VLMClient(apiKey)

                            coroutineScope.launch {
                                isProcessingBatch = true
                                batchProgressMessage = "Analyzing calibration style with Gemini..."

                                try {
                                    val afterRefBitmap = withContext(Dispatchers.Default) {
                                        ImageProcessor.apply(refBitmap, referenceParams)
                                    }

                                    val beforeB64 = bitmapToBase64(refBitmap)
                                    val afterB64 = bitmapToBase64(afterRefBitmap)

                                    // 1. Call vlmClient.analyzeStyle() ONCE
                                    val style = withContext(Dispatchers.IO) {
                                        vlmClient.analyzeStyle(beforeB64, afterB64)
                                    }
                                    currentStyleDescription = style

                                    // 2. Call vlmClient.parseConditionalRules(style)
                                    val rules = vlmClient.parseConditionalRules(style)

                                    val items = mutableListOf<EditedPhotoItem>()

                                    // Reference photo
                                    items.add(
                                        EditedPhotoItem(
                                            photo = referencePhoto,
                                            initialBitmap = refBitmap,
                                            editedBitmap = afterRefBitmap,
                                            params = referenceParams
                                        )
                                    )

                                    // 4. Log reference photo
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

                                    // 3. For every other photo in trip: offline lighting classification & rules
                                    val remainingPhotos = trip.photos.filter { it.id != referencePhoto.id }
                                    for ((index, photo) in remainingPhotos.withIndex()) {
                                        batchProgressMessage = "Locally classifying & rendering photo ${index + 1} of ${remainingPhotos.size}..."

                                        val origBitmap = loadBitmap(context, photo.uri)

                                        val lighting = withContext(Dispatchers.Default) {
                                            ImageProcessor.classifyLighting(origBitmap)
                                        }

                                        val photoParams = ImageProcessor.matchAndApplyRules(
                                            photoId = photo.id,
                                            lighting = lighting,
                                            rules = rules,
                                            fallbackParams = referenceParams
                                        )

                                        val editedBitmap = withContext(Dispatchers.Default) {
                                            ImageProcessor.apply(origBitmap, photoParams)
                                        }

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

        // SCREEN 7: REVIEW, CORRECTIONS & SAVING TO MEDIASTORE (Feature 1)
        composable("review") {
            val trip = selectedTrip
            val style = currentStyleDescription

            if (trip != null && style != null) {
                TripReviewScreen(
                    trip = trip,
                    style = style,
                    items = editedPhotoItems,
                    onCorrectionRequested = { item, note ->
                        val apiKey = ApiKeyStorage.getApiKey(context)
                        val vlmClient = VLMClient(apiKey)

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

    // Feature 2: Prompt to enter API Key if missing
    if (showApiKeyPromptDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyPromptDialog = false },
            title = { Text("Gemini API Key Required") },
            text = {
                Text("To calibrate a trip's aesthetic, please add your Google Gemini API key in Settings. Your key is stored securely on your device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showApiKeyPromptDialog = false
                        navController.navigate("settings")
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyPromptDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Review screen of all edited photos in the trip.
 * Feature 1: "Accept All Edits & Finish" saves every edited photo to MediaStore
 * under "Pictures/Tasveer Edited" with an interactive progress indicator.
 */
@Composable
fun TripReviewScreen(
    trip: Trip,
    style: StyleDescription,
    items: List<EditedPhotoItem>,
    onCorrectionRequested: (EditedPhotoItem, String) -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var inspectingItem by remember { mutableStateOf<EditedPhotoItem?>(null) }
    var correctionText by remember { mutableStateOf("") }

    // Feature 1: MediaStore saving progress state
    var isSavingBatch by remember { mutableStateOf(false) }
    var savedCount by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            text = "Trip Review: ${trip.locationName ?: trip.id}",
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

        // Feature 1: Save All to MediaStore
        Button(
            onClick = {
                coroutineScope.launch {
                    isSavingBatch = true
                    savedCount = 0

                    items.forEachIndexed { index, item ->
                        val prefix = "tasveer_${trip.id}_${item.photo.id}_${System.currentTimeMillis()}"
                        PhotoSaver.saveBitmapToMediaStore(context, item.editedBitmap, prefix)
                        savedCount = index + 1
                    }

                    isSavingBatch = false
                    onDone()
                }
            },
            enabled = !isSavingBatch && items.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isSavingBatch) "Saving to Pictures/Tasveer Edited..." else "Accept All Edits & Finish (${items.size})")
        }
    }

    // Feature 1: Progress indicator dialog while saving
    if (isSavingBatch) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Saving Edited Photos") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { savedCount.toFloat() / items.size.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Saving $savedCount of ${items.size} photos to \"Pictures/Tasveer Edited\" album...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {}
        )
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
