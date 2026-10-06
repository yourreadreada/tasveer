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
        val pixels = IntArray(9 * 8)
        scaled.getPixels(pixels, 0, 9, 0, 0, 9, 8)

        if (scaled != bitmap) {
            scaled.recycle()
        }

        return computeDHashFromGrayscale(9, 8) { x, y ->
            val p = pixels[y * 9 + x]
            (0.299 * ((p shr 16) and 0xff) + 0.587 * ((p shr 8) and 0xff) + 0.114 * (p and 0xff)).toInt()
        }
    }

    /**
     * Pure 64-bit difference hash computation from luminance coordinates.
     * Can be tested without Android Bitmap mocks.
     */
    fun computeDHashFromGrayscale(width: Int = 9, height: Int = 8, getLuma: (x: Int, y: Int) -> Int): Long {
        var hash = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val leftLum = getLuma(x, y)
                val rightLum = getLuma(x + 1, y)

                if (leftLum > rightLum) {
                    hash = hash or (1L shl (y * 8 + x))
                }
            }
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
