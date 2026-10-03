package com.read.photoeditor.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.read.photoeditor.data.model.Photo
import com.read.photoeditor.data.model.Trip

/**
 * Shows all photos in one trip. The user selects 1-2 photos here to be the
 * "reference edit" — per the preview-first flow they asked for, nothing gets
 * touched until they've defined the look on these first.
 */
@Composable
fun TripScreen(
    trip: Trip,
    onReferenceChosen: (List<Photo>) -> Unit
) {
    val selected = remember { mutableStateListOf<Photo>() }

    Column {
        Text("Select 1-2 photos to define the look for this trip", modifier = Modifier.padding(12.dp))

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
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Text("Edit reference photo(s) →")
        }
    }
}
