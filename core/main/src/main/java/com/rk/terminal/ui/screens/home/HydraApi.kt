package com.rk.terminal.ui.screens.home

import com.google.gson.Gson
import com.rk.settings.Settings
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

object HydraApi {
    private val client = OkHttpClient.Builder()
        .addInterceptor(UserAgentInterceptor())
        .addInterceptor(AuthInterceptor())
        .build()

    private val baseClient = OkHttpClient.Builder()
        .addInterceptor(UserAgentInterceptor())
        .build()

    fun getClient(): OkHttpClient = client

    fun revalidateSession() {
        if (Settings.refreshToken.isNotBlank() && System.currentTimeMillis() + 300000 > Settings.tokenExpiration) {
            // Token is expired or about to expire in 5 minutes
            synchronized(this) {
                if (System.currentTimeMillis() + 300000 > Settings.tokenExpiration) {
                    AuthInterceptor().refreshAccessToken()
                }
            }
        }
    }

    private class UserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .header("User-Agent", "Hydra Launcher Android")
                .build()
            return chain.proceed(request)
        }
    }

    private class AuthInterceptor : Interceptor {
        fun refreshAccessToken(): Boolean {
            val client = baseClient
            val gson = Gson()
            val requestBody = mapOf("refreshToken" to Settings.refreshToken)
            val json = gson.toJson(requestBody)
            val body = json.toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("https://hydra-api-us-east-1.losbroxas.org/auth/refresh")
                .post(body)
                .build()

            return try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val respBody = response.body?.string()
                    val data = gson.fromJson(respBody, Map::class.java)
                    val newAccessToken = data["accessToken"] as? String
                    val newRefreshToken = data["refreshToken"] as? String
                    val expiresIn = (data["expiresIn"] as? Number)?.toLong() ?: 0L
                    val userId = data["userId"] as? String

                    if (newAccessToken != null && newRefreshToken != null) {
                        Settings.accessToken = newAccessToken
                        Settings.refreshToken = newRefreshToken
                        Settings.tokenExpiration = System.currentTimeMillis() + (expiresIn * 1000)
                        if (userId != null) {
                            Settings.userId = userId
                        }
                        true
                    } else false
                } else {
                    if (response.code == 401 || response.code == 403) {
                        // Refresh token is invalid/expired, log out
                        Settings.accessToken = ""
                        Settings.refreshToken = ""
                        Settings.userId = ""
                    }
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()

            // Only add token for Hydra API calls
            if (!originalRequest.url.toString().contains("hydra-api-us-east-1.losbroxas.org")) {
                return chain.proceed(originalRequest)
            }

            var request = originalRequest
            if (Settings.accessToken.isNotBlank()) {
                // Proactively refresh token if it's expired or about to expire (within 5 minutes)
                if (Settings.refreshToken.isNotBlank() && System.currentTimeMillis() + 300000 > Settings.tokenExpiration) {
                    synchronized(this) {
                        if (System.currentTimeMillis() + 300000 > Settings.tokenExpiration) {
                            refreshAccessToken()
                        }
                    }
                }

                request = originalRequest.newBuilder()
                    .header("Authorization", "Bearer ${Settings.accessToken}")
                    .build()
            }

            val response = chain.proceed(request)

            if (response.code == 401 && Settings.refreshToken.isNotBlank()) {
                synchronized(this) {
                    // Try to refresh token
                    val refreshSuccess = refreshAccessToken()
                    if (refreshSuccess) {
                        response.close()
                        val newRequest = originalRequest.newBuilder()
                            .header("Authorization", "Bearer ${Settings.accessToken}")
                            .build()
                        return chain.proceed(newRequest)
                    }
                }
            }

            return response
        }

    }
}
