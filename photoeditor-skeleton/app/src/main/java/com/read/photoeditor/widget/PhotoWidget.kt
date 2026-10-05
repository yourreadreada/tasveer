package com.read.photoeditor.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.read.photoeditor.MainActivity
import com.read.photoeditor.R

/**
 * Full-bleed home-screen photo widget powered by Jetpack Glance.
 * - Displays a single user-chosen photo filling whatever widget size the user places (2x2, 4x2, etc.)
 * - Tapping main area opens the app.
 * - Subtle corner icon allows changing the photo for this specific widget instance.
 */
class PhotoWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetManager = GlanceAppWidgetManager(context)
        val appWidgetId = try {
            appWidgetManager.getAppWidgetId(id)
        } catch (_: Exception) {
            AppWidgetManager.INVALID_APPWIDGET_ID
        }

        provideContent {
            val prefs = currentState<Preferences>()
            val uriStringFromPrefs = prefs[PhotoWidgetKeys.PHOTO_URI_KEY]
            val resolvedUri = if (!uriStringFromPrefs.isNullOrBlank()) {
                Uri.parse(uriStringFromPrefs)
            } else if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                PhotoWidgetStore.getUri(context, appWidgetId)
            } else {
                null
            }

            val bitmap = resolvedUri?.let { uri ->
                PhotoWidgetStore.loadDownsampledBitmap(context, uri)
            }

            // Tapping main area opens the main app
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            // Subtle corner tap target opens the photo picker to change this widget's photo
            val changePhotoIntent = Intent(context, PhotoWidgetConfigureActivity::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(16.dp)
                    .clickable(actionStartActivity(openAppIntent)),
                contentAlignment = Alignment.TopEnd
            ) {
                if (bitmap != null) {
                    Image(
                        provider = ImageProvider(bitmap),
                        contentDescription = "Custom Photo Widget",
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .background(ColorProvider(Color(0xFF1E1E1E))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tap to choose photo",
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 14.sp
                            )
                        )
                    }
                }

                // Subtle corner tap target to change photo
                Box(
                    modifier = GlanceModifier
                        .padding(8.dp)
                        .size(32.dp)
                        .background(ColorProvider(Color(0x99000000)))
                        .cornerRadius(16.dp)
                        .clickable(actionStartActivity(changePhotoIntent)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_edit),
                        contentDescription = "Change photo",
                        modifier = GlanceModifier.size(18.dp)
                    )
                }
            }
        }
    }
}
