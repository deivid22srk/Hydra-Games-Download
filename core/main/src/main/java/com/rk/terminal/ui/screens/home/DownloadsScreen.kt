package com.rk.terminal.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.components.compose.preferences.base.PreferenceLayoutLazyColumn

data class DownloadProgress(
    val id: String,
    val title: String,
    val progress: Float,
    val status: String,
    val isCompleted: Boolean = false
)

// Global state for demonstration. In a real app, use a persistent Service or Database.
val activeDownloads = mutableStateListOf<DownloadProgress>()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen() {
    PreferenceLayoutLazyColumn(label = "Downloads Ativos", backArrowVisible = false) {
        if (activeDownloads.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Nenhum download em andamento",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(activeDownloads) { download ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (download.isCompleted) Icons.Default.DownloadDone else Icons.Default.Download,
                                contentDescription = null,
                                tint = if (download.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = download.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (!download.isCompleted) {
                            LinearProgressIndicator(
                                progress = { download.progress },
                                modifier = Modifier.fillMaxWidth(),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = download.status, style = MaterialTheme.typography.bodySmall)
                            if (!download.isCompleted) {
                                Text(
                                    text = "${(download.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
