package com.read.photoeditor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.read.photoeditor.data.model.Photo
import com.read.photoeditor.data.model.Trip
import com.read.photoeditor.ui.theme.*

/**
 * Displays all photos in the selected folder or trip.
 * The user selects 1-2 photos here to be the "reference edit" for calibration.
 * Also provides access to Clean Up (duplicates & blurry photos).
 */
@Composable
fun TripScreen(
    trip: Trip,
    onBack: () -> Unit = {},
    onReferenceChosen: (List<Photo>) -> Unit,
    onNavigateToCleanup: () -> Unit
) {
    val selected = remember { mutableStateListOf<Photo>() }

    Scaffold(
        containerColor = Stone950,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Stone900)
                    .border(BorderStroke(1.dp, Stone800))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Stone300
                            )
                        }

                        Column {
                            Text(
                                text = trip.locationName ?: "Selected Folder",
                                color = Stone100,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${trip.photos.size} photos • Select 1–2 references",
                                color = Stone400,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Clean Up button
                    OutlinedButton(
                        onClick = onNavigateToCleanup,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber400),
                        border = BorderStroke(1.dp, Amber500.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Clean Up", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
            // Helper instruction banner
            Surface(
                color = Stone900,
                border = BorderStroke(1.dp, Stone800),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (selected.isEmpty())
                            "Tap 1 or 2 photos below to set your reference aesthetic look."
                        else
                            "${selected.size} of 2 reference photos selected. Tap button below to calibrate.",
                        color = if (selected.isEmpty()) Stone400 else Amber300,
                        fontSize = 12.sp
                    )
                }
            }

            // Photo Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(trip.photos) { photo ->
                    val isSelected = selected.contains(photo)
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Stone900)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) Amber500 else Stone800,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                if (isSelected) {
                                    selected.remove(photo)
                                } else if (selected.size < 2) {
                                    selected.add(photo)
                                }
                            }
                    ) {
                        AsyncImage(
                            model = photo.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Selection Checkmark Badge
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Amber500),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Stone950,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Calibration Action Button
            Surface(
                color = Stone900,
                border = BorderStroke(1.dp, Stone800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { onReferenceChosen(selected.toList()) },
                    enabled = selected.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Amber500,
                        contentColor = Stone950,
                        disabledContainerColor = Stone800,
                        disabledContentColor = Stone500
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                        .height(48.dp)
                ) {
                    Text(
                        text = if (selected.isEmpty())
                            "Select reference photo(s) to continue"
                        else
                            "Edit reference photo(s) (${selected.size}/2) →",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
