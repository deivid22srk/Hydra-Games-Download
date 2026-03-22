package com.rk.terminal.ui.screens.home

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest

object HydraSourceCache {
    private fun getCacheDir(context: Context): File {
        val dir = File(context.filesDir, "hydra_sources")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getCacheFile(context: Context, url: String): File {
        val hash = MessageDigest.getInstance("MD5").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(getCacheDir(context), "$hash.json")
    }

    fun saveSource(context: Context, url: String, source: HydraSource) {
        val file = getCacheFile(context, url)
        val json = Gson().toJson(source)
        file.writeText(json)
    }

    fun getSource(context: Context, url: String): HydraSource? {
        val file = getCacheFile(context, url)
        if (!file.exists()) return null
        return try {
            val json = file.readText()
            Gson().fromJson(json, HydraSource::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
