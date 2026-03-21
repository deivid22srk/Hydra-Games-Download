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

data class DownloadProgress(
    val id: String,
    val gid: String? = null,
    val title: String,
    val progress: Float,
    val status: String,
    val speed: String = "",
    val totalSize: String = "",
    val isCompleted: Boolean = false,
    val isPaused: Boolean = false,
    val filePath: String? = null
)

val activeDownloads = mutableStateListOf<DownloadProgress>()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen() {
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf<DownloadProgress?>(null) }
    var deleteFilesFromStorage by remember { mutableStateOf(false) }

    // Polling Aria2 status
    LaunchedEffect(Unit) {
        val client = OkHttpClient()
        val gson = Gson()
        while (true) {
            try {
                updateAria2Status(client, gson)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            delay(2000)
        }
    }

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
                                            resumeDownload(targetGid)
                                            updateLocalStatus(download.id, isPaused = false, status = "Retomando...")
                                        } else {
                                            pauseDownload(targetGid)
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
                                removeDownloadResult(download.gid)
                            } else {
                                removeDownload(download.gid)
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

                        activeDownloads.removeIf { it.id == download.id }
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
    val index = activeDownloads.indexOfFirst { it.id == id }
    if (index != -1) {
        activeDownloads[index] = activeDownloads[index].copy(isPaused = isPaused, status = status)
    }
}

private suspend fun updateAria2Status(client: OkHttpClient, gson: Gson) {
    val rpcUrl = "http://localhost:${Settings.aria2RpcPort}/jsonrpc"
    val secret = Settings.aria2RpcSecret

    suspend fun callMethod(method: String, extraParams: List<Any> = emptyList()): List<Map<String, Any>>? {
        val params = mutableListOf<Any>()
        if (secret.isNotBlank()) params.add("token:$secret")
        params.addAll(extraParams)

        val requestBody = gson.toJson(mapOf(
            "jsonrpc" to "2.0",
            "id" to "q",
            "method" to method,
            "params" to params
        )).toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder().url(rpcUrl).post(requestBody).build()
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        val map = gson.fromJson(body, Map::class.java)
                        val result = map["result"]
                        if (result is List<*>) {
                            result.filterIsInstance<Map<String, Any>>()
                        } else null
                    } else null
                }
            }.getOrNull()
        }
    }

    val active = callMethod("aria2.tellActive") ?: emptyList()
    val waiting = callMethod("aria2.tellWaiting", listOf(0, 100)) ?: emptyList()
    val stopped = callMethod("aria2.tellStopped", listOf(0, 100)) ?: emptyList()

    val allTasks = active + waiting + stopped

    withContext(Dispatchers.Main) {
        // Build a set of GIDs returned by RPC
        val rpcGids = allTasks.mapNotNull { it["gid"] as? String }.toSet()

        // Remove items from activeDownloads that are no longer in Aria2, not completed AND not paused/waiting
        // This ensures items cleared from Aria2 (but not completed) disappear from UI, 
        // while allowing paused ones to remain even during status transitions.
        activeDownloads.removeIf { it.gid != null && it.gid !in rpcGids && !it.isCompleted && !it.isPaused }

        allTasks.forEach { res ->
            val gid = res["gid"] as? String ?: return@forEach
            val statusAttr = res["status"] as? String ?: ""
            val completedLen = (res["completedLength"] as? String)?.toLongOrNull() ?: 0L
            val totalLen = (res["totalLength"] as? String)?.toLongOrNull() ?: 0L
            val speed = (res["downloadSpeed"] as? String)?.toLongOrNull() ?: 0L

            val files = res["files"] as? List<Map<String, Any>>
            val fileInfo = files?.firstOrNull()
            val fullPath = fileInfo?.get("path") as? String
            val fileName = fileInfo?.let {
                val path = it["path"] as? String
                if (path.isNullOrEmpty()) {
                    val uris = it["uris"] as? List<Map<String, Any>>
                    uris?.firstOrNull()?.let { (it["uri"] as? String)?.split("/")?.last()?.split("?")?.first() }
                } else path.split("/").last()
            } ?: "Download Aria2"

            val progress = if (totalLen > 0) completedLen.toFloat() / totalLen else 0f
            val speedStr = formatSpeed(speed)
            val sizeStr = formatSize(totalLen)

            val isPaused = statusAttr == "paused" || statusAttr == "waiting"
            val isCompleted = statusAttr == "complete"

            val existingIndex = activeDownloads.indexOfFirst { (it.gid != null && it.gid == gid) || it.id == gid }
            if (existingIndex != -1) {
                val current = activeDownloads[existingIndex]
                activeDownloads[existingIndex] = current.copy(
                    id = if (current.id.startsWith("http")) gid else current.id, // Update temp ID to real GID
                    gid = gid,
                    title = if (current.title == "Download do Navegador" || current.title == "Download Aria2") fileName else current.title,
                    progress = progress,
                    status = when(statusAttr) {
                        "active" -> "Baixando..."
                        "paused" -> "Pausado"
                        "waiting" -> "Na fila"
                        "complete" -> "Download concluído"
                        "error" -> "Erro no download"
                        else -> statusAttr
                    },
                    speed = speedStr,
                    totalSize = sizeStr,
                    isPaused = isPaused,
                    isCompleted = isCompleted,
                    filePath = fullPath
                )
            } else {
                // Persistent: items found in Aria2 but not in our list (e.g. after restart)
                activeDownloads.add(
                    DownloadProgress(
                        id = gid,
                        gid = gid,
                        title = fileName,
                        progress = progress,
                        status = if (isPaused) "Pausado" else if (isCompleted) "Concluído" else "Adicionado",
                        speed = speedStr,
                        totalSize = sizeStr,
                        isPaused = isPaused,
                        isCompleted = isCompleted,
                        filePath = fullPath
                    )
                )
            }
        }
    }
}

private fun formatSpeed(speedBytes: Long): String {
    if (speedBytes <= 0) return ""
    if (speedBytes < 1024) return "$speedBytes B/s"
    val kb = speedBytes / 1024
    if (kb < 1024) return "$kb KB/s"
    val mb = kb.toFloat() / 1024
    return "%.1f MB/s".format(mb)
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024
    if (kb < 1024) return "$kb KB"
    val mb = kb.toFloat() / 1024
    return "%.1f MB".format(mb)
}

fun pauseDownload(gid: String) {
    callAria2Method("aria2.pause", listOf(gid))
}

fun resumeDownload(gid: String) {
    callAria2Method("aria2.unpause", listOf(gid))
}

fun removeDownload(gid: String) {
    callAria2Method("aria2.remove", listOf(gid))
}

fun removeDownloadResult(gid: String) {
    callAria2Method("aria2.removeDownloadResult", listOf(gid))
}

private fun callAria2Method(method: String, params: List<Any>) {
    val client = OkHttpClient()
    val gson = Gson()
    val rpcUrl = "http://localhost:${Settings.aria2RpcPort}/jsonrpc"
    val secret = Settings.aria2RpcSecret

    val rpcParams = mutableListOf<Any>()
    if (secret.isNotBlank()) rpcParams.add("token:$secret")
    rpcParams.addAll(params)

    val requestBody = gson.toJson(mapOf(
        "jsonrpc" to "2.0",
        "id" to "ctrl",
        "method" to method,
        "params" to rpcParams
    )).toRequestBody("application/json".toMediaTypeOrNull())

    val request = Request.Builder().url(rpcUrl).post(requestBody).build()

    client.newCall(request).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
    })
}
