package com.read.photoeditor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.read.photoeditor.data.model.Trip

/**
 * Top-level screen: auto-clustered trips, newest first. Tapping a trip opens TripScreen.
 * Deliberately plain/clean — this is the "Google Photos" half of the vision.
 */
@Composable
fun GalleryScreen(trips: List<Trip>, onTripClick: (Trip) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(trips) { trip ->
            Column(modifier = Modifier.clickable { onTripClick(trip) }) {
                AsyncImage(
                    model = trip.photos.firstOrNull()?.uri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
                Text("${trip.photos.size} photos")
                // TODO: format startMillis/endMillis into a readable date range,
                // and eventually a reverse-geocoded place name once location is wired up.
            }
        }
    }
}
