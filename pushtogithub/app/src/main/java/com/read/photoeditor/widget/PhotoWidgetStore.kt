package com.read.photoeditor.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PhotoWidgetKeys {
    val PHOTO_URI_KEY = stringPreferencesKey("photo_uri")
    val APP_WIDGET_ID_KEY = intPreferencesKey("app_widget_id")
}

/**
 * Per-widget instance persistence helper and safe bitmap decoder for Glance AppWidget.
 */
object PhotoWidgetStore {
    private const val PREFS_NAME = "tasveer_photo_widget_prefs"
    private const val KEY_PREFIX = "widget_photo_uri_"

    fun saveUri(context: Context, appWidgetId: Int, uri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$KEY_PREFIX$appWidgetId", uri.toString())
            .apply()
    }

    fun getUri(context: Context, appWidgetId: Int): Uri? {
        val s = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$KEY_PREFIX$appWidgetId", null)
        return s?.let { Uri.parse(it) }
    }

    /**
     * Decodes and downsamples the image from MediaStore/PhotoPicker URI to prevent
     * TransactionTooLargeException during Binder IPC with the Android Launcher.
     */
    fun loadDownsampledBitmap(context: Context, uri: Uri, maxDimension: Int = 800): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return null
            }

            var inSampleSize = 1
            val maxSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while (maxSide / (inSampleSize * 2) >= maxDimension) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (_: Exception) {
            null
        }
    }
}
