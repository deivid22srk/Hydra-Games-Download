package com.rk.terminal.ui.screens.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object PixelDrainApi {
    private const val BYPASS_BASE_URL = "https://cdn.pixeldrain.eu.cc"
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun unlock(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val id = extractId(url) ?: return@withContext null
            val bypassUrl = "$BYPASS_BASE_URL/$id"

            // Try bypass
            val bypassRequest = Request.Builder().url(bypassUrl).head().build()
            try {
                client.newCall(bypassRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        return@withContext bypassUrl
                    }
                }
            } catch (e: Exception) {
                // Ignore and fallback to API resolver
            }

            // Fallback to API resolver
            "https://pixeldrain.com/api/file/$id?download"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractId(url: String): String? {
        return try {
            val uri = java.net.URI(url)
            val pathParts = uri.path.split("/").filter { it.isNotBlank() }
            if (pathParts.size >= 2 && pathParts[0] == "u") {
                pathParts[1]
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
