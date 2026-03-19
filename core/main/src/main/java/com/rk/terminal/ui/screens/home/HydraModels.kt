package com.rk.terminal.ui.screens.home

import com.google.gson.annotations.SerializedName

data class HydraSourceConfig(
    val url: String,
    val isEnabled: Boolean = true
)

data class HydraDownloadSource(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("uris") val uris: List<String>? = null
)

data class HydraGame(
    @SerializedName("title") val title: String? = null,
    @SerializedName("uris") val uris: List<String>? = null,
    @SerializedName("shop") val shop: String? = null,
    @SerializedName("objectId") val objectId: String? = null,
    @SerializedName("libraryImageUrl") val libraryImageUrl: String? = null,
    @SerializedName("downloadSources") val downloadSources: List<HydraDownloadSource>? = null,
    var sourceName: String? = null
)

data class HydraSource(
    @SerializedName("name") val name: String? = null,
    @SerializedName("downloads") val downloads: List<HydraGame>? = null
)

data class HydraSearchResponse(
    @SerializedName("count") val count: Int? = null,
    @SerializedName("edges") val edges: List<HydraGame>? = null
)

data class HydraGameStats(
    @SerializedName("playerCount") val playerCount: Int? = null,
    @SerializedName("downloadCount") val downloadCount: Int? = null,
    @SerializedName("averageScore") val averageScore: Double? = null,
    @SerializedName("reviewCount") val reviewCount: Int? = null
)

data class HydraGameAssets(
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("libraryHeroImageUrl") val libraryHeroImageUrl: String? = null,
    @SerializedName("libraryImageUrl") val libraryImageUrl: String? = null,
    @SerializedName("logoImageUrl") val logoImageUrl: String? = null
)

data class HydraRepack(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("uris") val uris: List<String>? = null,
    @SerializedName("fileSize") val fileSize: String? = null,
    @SerializedName("uploadDate") val uploadDate: String? = null,
    @SerializedName("repacker") val repacker: String? = null
)

data class SGDBResponse(val success: Boolean, val data: List<SGDBGame>)
data class SGDBGame(val id: Int, val name: String)
data class SGDBArtResponse(val success: Boolean, val data: List<SGDBArt>)
data class SGDBArt(val url: String)
