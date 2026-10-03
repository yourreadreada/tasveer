package com.read.photoeditor.data

import com.read.photoeditor.data.model.Photo
import com.read.photoeditor.data.model.Trip
import kotlin.math.abs

/**
 * Groups photos into "trips" automatically, per the design we settled on:
 * primarily by date gaps, refined by location when GPS is available,
 * falling back gracefully to date-only clustering when it isn't.
 */
class TripClusterer(
    private val maxGapHours: Long = 18,       // a gap bigger than this starts a new trip
    private val maxLocationDriftKm: Double = 50.0 // bigger jump than this also starts a new trip
) {

    fun cluster(photos: List<Photo>): List<Trip> {
        if (photos.isEmpty()) return emptyList()

        val sorted = photos.sortedBy { it.takenAtMillis }
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
            Trip(
                id = "trip_$index",
                photos = group,
                startMillis = group.minOf { it.takenAtMillis },
                endMillis = group.maxOf { it.takenAtMillis }
            )
        }
        // TODO: small trips (1-2 photos) are probably not "trips" at all — consider a
        // minimum photo-count threshold before showing a cluster as a trip in the UI.
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
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
