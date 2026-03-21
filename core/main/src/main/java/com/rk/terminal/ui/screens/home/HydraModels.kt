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
    @SerializedName("fileSize") val fileSize: String? = null,
    @SerializedName("shop") val shop: String? = null,
    @SerializedName("objectId") val objectId: String? = null,
    @SerializedName("libraryImageUrl") val libraryImageUrl: String? = null,
    @SerializedName("downloadSources") val downloadSources: List<HydraDownloadSource>? = null,
    @SerializedName("playTimeInSeconds") val playTimeInSeconds: Long? = null,
    @SerializedName("lastTimePlayed") val lastTimePlayed: String? = null,
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

data class HydraFriend(
    @SerializedName("id") val id: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("profileImageUrl") val profileImageUrl: String? = null
)

data class HydraFriendRequest(
    @SerializedName("id") val id: String? = null,
    @SerializedName("AId") val AId: String? = null,
    @SerializedName("BId") val BId: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("A") val userA: HydraFriend? = null,
    @SerializedName("B") val userB: HydraFriend? = null
)

data class HydraFriendRequestsResponse(
    @SerializedName("incoming") val incoming: List<HydraFriendRequest>? = null,
    @SerializedName("outgoing") val outgoing: List<HydraFriendRequest>? = null
)

data class HydraBadge(
    @SerializedName("name") val name: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("badge") val badge: BadgeIcon? = null
)

data class BadgeIcon(
    @SerializedName("url") val url: String? = null
)

data class HydraRecentGame(
    @SerializedName("title") val title: String? = null,
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("playTimeInSeconds") val playTimeInSeconds: Long? = null,
    @SerializedName("shop") val shop: String? = null,
    @SerializedName("objectId") val objectId: String? = null
)

data class HydraStatValue(
    @SerializedName("value") val value: Double? = null,
    @SerializedName("topPercentile") val topPercentile: Double? = null
)

data class HydraUserStats(
    @SerializedName("unlockedAchievementSum") val unlockedAchievementSum: Int? = null,
    @SerializedName("totalPlayTimeInSeconds") val totalPlayTimeInSeconds: HydraStatValue? = null,
    @SerializedName("achievementsPointsEarnedSum") val achievementsPointsEarnedSum: HydraStatValue? = null
)

data class HydraProfile(
    @SerializedName("id") val id: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("profileImageUrl") val profileImageUrl: String? = null,
    @SerializedName("backgroundImageUrl") val backgroundImageUrl: String? = null,
    @SerializedName("friends") val friends: List<HydraFriend>? = null,
    @SerializedName("karma") val karma: Int? = null,
    @SerializedName("badges") val badges: List<String>? = null,
    @SerializedName("recentGames") val recentGames: List<HydraRecentGame>? = null,
    @SerializedName("stats") val stats: HydraUserStats? = null
)

data class HydraReviewUser(
    @SerializedName("id") val id: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("profileImageUrl") val profileImageUrl: String? = null
)

data class HydraReview(
    @SerializedName("id") val id: String? = null,
    @SerializedName("reviewHtml") val reviewHtml: String? = null,
    @SerializedName("score") val score: Int? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("upvotes") val upvotes: Int? = null,
    @SerializedName("downvotes") val downvotes: Int? = null,
    @SerializedName("user") val user: HydraReviewUser? = null
)

data class HydraReviewsResponse(
    @SerializedName("reviews") val reviews: List<HydraReview>? = null,
    @SerializedName("totalCount") val totalCount: Int? = null
)

data class SGDBResponse(val success: Boolean, val data: List<SGDBGame>)
data class SGDBGame(val id: Int, val name: String)
data class SGDBArtResponse(val success: Boolean, val data: List<SGDBArt>)
data class SGDBArt(val url: String)
