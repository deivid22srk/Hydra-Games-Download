package com.rk.terminal.ui.screens.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object BuzzHeavierApi {
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    suspend fun getDirectLink(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val baseUrl = url.split("#")[0]

            // First request to establish session if needed (mimicking HydraPc)
            val initialRequest = Request.Builder()
                .url(baseUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(initialRequest).execute().close()

            val downloadUrl = if (baseUrl.endsWith("/")) "${baseUrl}download" else "$baseUrl/download"

            val headRequest = Request.Builder()
                .url(downloadUrl)
                .head()
                .header("hx-current-url", baseUrl)
                .header("hx-request", "true")
                .header("referer", baseUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(headRequest).execute().use { response ->
                val hxRedirect = response.header("hx-redirect")
                if (hxRedirect != null) {
                    if (hxRedirect.startsWith("/dl/")) {
                        val domain = java.net.URI(baseUrl).host
                        "https://$domain$hxRedirect"
                    } else {
                        hxRedirect
                    }
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
