package com.rk.terminal.ui.screens.home

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class GofileAccountResponse(
    val status: String,
    val data: GofileAccountData?
)

data class GofileAccountData(
    val id: String,
    val token: String
)

data class GofileContentsResponse(
    val status: String,
    val data: GofileContentsData?
)

data class GofileContentsData(
    val id: String,
    val type: String,
    val link: String?,
    val children: Map<String, GofileContentChild>?
)

data class GofileContentChild(
    val id: String,
    val type: String,
    val link: String?
)

object GofileApi {
    private const val DEFAULT_USER_AGENT = "Mozilla/5.0"
    private const val LANGUAGE = "en-US"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    private fun generateWebsiteToken(accountToken: String): String {
        val timeSlot = System.currentTimeMillis() / 1000 / 14400
        val raw = "$DEFAULT_USER_AGENT::$LANGUAGE::$accountToken::$timeSlot::gf2026x"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(raw.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun getBaseHeaders(accountToken: String? = null): Map<String, String> {
        val headers = mutableMapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Accept-Encoding" to "gzip",
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "Origin" to "https://gofile.io",
            "Referer" to "https://gofile.io/"
        )
        if (accountToken != null && accountToken.isNotBlank()) {
            headers["Authorization"] = "Bearer $accountToken"
        }
        return headers
    }

    suspend fun authorize(): String? = withContext(Dispatchers.IO) {
        val websiteToken = generateWebsiteToken("")
        val headers = getBaseHeaders().toMutableMap()
        headers["X-Website-Token"] = websiteToken
        headers["X-BL"] = LANGUAGE

        val request = Request.Builder()
            .url("https://api.gofile.io/accounts")
            .post("{}".toRequestBody("application/json".toMediaTypeOrNull()))
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val resp = gson.fromJson(body, GofileAccountResponse::class.java)
                    if (resp.status == "ok") {
                        resp.data?.token
                    } else null
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getDownloadLink(id: String, token: String, password: String? = null): String? {
        return parseLinksRecursively(id, token, password)
    }

    suspend fun checkDownloadUrl(url: String, token: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .head()
            .addHeader("Cookie", "accountToken=$token")
            .build()
        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun parseLinksRecursively(id: String, token: String, password: String? = null): String? = withContext(Dispatchers.IO) {
        val urlBuilder = StringBuilder("https://api.gofile.io/contents/$id?cache=true&sortField=createTime&sortDirection=1")
        if (password != null) {
            urlBuilder.append("&password=$password")
        }

        val websiteToken = generateWebsiteToken(token)
        val headers = getBaseHeaders(token).toMutableMap()
        headers["X-Website-Token"] = websiteToken
        headers["X-BL"] = LANGUAGE

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .get()
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val resp = gson.fromJson(body, GofileContentsResponse::class.java)
                    if (resp.status != "ok" || resp.data == null) return@withContext null

                    if (resp.data.type == "file") {
                        return@withContext resp.data.link
                    }

                    if (resp.data.type == "folder") {
                        val children = resp.data.children?.values ?: emptyList()
                        for (child in children) {
                            if (child.type == "file" && child.link != null) {
                                return@withContext child.link
                            }
                            if (child.type == "folder") {
                                val nestedLink = parseLinksRecursively(child.id, token, password)
                                if (nestedLink != null) return@withContext nestedLink
                            }
                        }
                    }
                    null
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
