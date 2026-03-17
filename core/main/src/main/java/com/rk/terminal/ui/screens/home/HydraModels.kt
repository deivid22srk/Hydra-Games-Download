package com.rk.terminal.ui.screens.home

import com.google.gson.annotations.SerializedName

data class HydraSourceConfig(
    val url: String,
    val isEnabled: Boolean = true
)

data class HydraGame(
    @SerializedName("title") val title: String? = null,
    @SerializedName("uris") val uris: List<String>? = null
)

data class HydraSource(
    @SerializedName("name") val name: String? = null,
    @SerializedName("downloads") val downloads: List<HydraGame>? = null
)

data class SGDBResponse(val success: Boolean, val data: List<SGDBGame>)
data class SGDBGame(val id: Int, val name: String)
data class SGDBArtResponse(val success: Boolean, val data: List<SGDBArt>)
data class SGDBArt(val url: String)
