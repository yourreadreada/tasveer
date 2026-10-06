package com.read.photoeditor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.read.photoeditor.data.EditLogRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetProgressScreen(
    editLogRepository: EditLogRepository,
    onBack: () -> Unit
) {
    var totalEdits by remember { mutableIntStateOf(0) }
    var correctedEdits by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    val targetEdits = 100

    LaunchedEffect(Unit) {
        val (total, corrected) = editLogRepository.getStats()
        totalEdits = total
        correctedEdits = corrected
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personalized Model Dataset") },
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
                .padding(20.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Path B: Personalized Model",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Every time you calibrate a trip and correct individual photos, Tasveer logs the inputs and outputs. Once you hit 100 accepted edits, this dataset can train a lightweight, on-device model customized to your personal aesthetic.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    val progress = (totalEdits.toFloat() / targetEdits).coerceIn(0f, 1f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$totalEdits / $targetEdits edits logged",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Dataset Breakdown", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            ListItem(
                headlineContent = { Text("Total Logged Edits") },
                supportingContent = { Text("Accepted batch edits matching your style") },
                trailingContent = { Text("$totalEdits", style = MaterialTheme.typography.titleLarge) }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Correction Cycles") },
                supportingContent = { Text("Highest-value training samples where you flagged and re-tuned") },
                trailingContent = {
                    Text(
                        "$correctedEdits",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )
            HorizontalDivider()
        }
    }
}
