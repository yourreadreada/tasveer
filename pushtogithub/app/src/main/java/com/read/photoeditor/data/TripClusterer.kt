package com.read.photoeditor.data

import android.content.Context
import android.location.Geocoder
import com.read.photoeditor.data.model.Photo
import com.read.photoeditor.data.model.Trip
import java.util.Locale

/**
 * Groups photos into "trips" automatically:
 * - Excludes screenshots / documents from clustering (Feature 4).
 * - Clusters primarily by date gaps, refined by location drift when GPS is available.
 * - Reverse-geocodes average coordinates to a real place name via Android's Geocoder (Feature 7).
 */
class TripClusterer(
    val maxGapHours: Long = 36,       // a gap bigger than this starts a new trip
    val maxLocationDriftKm: Double = 50.0 // bigger jump than this also starts a new trip
) {

    fun cluster(photos: List<Photo>, context: Context? = null): List<Trip> {
        // Feature 4: Exclude screenshots from trip clustering
        val eligiblePhotos = photos.filter { !it.isScreenshot }
        if (eligiblePhotos.isEmpty()) return emptyList()

        val sorted = eligiblePhotos.sortedBy { it.takenAtMillis }
        val trips = mutableListOf<MutableList<Photo>>(mutableListOf(sorted.first()))

        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]

            val gapHours = (curr.takenAtMillis - prev.takenAtMillis) / 3_600_000.0
            val tooFarInTime = gapHours > maxGapHours

            val tooFarInSpace = if (
                prev.latitude != null && prev.longitude != null &&
                curr.latitude != null && curr.longitude != null
            ) {
                haversineKm(prev.latitude, prev.longitude, curr.latitude, curr.longitude) > maxLocationDriftKm
            } else {
                false // no GPS on one or both — fall back to date-only judgment
            }

            if (tooFarInTime || tooFarInSpace) {
                trips.add(mutableListOf(curr))
            } else {
                trips.last().add(curr)
            }
        }

        return trips.mapIndexed { index, group ->
            // Feature 7: Reverse-geocode average coordinates to real place name
            var placeName: String? = null
            val gpsPhotos = group.filter { it.latitude != null && it.longitude != null }
            if (gpsPhotos.isNotEmpty() && context != null && Geocoder.isPresent()) {
                try {
                    val avgLat = gpsPhotos.map { it.latitude!! }.average()
                    val avgLng = gpsPhotos.map { it.longitude!! }.average()
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(avgLat, avgLng, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        placeName = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: addr.countryName
                    }
                } catch (e: Exception) {
                    // Fall back to null gracefully
                }
            }

            Trip(
                id = "${placeName ?: "trip"}_${index + 1}",
                photos = group,
                startMillis = group.minOf { it.takenAtMillis },
                endMillis = group.maxOf { it.takenAtMillis },
                locationName = placeName
            )
        }
    }

    internal fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
