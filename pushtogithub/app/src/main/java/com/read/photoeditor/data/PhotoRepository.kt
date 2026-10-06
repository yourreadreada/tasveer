package com.read.photoeditor.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.read.photoeditor.data.model.Photo
import com.read.photoeditor.data.model.Trip

import android.os.Build
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Pulls photos straight from the phone's MediaStore and enriches them with
 * EXIF metadata (GPS coordinates, timestamps) and screenshot classification.
 * Also provides folder-wise grouping so photos can be viewed by device album (like Google Photos).
 */
class PhotoRepository(private val context: Context) {

    fun loadAllPhotos(): List<Photo> {
        val photos = mutableListOf<Photo>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media._ID} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val dateAddedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
            val dateModifiedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val takenAt = cursor.getLong(dateCol)
                val bucket = cursor.getString(bucketCol) ?: ""
                val displayName = if (nameCol != -1) cursor.getString(nameCol) else null
                val folderName = if (bucket.isNotBlank()) bucket else "Internal Storage"
                val rawUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )

                // Feature 4: Screenshot auto-sorting detection
                val isScreenshot = bucket.contains("screenshot", ignoreCase = true) ||
                        bucket.contains("screen_shot", ignoreCase = true) ||
                        bucket.contains("captures", ignoreCase = true)

                // Feature 7: Read real GPS coordinates & EXIF timestamp
                var lat: Double? = null
                var lng: Double? = null
                var photoTakenAt = takenAt

                val readUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        MediaStore.setRequireOriginal(rawUri)
                    } catch (e: Exception) {
                        rawUri
                    }
                } else {
                    rawUri
                }

                try {
                    context.contentResolver.openInputStream(readUri)?.use { stream ->
                        val exif = ExifInterface(stream)
                        val latLong = FloatArray(2)
                        if (exif.getLatLong(latLong)) {
                            lat = latLong[0].toDouble()
                            lng = latLong[1].toDouble()
                        }
                        if (photoTakenAt <= 0L) {
                            val exifTime = exif.dateTime
                            if (exifTime != null && exifTime > 0L) {
                                photoTakenAt = exifTime
                            } else {
                                val dateOrig = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                                if (!dateOrig.isNullOrBlank()) {
                                    val parsed = parseExifDate(dateOrig)
                                    if (parsed != null) photoTakenAt = parsed
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignored: photo has no EXIF or stream failed
                }

                if (photoTakenAt <= 0L) {
                    val added = if (dateAddedCol != -1) cursor.getLong(dateAddedCol) else 0L
                    val mod = if (dateModifiedCol != -1) cursor.getLong(dateModifiedCol) else 0L
                    photoTakenAt = when {
                        added > 0L -> added * 1000L
                        mod > 0L -> mod * 1000L
                        else -> System.currentTimeMillis()
                    }
                }

                photos.add(
                    Photo(
                        id = id,
                        uri = rawUri.toString(),
                        takenAtMillis = photoTakenAt,
                        latitude = lat,
                        longitude = lng,
                        isFrontCamera = bucket.contains("selfie", ignoreCase = true) ||
                                bucket.contains("front", ignoreCase = true),
                        isScreenshot = isScreenshot,
                        folderName = folderName,
                        title = displayName
                    )
                )
            }
        }
        return photos
    }

    /**
     * Groups photos by their on-device folder (album) name, matching the Google Photos
     * folder-based library organization. Non-screenshot photos in each folder are kept
     * together regardless of timestamp drift.
     */
    fun groupPhotosByFolder(photos: List<Photo>): List<Trip> {
        val nonScreenshots = photos.filter { !it.isScreenshot }
        return nonScreenshots.groupBy { it.folderName.ifBlank { "Uncategorized" } }
            .map { (folderName, folderPhotos) ->
                val sorted = folderPhotos.sortedByDescending { it.takenAtMillis }
                Trip(
                    id = "folder_${folderName.replace(" ", "_")}",
                    photos = sorted,
                    startMillis = sorted.minOfOrNull { it.takenAtMillis } ?: 0L,
                    endMillis = sorted.maxOfOrNull { it.takenAtMillis } ?: 0L,
                    locationName = folderName,
                    title = folderName
                )
            }
            .sortedByDescending { it.photos.size }
    }

    private fun parseExifDate(str: String): Long? {
        return try {
            val format = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            format.parse(str)?.time
        } catch (e: Exception) {
            null
        }
    }
}
