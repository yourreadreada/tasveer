package com.read.photoeditor.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure storage manager for Gemini API key using Android EncryptedSharedPreferences.
 */
object ApiKeyStorage {
    private const val PREFS_FILE = "secure_tasveer_prefs"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"

    fun getEncryptedPrefs(context: Context) = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    fun getApiKey(context: Context): String {
        return getEncryptedPrefs(context).getString(KEY_GEMINI_API_KEY, "") ?: ""
    }

    fun saveApiKey(context: Context, key: String) {
        getEncryptedPrefs(context).edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToDatasetProgress: () -> Unit
) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(ApiKeyStorage.getApiKey(context)) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Google Gemini API Key",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Used once per trip calibration for semantic style analysis and for single-photo corrections. Stored securely on-device using EncryptedSharedPreferences.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    saveStatus = null
                },
                label = { Text("Gemini API Key") },
                placeholder = { Text("AIzaSy...") },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isPasswordVisible) "Hide key" else "Show key"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            saveStatus?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    ApiKeyStorage.saveApiKey(context, apiKey)
                    saveStatus = if (apiKey.isBlank()) "API Key cleared" else "API Key saved securely!"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Key")
            }

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Dataset Collection",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Review how many edit logs have been recorded for future on-device model training.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            OutlinedButton(
                onClick = onNavigateToDatasetProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Dataset Progress")
            }
        }
    }
}
