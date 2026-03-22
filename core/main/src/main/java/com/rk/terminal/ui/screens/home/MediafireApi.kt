package com.rk.terminal.ui.screens.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object MediafireApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val validMediafirePreDL = Pattern.compile("(?<=['\"])(https?:)?(//)?(www\\.)?mediafire\\.com/(file|view|download)/[^'\"?]+\\?dkey=[^'\"]+(?=['\"])")
    private val validDynamicDL = Pattern.compile("(?<=['\"])https?://download\\d+\\.mediafire\\.com/[^'\"]+(?=['\"])")

    suspend fun getDownloadUrl(mediafireUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val processedUrl = processUrl(mediafireUrl)
            val request = Request.Builder().url(processedUrl).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val html = response.body?.string() ?: ""
                extractDirectUrl(html)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun processUrl(url: String): String {
        var processed = url.replace("http://", "https://")
        if (!processed.startsWith("https://")) {
            processed = if (processed.startsWith("//")) "https:$processed" else "https://$processed"
        }
        return processed
    }

    private fun extractDirectUrl(html: String): String? {
        val preMatcher = validMediafirePreDL.matcher(html)
        if (preMatcher.find()) {
            return preMatcher.group()
        }

        val dlMatcher = validDynamicDL.matcher(html)
        if (dlMatcher.find()) {
            return dlMatcher.group()
        }

        return null
    }
}
