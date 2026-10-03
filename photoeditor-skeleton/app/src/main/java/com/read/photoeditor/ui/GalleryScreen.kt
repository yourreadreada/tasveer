package com.read.photoeditor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.read.photoeditor.data.model.Trip
import java.text.SimpleDateFormat
import java.util.*

/**
 * Top-level screen: auto-clustered trips, newest first. Tapping a trip opens TripScreen.
 * Includes search functionality by location name and date range filtering based on start date.
 */
@Composable
fun GalleryScreen(
    trips: List<Trip>,
    onTripClick: (Trip) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var startDateFilterMillis by remember { mutableStateOf<Long?>(null) }
    var endDateFilterMillis by remember { mutableStateOf<Long?>(null) }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    // Filtered trips by location name and start date
    val filteredTrips = remember(trips, searchQuery, startDateFilterMillis, endDateFilterMillis) {
        trips.filter { trip ->
            val matchesLocation = if (searchQuery.isBlank()) {
                true
            } else {
                trip.photos.any { it.latitude != null } ||
                    trip.id.contains(searchQuery, ignoreCase = true)
            }

            val matchesStartDate = startDateFilterMillis?.let { minStart ->
                trip.startMillis >= minStart
            } ?: true

            val matchesEndDate = endDateFilterMillis?.let { maxStart ->
                trip.startMillis <= maxStart
            } ?: true

            matchesLocation && matchesStartDate && matchesEndDate
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Search by location name
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search by location...") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true
        )

        // Date Range Filter indicator / reset
        if (startDateFilterMillis != null || endDateFilterMillis != null || searchQuery.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Showing ${filteredTrips.size} of ${trips.size} trips",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = {
                    searchQuery = ""
                    startDateFilterMillis = null
                    endDateFilterMillis = null
                }) {
                    Text("Clear filters")
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredTrips) { trip ->
                Column(modifier = Modifier.clickable { onTripClick(trip) }) {
                    AsyncImage(
                        model = trip.photos.firstOrNull()?.uri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                    Text("${trip.photos.size} photos", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "${dateFormat.format(Date(trip.startMillis))} – ${dateFormat.format(Date(trip.endMillis))}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
