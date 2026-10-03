package com.read.photoeditor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.read.photoeditor.data.model.Trip
import java.text.SimpleDateFormat
import java.util.*

/**
 * Top-level screen: auto-clustered trips, newest first.
 * Features:
 * - Real trip location names reverse-geocoded from EXIF GPS (Feature 7)
 * - Working Material3 Date Range Picker (Feature 5)
 * - Screenshot auto-sorting exclusion badge (Feature 4)
 * - Settings & Dataset Progress navigation actions (Feature 2 & 6)
 * - Background loading indicator while scanning photo library & clustering
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    trips: List<Trip>,
    screenshotCount: Int = 0,
    isLoading: Boolean = false,
    onTripClick: (Trip) -> Unit,
    onSettingsClick: () -> Unit,
    onDatasetClick: () -> Unit
) {
    var startDateFilterMillis by remember { mutableStateOf<Long?>(null) }
    var endDateFilterMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    val dateRangePickerState = rememberDateRangePickerState()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    // Filtered trips based on selected start/end date range
    val filteredTrips = remember(trips, startDateFilterMillis, endDateFilterMillis) {
        trips.filter { trip ->
            val matchesStartDate = startDateFilterMillis?.let { minStart ->
                trip.startMillis >= minStart
            } ?: true

            val matchesEndDate = endDateFilterMillis?.let { maxStart ->
                trip.startMillis <= maxStart
            } ?: true

            matchesStartDate && matchesEndDate
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasveer Trips") },
                actions = {
                    IconButton(onClick = onDatasetClick) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Dataset Progress")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            // Filter Bar with Date Range Picker Button & Screenshot Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showDatePickerDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (startDateFilterMillis != null || endDateFilterMillis != null) "Date Filtered" else "Filter by Date",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Feature 4: Screenshot auto-sorting badge
                if (screenshotCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "$screenshotCount screenshots excluded",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Active Filter Reset row
            if (startDateFilterMillis != null || endDateFilterMillis != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Showing ${filteredTrips.size} of ${trips.size} trips",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = {
                        startDateFilterMillis = null
                        endDateFilterMillis = null
                    }) {
                        Text("Reset filter")
                    }
                }
            }

            // Main Content: Loading State vs Trip Cards Grid
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Scanning photo library & clustering trips...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (filteredTrips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (trips.isEmpty()) "No trips found in your photo library." else "No trips match the selected date filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredTrips) { trip ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTripClick(trip) }
                        ) {
                            Column {
                                AsyncImage(
                                    model = trip.photos.firstOrNull()?.uri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                )
                                Column(modifier = Modifier.padding(10.dp)) {
                                    // Feature 7: Real place name from reverse-geocoding, falling back to dates
                                    Text(
                                        text = trip.locationName ?: "${dateFormat.format(Date(trip.startMillis))} Trip",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${trip.photos.size} photos",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${dateFormat.format(Date(trip.startMillis))} – ${dateFormat.format(Date(trip.endMillis))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Feature 5: Material3 Date Range Picker Dialog
    if (showDatePickerDialog) {
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                Button(onClick = {
                    startDateFilterMillis = dateRangePickerState.selectedStartDateMillis
                    endDateFilterMillis = dateRangePickerState.selectedEndDateMillis
                    showDatePickerDialog = false
                }) {
                    Text("Apply Range")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = {
                    Text("Select Trip Date Range", modifier = Modifier.padding(16.dp))
                },
                modifier = Modifier.fillMaxWidth().height(460.dp)
            )
        }
    }
}
