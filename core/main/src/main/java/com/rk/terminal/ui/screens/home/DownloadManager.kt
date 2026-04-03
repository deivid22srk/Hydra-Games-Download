package com.rk.terminal.ui.screens.home

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.NotificationCompat
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.rk.resources.drawables

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
    val isError: Boolean = false,
    val filePath: String? = null
)

object DownloadManager {
    val activeDownloads = mutableStateListOf<DownloadProgress>()
    private val client = OkHttpClient()
    private val gson = Gson()
    private var pollingJob: Job? = null
    private const val CHANNEL_ID = "download_channel"

    fun startPolling(context: Context) {
        if (pollingJob != null) return

        createNotificationChannel(context)

        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    updateAria2Status(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(2000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Downloads"
            val descriptionText = "Notifications for active downloads"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private suspend fun updateAria2Status(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
            return runCatching {
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

        val active = callMethod("aria2.tellActive") ?: emptyList()
        val waiting = callMethod("aria2.tellWaiting", listOf(0, 100)) ?: emptyList()
        val stopped = callMethod("aria2.tellStopped", listOf(0, 100)) ?: emptyList()

        val allTasks = active + waiting + stopped

        withContext(Dispatchers.Main) {
            val rpcGids = allTasks.mapNotNull { it["gid"] as? String }.toSet()
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
                        val rawName = uris?.firstOrNull()?.let { (it["uri"] as? String)?.split("/")?.last()?.split("?")?.first() }
                        try {
                            if (rawName != null) java.net.URLDecoder.decode(rawName, "UTF-8") else null
                        } catch (e: Exception) {
                            rawName
                        }
                    } else {
                        val name = path.split("/").last()
                        try {
                            java.net.URLDecoder.decode(name, "UTF-8")
                        } catch (e: Exception) {
                            name
                        }
                    }
                } ?: "Download Aria2"

                val progress = if (totalLen > 0) completedLen.toFloat() / totalLen else 0f
                val speedStr = formatSpeed(speed)
                val sizeStr = formatSize(totalLen)

                val isPaused = statusAttr == "paused" || statusAttr == "waiting"
                val isCompleted = statusAttr == "complete"
                val isError = statusAttr == "error"

                val existingIndex = activeDownloads.indexOfFirst { (it.gid != null && it.gid == gid) || it.id == gid }
                val updatedDownload = if (existingIndex != -1) {
                    val current = activeDownloads[existingIndex]
                    current.copy(
                        id = if (current.id.startsWith("http")) gid else current.id,
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
                        isError = isError,
                        filePath = fullPath
                    )
                } else {
                    DownloadProgress(
                        id = gid,
                        gid = gid,
                        title = fileName,
                        progress = progress,
                        status = if (isPaused) "Pausado" else if (isCompleted) "Concluído" else if (isError) "Erro no download" else "Adicionado",
                        speed = speedStr,
                        totalSize = sizeStr,
                        isPaused = isPaused,
                        isCompleted = isCompleted,
                        isError = isError,
                        filePath = fullPath
                    )
                }

                if (existingIndex != -1) {
                    activeDownloads[existingIndex] = updatedDownload
                } else {
                    activeDownloads.add(updatedDownload)
                }

                if (!isCompleted) {
                    updateNotification(context, notificationManager, updatedDownload)
                } else {
                    notificationManager.cancel(gid.hashCode())
                }
            }
        }
    }

    private fun updateNotification(context: Context, notificationManager: NotificationManager, download: DownloadProgress) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("GO_TO_DOWNLOADS", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, download.gid.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(download.title)
            .setContentText("${(download.progress * 100).toInt()}% - ${download.status} ${download.speed}")
            .setSmallIcon(drawables.terminal)
            .setProgress(100, (download.progress * 100).toInt(), false)
            .setContentIntent(pendingIntent)
            .setOngoing(!download.isPaused)
            .setOnlyAlertOnce(true)
            .build()

        notificationManager.notify(download.gid.hashCode(), notification)
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

    fun updateGlobalOptions() {
        val options = mutableMapOf<String, String>()
        options["max-overall-download-limit"] = Settings.aria2MaxDownloadLimit
        options["max-connection-per-server"] = Settings.aria2MaxConnections.toString()

        callAria2Method("aria2.changeGlobalOption", listOf(options))
    }

    private fun callAria2Method(method: String, params: List<Any>) {
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
}
