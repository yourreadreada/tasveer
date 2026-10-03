package com.read.photoeditor

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.read.photoeditor.data.EditLogRepository
import com.read.photoeditor.data.PhotoRepository
import com.read.photoeditor.data.TripClusterer
import com.read.photoeditor.data.EditLogDatabase
import com.read.photoeditor.data.model.Trip
import com.read.photoeditor.ui.EditScreen
import com.read.photoeditor.ui.GalleryScreen
import com.read.photoeditor.ui.TripScreen
import com.read.photoeditor.vlm.VLMClient

/**
 * App entry point. Handles the runtime permission request, loads + clusters photos,
 * and hosts the three-screen flow: Gallery -> Trip -> Edit (reference) -> [apply to rest].
 *
 * NOTE: the "apply AI edits to the rest of the trip" step (calling VLMClient.analyzeStyle
 * then planEdit per photo, running ImageProcessor, logging via EditLogRepository, and
 * showing the review/correction grid) is intentionally left as a TODO wiring point —
 * the pieces all exist (VLMClient, ImageProcessor, EditLogRepository) but the orchestrating
 * ViewModel/coroutine flow is the next thing to build once this skeleton compiles and runs.
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

        // TODO: don't hardcode the key — read from local.properties / a secure source,
        // and see the security note in VLMClient before this app ever leaves your device.
        vlmClient = VLMClient(apiKey = "YOUR_GEMINI_API_KEY")

        setContent {
            val trips = remember { mutableStateOf<List<Trip>>(emptyList()) }
            tripsState = trips
            val navController = rememberNavController()

            Surface {
                AppNavHost(navController = navController, trips = trips.value)
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

@Composable
fun AppNavHost(navController: NavHostController, trips: List<Trip>) {
    NavHost(navController = navController, startDestination = "gallery") {
        composable("gallery") {
            GalleryScreen(trips = trips) { trip ->
                navController.currentBackStackEntry?.savedStateHandle?.set("trip", trip)
                navController.navigate("trip")
            }
        }
        composable("trip") {
            // NOTE: passing Trip directly through savedStateHandle only works once Trip
            // implements Parcelable (add @Parcelize in EditModels.kt) — or, simpler,
            // pass just the trip's id/index and look it up from a shared ViewModel instead.
            val trip = navController.previousBackStackEntry?.savedStateHandle?.get<Trip>("trip")
            if (trip != null) {
                TripScreen(trip = trip) { referencePhotos ->
                    // TODO: navigate to EditScreen with the first chosen reference photo,
                    // load its Bitmap via contentResolver, and on confirm, kick off the
                    // analyzeStyle() -> planEdit() per remaining photo -> ImageProcessor.apply()
                    // -> EditLogRepository.logEdit() pipeline described in VLMClient's docs.
                }
            }
        }
        // "edit" route to be wired once the apply-to-trip orchestration above is built.
    }
}
