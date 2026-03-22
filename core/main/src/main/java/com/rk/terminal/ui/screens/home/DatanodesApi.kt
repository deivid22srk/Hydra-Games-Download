package com.rk.terminal.ui.screens.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import com.google.gson.Gson

object DatanodesApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    suspend fun getDownloadUrl(downloadUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val uri = java.net.URI(downloadUrl)
            val pathSegments = uri.path.split("/").filter { it.isNotBlank() }
            val fileCode = pathSegments.firstOrNull() ?: return@withContext null

            val formBody = FormBody.Builder()
                .add("op", "download2")
                .add("id", fileCode)
                .add("rand", "")
                .add("referer", "https://datanodes.to/download")
                .add("method_free", "Free Download >>")
                .add("method_premium", "")
                .add("__dl", "1")
                .add("g_captch__a", "1")
                .build()

            val request = Request.Builder()
                .url("https://datanodes.to/download")
                .post(formBody)
                .header("accept", "*/*")
                .header("accept-language", "en-US,en;q=0.9")
                .header("sec-ch-ua", "\"Google Chrome\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\"")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua-platform", "\"Windows\"")
                .header("sec-fetch-dest", "empty")
                .header("sec-fetch-mode", "cors")
                .header("sec-fetch-site", "same-origin")
                .header("Referer", "https://datanodes.to/download")
                .header("Cookie", "lang=english;")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val data = gson.fromJson(body, Map::class.java)
                    val url = data["url"] as? String
                    if (url != null) {
                        java.net.URLDecoder.decode(url, "UTF-8")
                    } else null
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
