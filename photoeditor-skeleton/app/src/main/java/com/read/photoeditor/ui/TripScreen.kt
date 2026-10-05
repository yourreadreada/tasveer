package com.read.photoeditor.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.read.photoeditor.data.model.Photo
import com.read.photoeditor.data.model.Trip

/**
 * Shows all photos in one trip. The user selects 1-2 photos here to be the
 * "reference edit" — per the preview-first flow, nothing gets touched until they've
 * calibrated on these first.
 * Also provides access to Trip Cleanup (duplicates & blurry photos).
 */
@Composable
fun TripScreen(
    trip: Trip,
    onReferenceChosen: (List<Photo>) -> Unit,
    onNavigateToCleanup: () -> Unit
) {
    val selected = remember { mutableStateListOf<Photo>() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trip.locationName ?: "Folder Photos",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${trip.photos.size} photos • Select 1-2 to calibrate edit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Feature 3: Entry point to CleanupScreen
            OutlinedButton(
                onClick = onNavigateToCleanup,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clean Up", style = MaterialTheme.typography.labelMedium)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(trip.photos) { photo ->
                val isSelected = selected.contains(photo)
                AsyncImage(
                    model = photo.uri,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(2.dp)
                        .aspectRatio(1f)
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = Color(0xFF4CAF50)
                        )
                        .clickable {
                            if (isSelected) {
                                selected.remove(photo)
                            } else if (selected.size < 2) {
                                selected.add(photo)
                            }
                        }
                )
            }
        }

        Button(
            onClick = { onReferenceChosen(selected.toList()) },
            enabled = selected.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text("Edit reference photo(s) (${selected.size}/2) →")
        }
    }
}
