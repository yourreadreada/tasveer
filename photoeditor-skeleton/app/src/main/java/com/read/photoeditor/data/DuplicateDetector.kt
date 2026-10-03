package com.read.photoeditor.data

import android.graphics.Bitmap
import com.read.photoeditor.data.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Represents a cluster of duplicate or burst-shot photos.
 */
data class DuplicateGroup(
    val primaryPhoto: Photo,
    val duplicates: List<Photo>
)

object DuplicateDetector {

    /**
     * Computes a 64-bit difference hash (dHash) on an 9x8 downscaled grayscale representation.
     * Fast, lightweight, and completely offline.
     */
    fun computeDHash(bitmap: Bitmap): Long {
        // Downscale to 9 columns x 8 rows
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var hash = 0L

        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val leftPixel = scaled.getPixel(x, y)
                val rightPixel = scaled.getPixel(x + 1, y)

                // Luminance approximation
                val leftLum = (0.299 * ((leftPixel shr 16) and 0xff) +
                        0.587 * ((leftPixel shr 8) and 0xff) +
                        0.114 * (leftPixel and 0xff)).toInt()

                val rightLum = (0.299 * ((rightPixel shr 16) and 0xff) +
                        0.587 * ((rightPixel shr 8) and 0xff) +
                        0.114 * (rightPixel and 0xff)).toInt()

                if (leftLum > rightLum) {
                    hash = hash or (1L shl (y * 8 + x))
                }
            }
        }

        if (scaled != bitmap) {
            scaled.recycle()
        }

        return hash
    }

    /**
     * Calculates the Hamming distance (number of bit differences) between two dHashes.
     */
    fun hammingDistance(hash1: Long, hash2: Long): Int {
        return java.lang.Long.bitCount(hash1 xor hash2)
    }

    /**
     * Identifies duplicate/burst groups within a list of photos using their computed dHashes.
     * Distance threshold <= 10 flags photos as near-identical duplicates.
     */
    suspend fun findDuplicates(
        photos: List<Photo>,
        loadBitmap: (Photo) -> Bitmap?,
        maxHammingDistance: Int = 10
    ): List<DuplicateGroup> = withContext(Dispatchers.Default) {
        val hashes = mutableMapOf<Long, Long>() // photoId -> dHash

        photos.forEach { photo ->
            val bmp = loadBitmap(photo)
            if (bmp != null) {
                hashes[photo.id] = computeDHash(bmp)
            }
        }

        val visited = mutableSetOf<Long>()
        val groups = mutableListOf<DuplicateGroup>()

        photos.forEach { photo ->
            if (photo.id in visited) return@forEach

            val baseHash = hashes[photo.id] ?: return@forEach
            val matchingPhotos = mutableListOf<Photo>()

            photos.forEach { candidate ->
                if (candidate.id != photo.id && candidate.id !in visited) {
                    val candHash = hashes[candidate.id]
                    if (candHash != null && hammingDistance(baseHash, candHash) <= maxHammingDistance) {
                        matchingPhotos.add(candidate)
                        visited.add(candidate.id)
                    }
                }
            }

            if (matchingPhotos.isNotEmpty()) {
                visited.add(photo.id)
                groups.add(DuplicateGroup(primaryPhoto = photo, duplicates = matchingPhotos))
            }
        }

        groups
    }
}
