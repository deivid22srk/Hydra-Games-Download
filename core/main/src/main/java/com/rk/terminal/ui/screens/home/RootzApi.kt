package com.rk.terminal.ui.screens.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import com.google.gson.Gson

data class RootzApiResponse(
    val success: Boolean,
    val data: RootzData? = null,
    val error: String? = null
)

data class RootzData(
    val url: String?,
    val fileName: String?
)

object RootzApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    suspend fun getDownloadUrl(uri: String): String? = withContext(Dispatchers.IO) {
        try {
            val urlObj = java.net.URL(uri)
            val pathSegments = urlObj.path.split("/").filter { it.isNotBlank() }

            if (pathSegments.size < 2 || pathSegments[0] != "d") {
                return@withContext null
            }

            val id = pathSegments[1]
            val apiUrl = "https://www.rootz.so/api/files/download-by-short/$id"

            val request = Request.Builder().url(apiUrl).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val resp = gson.fromJson(body, RootzApiResponse::class.java)
                    if (resp.success && resp.data?.url != null) {
                        resp.data.url
                    } else null
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
