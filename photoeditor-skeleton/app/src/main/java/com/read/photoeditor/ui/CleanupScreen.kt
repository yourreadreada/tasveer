package com.read.photoeditor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.read.photoeditor.data.BlurCheckResult
import com.read.photoeditor.data.BlurDetector
import com.read.photoeditor.data.DuplicateDetector
import com.read.photoeditor.data.DuplicateGroup
import com.read.photoeditor.data.model.Photo
import com.read.photoeditor.data.model.Trip
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupScreen(
    trip: Trip,
    loadBitmap: (Photo) -> Bitmap?,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Duplicates, 1: Blurry
    var isAnalyzing by remember { mutableStateOf(true) }

    var duplicateGroups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var blurryPhotos by remember { mutableStateOf<List<Pair<Photo, BlurCheckResult>>>(emptyList()) }
    val markedForDeletion = remember { mutableStateListOf<Long>() }

    var showConfirmDialog by remember { mutableStateOf(false) }

    // Run offline detectors on entry
    LaunchedEffect(trip) {
        isAnalyzing = true
        coroutineScope.launch {
            val dupes = DuplicateDetector.findDuplicates(trip.photos, loadBitmap)
            val blurry = mutableListOf<Pair<Photo, BlurCheckResult>>()

            trip.photos.forEach { photo ->
                val bmp = loadBitmap(photo)
                if (bmp != null) {
                    val result = BlurDetector.checkBlur(bmp)
                    if (result.isBlurry) {
                        blurry.add(photo to result)
                    }
                }
            }

            duplicateGroups = dupes
            blurryPhotos = blurry

            // Automatically suggest extra duplicates for deletion (keep primary)
            dupes.forEach { group ->
                group.duplicates.forEach { dup ->
                    if (!markedForDeletion.contains(dup.id)) {
                        markedForDeletion.add(dup.id)
                    }
                }
            }
            isAnalyzing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Cleanup — ${trip.photos.size} Photos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${markedForDeletion.size} marked for deletion",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(
                        onClick = { showConfirmDialog = true },
                        enabled = markedForDeletion.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Review Deletion")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Duplicates (${duplicateGroups.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Blurry (${blurryPhotos.size})") }
                )
            }

            if (isAnalyzing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Analyzing duplicates & sharpness locally...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (selectedTab == 0) {
                // Duplicates tab
                if (duplicateGroups.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No burst duplicates found in this trip!", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(duplicateGroups) { group ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Duplicate Group (${1 + group.duplicates.size} similar shots)",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Tap to toggle mark for deletion:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val allInGroup = listOf(group.primaryPhoto) + group.duplicates
                                        allInGroup.forEach { photo ->
                                            val isMarked = markedForDeletion.contains(photo.id)
                                            val isPrimary = photo.id == group.primaryPhoto.id

                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        if (isMarked) {
                                                            markedForDeletion.remove(photo.id)
                                                        } else {
                                                            markedForDeletion.add(photo.id)
                                                        }
                                                    }
                                            ) {
                                                Box {
                                                    AsyncImage(
                                                        model = photo.uri,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(100.dp)
                                                            .border(
                                                                width = if (isMarked) 3.dp else 1.dp,
                                                                color = if (isMarked) MaterialTheme.colorScheme.error else Color.Transparent
                                                            )
                                                    )
                                                    if (isPrimary && !isMarked) {
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.align(Alignment.TopStart)
                                                        ) {
                                                            Text(
                                                                "Best",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onPrimary,
                                                                modifier = Modifier.padding(horizontal = 4.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                Text(
                                                    text = if (isMarked) "Delete" else "Keep",
                                                    color = if (isMarked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Blurry photos tab
                if (blurryPhotos.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("All photos meet sharpness standards!", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(blurryPhotos) { (photo, result) ->
                            val isMarked = markedForDeletion.contains(photo.id)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isMarked) markedForDeletion.remove(photo.id)
                                        else markedForDeletion.add(photo.id)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = photo.uri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .border(
                                                width = if (isMarked) 3.dp else 0.dp,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Photo #${photo.id}", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "Laplacian score: ${result.variance.toInt()} (Low sharpness)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Checkbox(
                                        checked = isMarked,
                                        onCheckedChange = { checked ->
                                            if (checked) markedForDeletion.add(photo.id)
                                            else markedForDeletion.remove(photo.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirm Cleanup") },
            text = {
                Text(
                    "${markedForDeletion.size} photos marked for deletion.\n\n" +
                    "TODO: In the next release, actual deletion from MediaStore will execute via " +
                    "MediaStore.createDeleteRequest(contentResolver, uris) to ensure scoped storage compliance."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onBack()
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
