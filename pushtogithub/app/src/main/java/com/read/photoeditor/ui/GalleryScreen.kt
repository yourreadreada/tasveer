package com.read.photoeditor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.read.photoeditor.data.model.Trip
import com.read.photoeditor.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

enum class GalleryViewMode {
    FOLDERS,
    TRIPS
}

enum class FolderDisplayMode {
    GRID,
    STREAM
}

/**
 * Top-level gallery screen matching the AI Studio dark stone luxury aesthetic.
 *
 * Primary Mode (Folders):
 * - Groups photos by their on-device folder (like Google Photos), preserving folder organization.
 * - Supports Folder Grid with multi-thumbnail strips and Stream View.
 *
 * Alternate Mode (Trips):
 * - Auto-clustered trips based on time gaps and GPS drift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    folders: List<Trip>,
    trips: List<Trip>,
    screenshotCount: Int = 0,
    datasetCount: Int = 0,
    isLoading: Boolean = false,
    onTripClick: (Trip) -> Unit,
    onSettingsClick: () -> Unit,
    onDatasetClick: () -> Unit
) {
    var viewMode by remember { mutableStateOf(GalleryViewMode.FOLDERS) }
    var folderDisplayMode by remember { mutableStateOf(FolderDisplayMode.GRID) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolderFilter by remember { mutableStateOf<String?>(null) }

    // Date range filters for trips mode
    var startDateFilterMillis by remember { mutableStateOf<Long?>(null) }
    var endDateFilterMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    val dateRangePickerState = rememberDateRangePickerState()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    // Current active collection based on mode
    val activeCollection = if (viewMode == GalleryViewMode.FOLDERS) folders else trips

    val filteredItems = remember(activeCollection, viewMode, searchQuery, selectedFolderFilter, startDateFilterMillis, endDateFilterMillis) {
        activeCollection.filter { item ->
            // Search query filter
            val query = searchQuery.trim().lowercase(Locale.getDefault())
            val matchesSearch = if (query.isEmpty()) {
                true
            } else {
                val name = (item.locationName ?: item.id).lowercase(Locale.getDefault())
                val matchesPhotos = item.photos.any { it.title?.lowercase(Locale.getDefault())?.contains(query) == true }
                name.contains(query) || matchesPhotos
            }

            if (!matchesSearch) return@filter false

            if (viewMode == GalleryViewMode.FOLDERS) {
                // Folder quick filter chip
                if (selectedFolderFilter != null && selectedFolderFilter != "All") {
                    if (item.locationName != selectedFolderFilter) return@filter false
                }
                true
            } else {
                // Date range filter for trips mode
                val matchesStart = startDateFilterMillis?.let { item.startMillis >= it } ?: true
                val matchesEnd = endDateFilterMillis?.let { item.startMillis <= it } ?: true
                matchesStart && matchesEnd
            }
        }
    }

    Scaffold(
        containerColor = Stone950,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Stone900)
                    .border(BorderStroke(1.dp, Stone800))
            ) {
                // Top Bar Row: Brand & Utilities
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Brand: Gradient camera icon + Tasveer + VLM Editor pill badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Amber600, Orange500, Amber400)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Tasveer Camera",
                                tint = Stone950,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "Tasveer",
                            color = Stone100,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Amber500.copy(alpha = 0.15f))
                                .border(BorderStroke(1.dp, Amber500.copy(alpha = 0.35f)), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "VLM EDITOR",
                                color = Amber300,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Utilities: Training Log Badge + Settings Icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Stone950,
                            border = BorderStroke(1.dp, Emerald400.copy(alpha = 0.35f)),
                            modifier = Modifier.clickable { onDatasetClick() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Emerald400)
                                )
                                Text(
                                    text = "Log: $datasetCount",
                                    color = Emerald400,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Stone400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Row 2: Mode Segmented Control (Folders vs Trips)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Stone950,
                        border = BorderStroke(1.dp, Stone800),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Folders Pill
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (viewMode == GalleryViewMode.FOLDERS) Stone800 else Color.Transparent)
                                    .clickable { viewMode = GalleryViewMode.FOLDERS }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (viewMode == GalleryViewMode.FOLDERS) Amber400 else Stone500,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Device Folders (${folders.size})",
                                        color = if (viewMode == GalleryViewMode.FOLDERS) Stone100 else Stone400,
                                        fontSize = 12.sp,
                                        fontWeight = if (viewMode == GalleryViewMode.FOLDERS) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            // Trips Pill
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (viewMode == GalleryViewMode.TRIPS) Stone800 else Color.Transparent)
                                    .clickable { viewMode = GalleryViewMode.TRIPS }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Explore,
                                        contentDescription = null,
                                        tint = if (viewMode == GalleryViewMode.TRIPS) Amber400 else Stone500,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Trips (${trips.size})",
                                        color = if (viewMode == GalleryViewMode.TRIPS) Stone100 else Stone400,
                                        fontSize = 12.sp,
                                        fontWeight = if (viewMode == GalleryViewMode.TRIPS) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // AI Studio Header: Pill badge + Stats
            val totalPhotos = activeCollection.sumOf { it.photos.size }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Amber500.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Amber500.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (viewMode == GalleryViewMode.FOLDERS) Icons.Default.Folder else Icons.Default.Explore,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (viewMode == GalleryViewMode.FOLDERS) "Device Folders" else "Clustered Trips",
                            color = Amber300,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = "${activeCollection.size} ${if (viewMode == GalleryViewMode.FOLDERS) "folders" else "trips"} • $totalPhotos photos",
                    color = Stone400,
                    fontSize = 12.sp
                )
            }

            // Main Title & Subtitle + View Mode Switcher
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (viewMode == GalleryViewMode.FOLDERS) "Photos on device" else "Clustered Trips",
                        color = Stone100,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (viewMode == GalleryViewMode.FOLDERS)
                            "Organized folder-wise like Google Photos. Select any folder (Camera, Downloads, WhatsApp, etc.) to browse its photos and calibrate AI edits."
                        else
                            "Tasveer automatically clusters photos by time gaps (≥18 hours) and GPS drift (≥50km). Search by location and filter by date.",
                        color = Stone400,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                if (viewMode == GalleryViewMode.FOLDERS) {
                    // [ Folder Grid | Stream View ] toggle
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Stone900,
                        border = BorderStroke(1.dp, Stone800)
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (folderDisplayMode == FolderDisplayMode.GRID) Amber500 else Color.Transparent)
                                    .clickable { folderDisplayMode = FolderDisplayMode.GRID }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    "Grid",
                                    color = if (folderDisplayMode == FolderDisplayMode.GRID) Stone950 else Stone400,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (folderDisplayMode == FolderDisplayMode.STREAM) Amber500 else Color.Transparent)
                                    .clickable { folderDisplayMode = FolderDisplayMode.STREAM }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    "Stream",
                                    color = if (folderDisplayMode == FolderDisplayMode.STREAM) Stone950 else Stone400,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Search & Filter Toolbar
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Stone900),
                border = BorderStroke(1.dp, Stone800),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Search text field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = if (viewMode == GalleryViewMode.FOLDERS)
                                    "Search folders or photos (e.g. Camera, WhatsApp, Downloads)..."
                                else
                                    "Search trips by location (e.g. Goa, Paris)...",
                                color = Stone500,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Stone500, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Stone400, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Stone950,
                            unfocusedContainerColor = Stone950,
                            focusedBorderColor = Amber500,
                            unfocusedBorderColor = Stone800,
                            focusedTextColor = Stone100,
                            unfocusedTextColor = Stone100
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary row: Quick chips / date filter button & screenshot badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (viewMode == GalleryViewMode.FOLDERS) {
                            // Folder quick chips
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Stone500, modifier = Modifier.size(13.dp))
                                    Text("Folder:", color = Stone500, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }

                                // "All Folders" chip
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedFolderFilter == null || selectedFolderFilter == "All") Amber500.copy(0.15f) else Stone950,
                                    border = BorderStroke(1.dp, if (selectedFolderFilter == null || selectedFolderFilter == "All") Amber500.copy(0.4f) else Stone800),
                                    modifier = Modifier.clickable { selectedFolderFilter = "All" }
                                ) {
                                    Text(
                                        text = "All Folders (${folders.size})",
                                        color = if (selectedFolderFilter == null || selectedFolderFilter == "All") Amber300 else Stone400,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                folders.forEach { folder ->
                                    val isSelected = selectedFolderFilter == folder.locationName
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Amber500.copy(0.15f) else Stone950,
                                        border = BorderStroke(1.dp, if (isSelected) Amber500.copy(0.4f) else Stone800),
                                        modifier = Modifier.clickable { selectedFolderFilter = folder.locationName }
                                    ) {
                                        Text(
                                            text = "${folder.locationName ?: "Folder"} (${folder.photos.size})",
                                            color = if (isSelected) Amber300 else Stone400,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            // Trips Mode: Date filter button
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { showDatePickerDialog = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber400),
                                    border = BorderStroke(1.dp, Amber500.copy(0.5f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (startDateFilterMillis != null || endDateFilterMillis != null) "Filtered by Date" else "Filter Dates",
                                        fontSize = 11.sp
                                    )
                                }

                                if (startDateFilterMillis != null || endDateFilterMillis != null) {
                                    TextButton(
                                        onClick = {
                                            startDateFilterMillis = null
                                            endDateFilterMillis = null
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Text("Clear", color = Amber400, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // Screenshots excluded badge
                        if (screenshotCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Stone950,
                                border = BorderStroke(1.dp, Stone800),
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Stone500, modifier = Modifier.size(11.dp))
                                    Text(
                                        text = "$screenshotCount screenshots excluded",
                                        color = Stone400,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Body Area
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Amber500)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (viewMode == GalleryViewMode.FOLDERS) "Scanning device photo folders..." else "Clustering trips by time & GPS...",
                            color = Stone300,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = Stone600,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No folders or photos match \"$searchQuery\"" else "No photos found on device",
                            color = Stone300,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Take or copy photos into Pictures or DCIM to edit with Tasveer.",
                            color = Stone500,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                AnimatedContent(
                    targetState = Pair(viewMode, folderDisplayMode),
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 0.98f, animationSpec = tween(180, easing = FastOutSlowInEasing)))
                            .togetherWith(
                                fadeOut(animationSpec = tween(140, easing = FastOutLinearInEasing))
                            )
                    },
                    label = "GalleryModeTransition",
                    modifier = Modifier.weight(1f)
                ) { (currentViewMode, currentDisplayMode) ->
                    if (currentViewMode == GalleryViewMode.FOLDERS && currentDisplayMode == FolderDisplayMode.STREAM) {
                        // STREAM VIEW: Folder sections with horizontal photo rows
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                    items(filteredItems) { folder ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Stone900)
                                .border(BorderStroke(1.dp, Stone800), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTripClick(folder) },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = Amber400, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = folder.locationName ?: folder.id,
                                        color = Stone100,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "• ${folder.photos.size} photos",
                                        color = Stone400,
                                        fontSize = 12.sp
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Open", color = Amber400, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Amber400, modifier = Modifier.size(12.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Horizontal scroll row of photos
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                folder.photos.forEach { photo ->
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Stone950)
                                            .border(BorderStroke(1.dp, Stone800), RoundedCornerShape(10.dp))
                                            .clickable { onTripClick(folder) }
                                    ) {
                                        AsyncImage(
                                            model = photo.uri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // FOLDER GRID / TRIPS GRID (Matching AI Studio Card Design)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredItems) { item ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Stone900),
                            border = BorderStroke(1.dp, Stone800),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTripClick(item) }
                        ) {
                            Column {
                                // Thumbnail with Aspect Ratio 16/10 + Badges & Gradient
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .background(Stone950)
                                ) {
                                    val coverPhoto = item.photos.firstOrNull()
                                    if (coverPhoto != null) {
                                        AsyncImage(
                                            model = coverPhoto.uri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Image,
                                                contentDescription = null,
                                                tint = Stone700,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }

                                    // Top-left glass pill badge: Photo count (matching AI Studio)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Stone950.copy(alpha = 0.82f))
                                            .border(BorderStroke(1.dp, Stone800), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (viewMode == GalleryViewMode.FOLDERS) Icons.Default.Folder else Icons.Default.DateRange,
                                                contentDescription = null,
                                                tint = Amber400,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = "${item.photos.size} photos",
                                                color = Stone200,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    // Bottom Gradient overlay
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(65.dp)
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, Stone950.copy(alpha = 0.95f))
                                                )
                                            )
                                    )

                                    // Title & Action Button overlay on bottom of image (matching AI Studio)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomStart)
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                            Text(
                                                text = item.locationName ?: item.id,
                                                color = Stone100,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Ready for AI editing",
                                                color = Stone400,
                                                fontSize = 10.sp
                                            )
                                        }

                                        // Circular Amber Arrow Button
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Amber500),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Open",
                                                tint = Stone950,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                // Mini Thumbnail Strip Below Cover (Matching AI Studio)
                                val previewThumbnails = item.photos.take(4)
                                if (previewThumbnails.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        previewThumbnails.forEach { thumb ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(34.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Stone950)
                                                    .border(BorderStroke(1.dp, Stone800), RoundedCornerShape(6.dp))
                                            ) {
                                                AsyncImage(
                                                    model = thumb.uri,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }

                                        // If more than 4 photos in folder, show +N box
                                        if (item.photos.size > 4) {
                                            Box(
                                                modifier = Modifier
                                                    .width(30.dp)
                                                    .height(34.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Stone950)
                                                    .border(BorderStroke(1.dp, Stone800), RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "+${item.photos.size - 4}",
                                                    color = Stone400,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
    }

    // Material3 Date Range Picker Dialog for Trips Mode
    if (showDatePickerDialog) {
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        startDateFilterMillis = dateRangePickerState.selectedStartDateMillis
                        endDateFilterMillis = dateRangePickerState.selectedEndDateMillis
                        showDatePickerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber500, contentColor = Stone950)
                ) {
                    Text("Apply Range", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel", color = Stone400)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = Stone900)
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = {
                    Text("Select Trip Date Range", color = Stone100, modifier = Modifier.padding(16.dp))
                },
                modifier = Modifier.fillMaxWidth().height(460.dp)
            )
        }
    }
}
