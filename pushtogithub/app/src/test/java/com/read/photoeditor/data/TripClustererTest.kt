package com.read.photoeditor.data

import com.read.photoeditor.data.model.Photo
import org.junit.Assert.*
import org.junit.Test

class TripClustererTest {

    private val clusterer = TripClusterer(maxGapHours = 36, maxLocationDriftKm = 50.0)

    @Test
    fun `cluster empty list returns empty`() {
        val result = clusterer.cluster(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `cluster single photo creates single trip`() {
        val photo = Photo(id = 1L, uri = "content://media/1", takenAtMillis = 1000L)
        val result = clusterer.cluster(listOf(photo))

        assertEquals(1, result.size)
        assertEquals(1, result[0].photos.size)
        assertEquals(1000L, result[0].startMillis)
        assertEquals(1000L, result[0].endMillis)
    }

    @Test
    fun `cluster photos within 36 hours groups into same trip`() {
        val baseTime = 1717200000000L // Jun 1, 2024
        val photos = listOf(
            Photo(id = 1L, uri = "content://media/1", takenAtMillis = baseTime),
            Photo(id = 2L, uri = "content://media/2", takenAtMillis = baseTime + (10 * 3600 * 1000L)), // +10h
            Photo(id = 3L, uri = "content://media/3", takenAtMillis = baseTime + (24 * 3600 * 1000L))  // +24h
        )

        val result = clusterer.cluster(photos)
        assertEquals(1, result.size)
        assertEquals(3, result[0].photos.size)
    }

    @Test
    fun `cluster photos with gap exceeding 36 hours splits into separate trips`() {
        val baseTime = 1717200000000L // Jun 1, 2024
        val photos = listOf(
            Photo(id = 1L, uri = "content://media/1", takenAtMillis = baseTime),
            Photo(id = 2L, uri = "content://media/2", takenAtMillis = baseTime + (10 * 3600 * 1000L)),
            // 48h gap -> should trigger new trip
            Photo(id = 3L, uri = "content://media/3", takenAtMillis = baseTime + (60 * 3600 * 1000L))
        )

        val result = clusterer.cluster(photos)
        assertEquals(2, result.size)
        assertEquals(2, result[0].photos.size)
        assertEquals(1, result[1].photos.size)
    }

    @Test
    fun `cluster automatically excludes screenshots`() {
        val baseTime = 1717200000000L
        val photos = listOf(
            Photo(id = 1L, uri = "content://media/1", takenAtMillis = baseTime),
            Photo(id = 2L, uri = "content://media/2", takenAtMillis = baseTime + 1000L, isScreenshot = true),
            Photo(id = 3L, uri = "content://media/3", takenAtMillis = baseTime + 2000L)
        )

        val result = clusterer.cluster(photos)
        assertEquals(1, result.size)
        assertEquals(2, result[0].photos.size)
        assertFalse(result[0].photos.any { it.isScreenshot })
    }

    @Test
    fun `haversine distance calculates accurate geographic distance`() {
        // Distance between Mumbai (19.0760, 72.8777) and Pune (18.5204, 73.8567) is ~120km
        val distKm = clusterer.haversineKm(19.0760, 72.8777, 18.5204, 73.8567)
        assertTrue("Distance should be approximately 120km, was $distKm", distKm in 115.0..125.0)

        // Same point distance should be 0.0
        val zeroDist = clusterer.haversineKm(19.0760, 72.8777, 19.0760, 72.8777)
        assertEquals(0.0, zeroDist, 0.001)
    }

    @Test
    fun `cluster splits photos by geographic drift even within time window`() {
        val baseTime = 1717200000000L
        val photos = listOf(
            // Mumbai photo
            Photo(id = 1L, uri = "content://media/1", takenAtMillis = baseTime, latitude = 19.0760, longitude = 72.8777),
            // Pune photo 2 hours later (>50km drift)
            Photo(id = 2L, uri = "content://media/2", takenAtMillis = baseTime + (2 * 3600 * 1000L), latitude = 18.5204, longitude = 73.8567)
        )

        val result = clusterer.cluster(photos)
        assertEquals(2, result.size)
        assertEquals(1, result[0].photos.size)
        assertEquals(1, result[1].photos.size)
    }
}
