package com.read.photoeditor.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Configuration activity launched automatically when placing the widget
 * or when tapping the widget's corner change-photo icon.
 * Uses Android's system Photo Picker (ActivityResultContracts.PickVisualMedia)
 * and takes persistable URI permissions so the widget keeps reading the image long-term.
 */
class PhotoWidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // Persist read permission across reboots and process deaths
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // If persistable permission is not grantable on this URI, continue saving
            }

            // Save URI mapping for this specific widget instance
            PhotoWidgetStore.saveUri(this, appWidgetId, uri)

            // Update Glance AppWidget state
            lifecycleScope.launch {
                try {
                    val glanceId = GlanceAppWidgetManager(this@PhotoWidgetConfigureActivity)
                        .getGlanceIdBy(appWidgetId)
                    updateAppWidgetState(
                        this@PhotoWidgetConfigureActivity,
                        PreferencesGlanceStateDefinition,
                        glanceId
                    ) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[PhotoWidgetKeys.PHOTO_URI_KEY] = uri.toString()
                            this[PhotoWidgetKeys.APP_WIDGET_ID_KEY] = appWidgetId
                        }
                    }
                    PhotoWidget().update(this@PhotoWidgetConfigureActivity, glanceId)
                } catch (_: Exception) {
                    // Fallback to PhotoWidgetStore
                }

                // Return RESULT_OK with widget ID to finalize widget placement
                val resultValue = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                setResult(RESULT_OK, resultValue)
                finish()
            }
        } else {
            // User dismissed picker without selecting a photo
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set RESULT_CANCELED by default so Android deletes unconfigured widget if user backs out
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Launch system Photo Picker immediately
        pickPhotoLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
}
