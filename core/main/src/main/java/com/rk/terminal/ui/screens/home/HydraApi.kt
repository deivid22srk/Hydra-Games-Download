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
        .addInterceptor(AuthInterceptor())
        .build()

    fun getClient(): OkHttpClient = client

    private class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()

            // Only add token for Hydra API calls
            if (!originalRequest.url.toString().contains("hydra-api-us-east-1.losbroxas.org")) {
                return chain.proceed(originalRequest)
            }

            var request = originalRequest
            if (Settings.accessToken.isNotBlank()) {
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

        private fun refreshAccessToken(): Boolean {
            val client = OkHttpClient()
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
                    val expiresIn = (data["expiresIn"] as? Double)?.toLong() ?: 0L

                    if (newAccessToken != null && newRefreshToken != null) {
                        Settings.accessToken = newAccessToken
                        Settings.refreshToken = newRefreshToken
                        Settings.tokenExpiration = System.currentTimeMillis() + (expiresIn * 1000)
                        true
                    } else false
                } else {
                    // Clear tokens if refresh fails
                    Settings.accessToken = ""
                    Settings.refreshToken = ""
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
