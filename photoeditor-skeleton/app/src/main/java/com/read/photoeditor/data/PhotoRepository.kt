package com.read.photoeditor.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.read.photoeditor.data.model.Photo

/**
 * Pulls photos straight from the phone's MediaStore — this is the "local files"
 * access path, separate from Google Photos (whose API can't read your whole library).
 */
class PhotoRepository(private val context: Context) {

    fun loadAllPhotos(): List<Photo> {
        val photos = mutableListOf<Photo>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.LATITUDE,   // deprecated on newer APIs — see TODO below
            MediaStore.Images.Media.LONGITUDE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME // folder name, e.g. "Camera" vs "Selfies"
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

                photos.add(
                    Photo(
                        id = id,
                        uri = uri.toString(),
                        takenAtMillis = takenAt,
                        // TODO: LATITUDE/LONGITUDE columns are deprecated on API 29+.
                        // Switch to reading EXIF directly via ExifInterface(inputStream)
                        // for GPSLatitude/GPSLongitude — more reliable across OEMs.
                        latitude = null,
                        longitude = null,
                        isFrontCamera = bucket.contains("selfie", ignoreCase = true) ||
                            bucket.contains("front", ignoreCase = true)
                        // NOTE: bucket-name guessing is a rough fallback. Better signal is
                        // EXIF's LensFacing tag where present, or comparing image dimensions
                        // against known front/back sensor resolutions for the device.
                    )
                )
            }
        }
        return photos
    }
}
