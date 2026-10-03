package com.read.photoeditor.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.read.photoeditor.data.model.Photo

/**
 * Pulls photos straight from the phone's MediaStore and enriches them with
 * EXIF metadata (GPS coordinates) and screenshot classification.
 */
class PhotoRepository(private val context: Context) {

    fun loadAllPhotos(): List<Photo> {
        val photos = mutableListOf<Photo>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

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

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val takenAt = cursor.getLong(dateCol)
                val bucket = cursor.getString(bucketCol) ?: ""
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )

                // Feature 4: Screenshot auto-sorting detection
                val isScreenshot = bucket.contains("screenshot", ignoreCase = true) ||
                        bucket.contains("screen_shot", ignoreCase = true) ||
                        bucket.contains("captures", ignoreCase = true)

                // Feature 7: Read real GPS coordinates from EXIF
                var lat: Double? = null
                var lng: Double? = null
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val exif = ExifInterface(stream)
                        val latLong = FloatArray(2)
                        if (exif.getLatLong(latLong)) {
                            lat = latLong[0].toDouble()
                            lng = latLong[1].toDouble()
                        }
                    }
                } catch (_: Exception) {
                    // Ignored: photo has no EXIF or stream failed
                }

                photos.add(
                    Photo(
                        id = id,
                        uri = uri.toString(),
                        takenAtMillis = takenAt,
                        latitude = lat,
                        longitude = lng,
                        isFrontCamera = bucket.contains("selfie", ignoreCase = true) ||
                                bucket.contains("front", ignoreCase = true),
                        isScreenshot = isScreenshot
                    )
                )
            }
        }
        return photos
    }
}
