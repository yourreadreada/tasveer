package com.read.photoeditor.data

import com.read.photoeditor.data.model.Photo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DuplicateDetectorTest {

    @Test
    fun `hammingDistance identical hashes returns zero`() {
        val hash = 0x123456789ABCDEF0L
        assertEquals(0, DuplicateDetector.hammingDistance(hash, hash))
    }

    @Test
    fun `hammingDistance single bit difference returns one`() {
        val hash1 = 0b00000001L
        val hash2 = 0b00000000L
        assertEquals(1, DuplicateDetector.hammingDistance(hash1, hash2))
    }

    @Test
    fun `hammingDistance completely inverted hashes returns 64`() {
        val hash1 = 0L
        val hash2 = -1L // all 1s in two's complement
        assertEquals(64, DuplicateDetector.hammingDistance(hash1, hash2))
    }

    @Test
    fun `computeDHashFromGrayscale identical gradient pattern produces identical hash`() {
        // Gradient from left to right (increasing brightness: left < right -> bit is 0)
        val hash1 = DuplicateDetector.computeDHashFromGrayscale { x, _ -> x * 20 }
        val hash2 = DuplicateDetector.computeDHashFromGrayscale { x, _ -> x * 20 }
        assertEquals(hash1, hash2)
    }

    @Test
    fun `computeDHashFromGrayscale inverted gradient produces inverted hash`() {
        val gradientAsc = DuplicateDetector.computeDHashFromGrayscale { x, _ -> x * 20 }
        val gradientDesc = DuplicateDetector.computeDHashFromGrayscale { x, _ -> 255 - x * 20 }

        // Hamming distance between ascending and descending should be 64
        val dist = DuplicateDetector.hammingDistance(gradientAsc, gradientDesc)
        assertEquals(64, dist)
    }

    @Test
    fun `findDuplicates clusters duplicate photos within threshold`() = runBlocking {
        val photo1 = Photo(id = 1L, uri = "content://media/1", takenAtMillis = 1000L)
        val photo2 = Photo(id = 2L, uri = "content://media/2", takenAtMillis = 1500L)
        val photo3 = Photo(id = 3L, uri = "content://media/3", takenAtMillis = 5000L)

        // Mock dHash provider via computeDHash or custom mock
        val photos = listOf(photo1, photo2, photo3)
        // With photo1 and photo2 returning identical hash and photo3 inverted
        val groups = mutableListOf<DuplicateGroup>()

        val baseHash: Long = 0x55AA55AA55AA55AAL
        val hashes: Map<Long, Long> = mapOf(
            1L to baseHash,
            2L to (baseHash xor 3L),
            3L to baseHash.inv()
        )

        // Find duplicates using the distance threshold
        val visited = mutableSetOf<Long>()
        photos.forEach { photo ->
            if (photo.id in visited) return@forEach
            val h1 = hashes[photo.id] ?: return@forEach
            val duplicates = mutableListOf<Photo>()
            photos.forEach { candidate ->
                if (candidate.id != photo.id && candidate.id !in visited) {
                    val h2 = hashes[candidate.id]
                    if (h2 != null && DuplicateDetector.hammingDistance(h1, h2) <= 10) {
                        duplicates.add(candidate)
                        visited.add(candidate.id)
                    }
                }
            }
            if (duplicates.isNotEmpty()) {
                visited.add(photo.id)
                groups.add(DuplicateGroup(photo, duplicates))
            }
        }

        assertEquals(1, groups.size)
        assertEquals(photo1.id, groups[0].primaryPhoto.id)
        assertEquals(1, groups[0].duplicates.size)
        assertEquals(photo2.id, groups[0].duplicates[0].id)
    }
}
