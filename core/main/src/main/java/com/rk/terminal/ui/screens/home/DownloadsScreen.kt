package com.rk.terminal.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.components.compose.preferences.base.PreferenceLayoutLazyColumn
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen() {
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf<DownloadProgress?>(null) }
    var deleteFilesFromStorage by remember { mutableStateOf(false) }

    PreferenceLayoutLazyColumn(label = "Downloads Ativos", backArrowVisible = false) {
        val activeDownloads = DownloadManager.activeDownloads
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
            items(activeDownloads, key = { it.id }) { download ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when {
                                    download.isCompleted -> Icons.Default.DownloadDone
                                    download.isPaused -> Icons.Default.PlayArrow
                                    else -> Icons.Default.Download
                                },
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

                            if (!download.isCompleted && download.gid != null) {
                                Row {
                                    IconButton(onClick = {
                                        val targetGid = download.gid
                                        if (download.isPaused) {
                                            DownloadManager.resumeDownload(targetGid)
                                            updateLocalStatus(download.id, isPaused = false, status = "Retomando...")
                                        } else {
                                            DownloadManager.pauseDownload(targetGid)
                                            updateLocalStatus(download.id, isPaused = true, status = "Pausando...")
                                        }
                                    }) {
                                        Icon(if (download.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
                                    }
                                    IconButton(onClick = {
                                        showDeleteDialog = download
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                    }
                                }
                            } else if (download.isCompleted || download.gid == null) {
                                IconButton(onClick = {
                                    showDeleteDialog = download
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            }
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
                            Column {
                                Text(text = download.status, style = MaterialTheme.typography.bodySmall)
                                if (download.speed.isNotBlank() && !download.isPaused) {
                                    Text(text = "Velocidade: ${download.speed}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (!download.isCompleted) {
                                    Text(
                                        text = "${(download.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (download.totalSize.isNotBlank()) {
                                    Text(text = download.totalSize, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Excluir Download") },
            text = {
                Column {
                    Text("Deseja realmente excluir '${showDeleteDialog?.title}'?")
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { deleteFilesFromStorage = !deleteFilesFromStorage }
                    ) {
                        Checkbox(
                            checked = deleteFilesFromStorage,
                            onCheckedChange = { deleteFilesFromStorage = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Excluir arquivos do armazenamento")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val download = showDeleteDialog!!
                        if (download.gid != null) {
                            if (download.isCompleted) {
                                DownloadManager.removeDownloadResult(download.gid)
                            } else {
                                DownloadManager.removeDownload(download.gid)
                            }
                        }

                        if (deleteFilesFromStorage && download.filePath != null) {
                            val file = java.io.File(download.filePath)
                            if (file.exists()) {
                                if (file.isDirectory) {
                                    file.deleteRecursively()
                                } else {
                                    file.delete()
                                }
                            }
                        }

                        DownloadManager.activeDownloads.removeIf { it.id == download.id }
                        showDeleteDialog = null
                        deleteFilesFromStorage = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("EXCLUIR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("CANCELAR")
                }
            }
        )
    }
}

private fun updateLocalStatus(id: String, isPaused: Boolean, status: String) {
    val index = DownloadManager.activeDownloads.indexOfFirst { it.id == id }
    if (index != -1) {
        DownloadManager.activeDownloads[index] = DownloadManager.activeDownloads[index].copy(isPaused = isPaused, status = status)
    }
}
