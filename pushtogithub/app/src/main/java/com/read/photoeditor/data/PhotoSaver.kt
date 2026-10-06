package com.read.photoeditor.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Saves edited photos to Android's MediaStore as new image copies in the "Tasveer Edited" album.
 * Never overwrites the original photos.
 */
object PhotoSaver {

    /**
     * Saves a Bitmap to MediaStore under "Pictures/Tasveer Edited" as a new copy.
     * Returns the Uri of the newly created image, or null on failure.
     */
    suspend fun saveBitmapToMediaStore(
        context: Context,
        bitmap: Bitmap,
        filenamePrefix: String = "tasveer_${System.currentTimeMillis()}"
    ): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val filename = "${filenamePrefix}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Tasveer Edited")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val imageUri = resolver.insert(collection, contentValues) ?: return@withContext null

        try {
            resolver.openOutputStream(imageUri)?.use { stream: OutputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            imageUri
        } catch (e: Exception) {
            try {
                resolver.delete(imageUri, null, null)
            } catch (e: Exception) {}
            null
        }
    }
}
