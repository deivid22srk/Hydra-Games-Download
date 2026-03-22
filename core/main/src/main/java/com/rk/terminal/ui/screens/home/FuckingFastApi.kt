package com.rk.terminal.ui.screens.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object FuckingFastApi {
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val FUCKINGFAST_REGEX = Pattern.compile("window\\.open\\(\"(https://fuckingfast\\.co/dl/[^\"]*)\"\\)")

    suspend fun getDirectLink(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val html = response.body?.string() ?: ""

                if (html.lowercase().contains("rate limit")) {
                    return@withContext null
                }

                if (html.contains("File Not Found Or Deleted")) {
                    return@withContext null
                }

                val matcher = FUCKINGFAST_REGEX.matcher(html)
                if (matcher.find()) {
                    matcher.group(1)
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
