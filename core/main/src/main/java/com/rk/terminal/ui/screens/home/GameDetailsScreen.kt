package com.rk.terminal.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.screens.terminal.TerminalBackEnd
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import java.net.URLEncoder
import androidx.lifecycle.lifecycleScope
import com.rk.terminal.ui.routes.MainActivityRoutes
import com.rk.terminal.ui.screens.settings.WorkingMode
import java.security.MessageDigest
import android.content.Intent
import android.net.Uri

fun generateGid(url: String): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(url.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(16)
}

data class LocalRepack(
    val title: String,
    val sourceName: String,
    val uris: List<String>,
    val fileSize: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailsScreen(
    viewModel: SharedGameViewModel,
    navController: NavController,
    mainActivity: MainActivity
) {
    val gameTitle = viewModel.selectedGameTitle
    val gameUris = viewModel.selectedGameUris
    val gameObjectId = viewModel.selectedGameObjectId
    val gameShop = viewModel.selectedGameShop
    var coverUrl by remember { mutableStateOf(viewModel.selectedGameCover) }
    var isResolvingLink by remember { mutableStateOf(false) }
    var gameStats by remember { mutableStateOf<HydraGameStats?>(null) }
    var gameAssets by remember { mutableStateOf<HydraGameAssets?>(null) }
    var repacks by remember { mutableStateOf<List<HydraRepack>>(emptyList()) }
    var localRepacks by remember { mutableStateOf<List<LocalRepack>>(emptyList()) }
    var steamDetails by remember { mutableStateOf<Map<String, Any>?>(null) }
    var hydraReviews by remember { mutableStateOf<List<HydraReview>>(emptyList()) }
    var allReviews by remember { mutableStateOf<List<HydraReview>>(emptyList()) }
    var totalReviewsCount by remember { mutableStateOf(0) }
    var hasUserReviewed by remember { mutableStateOf(false) }
    var reviewSort by remember { mutableStateOf("mostVoted") }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showReviewFormDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteReview by remember { mutableStateOf(false) }
    var isSearchingSources by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var isAddingToLibrary by remember { mutableStateOf(false) }
    var isRemovingFromLibrary by remember { mutableStateOf(false) }
    var isAlreadyInLibrary by remember { mutableStateOf(false) }
    var showLibraryMenu by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(gameTitle, gameObjectId, gameShop) {
        if (gameObjectId != null && gameShop != null) {
            isSearchingSources = true
            withContext(Dispatchers.IO) {
                // First: Local Sources Search (Immediate result)
                val localResults = mutableListOf<LocalRepack>()
                Settings.hydraSources.filter { it.isEnabled }.forEach { config ->
                    try {
                        val source = HydraSourceCache.getSource(mainActivity, config.url)
                        if (source != null) {
                            val sourceName = source.name ?: config.url.split("/").getOrNull(2) ?: "Desconhecida"
                            source.downloads?.filter {
                                val titleMatch = it.title?.contains(gameTitle, ignoreCase = true) == true ||
                                                 gameTitle.contains(it.title ?: "", ignoreCase = true)
                                val objectIdMatch = it.objectId == gameObjectId && it.shop == gameShop
                                titleMatch || (objectIdMatch && it.objectId != null)
                            }?.forEach { game ->
                                localResults.add(LocalRepack(
                                    title = game.title ?: "Sem nome",
                                    sourceName = sourceName,
                                    uris = game.uris ?: emptyList(),
                                    fileSize = game.fileSize
                                ))
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                withContext(Dispatchers.Main) { localRepacks = localResults }

                val client = HydraApi.getClient()
                val gson = Gson()
                val baseUrl = "https://hydra-api-us-east-1.losbroxas.org/games/$gameShop/$gameObjectId"

                try {
                    // Stats
                    client.newCall(Request.Builder().url("$baseUrl/stats").build()).execute().use { response ->
                        if (response.isSuccessful) {
                            gameStats = gson.fromJson(response.body?.string(), HydraGameStats::class.java)
                        }
                    }
                    // Assets
                    client.newCall(Request.Builder().url("$baseUrl/assets").build()).execute().use { response ->
                        if (response.isSuccessful) {
                            val assets = gson.fromJson(response.body?.string(), HydraGameAssets::class.java)
                            gameAssets = assets
                            if (coverUrl == null) coverUrl = assets.libraryImageUrl
                        }
                    }
                    // Repacks
                    val sources = Settings.hydraSources.filter { it.isEnabled }.map { it.url }
                    val repacksUrl = StringBuilder("$baseUrl/download-sources?take=20&skip=0")
                    sources.forEach { sourceId ->
                        repacksUrl.append("&downloadSourceIds[]=").append(URLEncoder.encode(sourceId, "UTF-8"))
                    }
                    client.newCall(Request.Builder().url(repacksUrl.toString()).build()).execute().use { response ->
                        if (response.isSuccessful) {
                            val type = object : com.google.gson.reflect.TypeToken<List<HydraRepack>>() {}.type
                            repacks = gson.fromJson(response.body?.string(), type) ?: emptyList()
                        }
                    }

                    // Steam Details
                    if (gameShop == "steam") {
                        val steamUrl = "https://store.steampowered.com/api/appdetails?appids=$gameObjectId&l=pt"
                        client.newCall(Request.Builder().url(steamUrl).build()).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string()
                                val data = gson.fromJson(body, Map::class.java)
                                val gameData = (data[gameObjectId] as? Map<*, *>)?.get("data") as? Map<String, Any>
                                steamDetails = gameData
                            }
                        }
                    }

                    // Check if already in library
                    val userId = Settings.userId
                    if (userId.isNotBlank()) {
                        val libUrl = "https://hydra-api-us-east-1.losbroxas.org/users/$userId/library?take=1000"
                        client.newCall(Request.Builder().url(libUrl).build()).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string()
                                val data = gson.fromJson<Map<String, Any>>(body, object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type)
                                val libraryList = data["library"] as? List<Map<String, Any>>
                                isAlreadyInLibrary = libraryList?.any {
                                    it["objectId"] == gameObjectId && it["shop"] == gameShop
                                } ?: false
                            }
                        }
                    }

                    // Hydra Reviews - initial batch
                    loadReviews(client, gson, gameShop, gameObjectId, sortBy = reviewSort,
                        onSuccess = { reviews, total ->
                            hydraReviews = reviews
                            allReviews = reviews
                            totalReviewsCount = total
                        })

                    // Check if user has reviewed
                    if (Settings.accessToken.isNotBlank()) {
                        client.newCall(Request.Builder().url("$baseUrl/reviews/check").build()).execute().use { response ->
                            if (response.isSuccessful) {
                                val checkResp = gson.fromJson(response.body?.string(), HydraCheckReviewResponse::class.java)
                                hasUserReviewed = checkResp.hasReviewed
                            }
                        }
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isSearchingSources = false
                }
            }
        }

        if (coverUrl != null) return@LaunchedEffect

        val apiKey = Settings.steamGridDbApiKey
        if (apiKey.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val client = HydraApi.getClient()
                    val gson = Gson()

                    val gridUrl = if (gameShop == "steam" && gameObjectId != null) {
                        "https://www.steamgriddb.com/api/v2/grids/steam/$gameObjectId"
                    } else {
                        val searchRequest = Request.Builder()
                            .url("https://www.steamgriddb.com/api/v2/search/autocomplete/${URLEncoder.encode(gameTitle, "UTF-8")}")
                            .addHeader("Authorization", "Bearer $apiKey")
                            .build()

                        val gameId = client.newCall(searchRequest).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string()
                                val searchData = gson.fromJson(body, SGDBResponse::class.java)
                                searchData.data.firstOrNull()?.id
                            } else null
                        }

                        if (gameId != null) "https://www.steamgriddb.com/api/v2/grids/game/$gameId" else null
                    }

                    if (gridUrl != null) {
                        val gridRequest = Request.Builder()
                            .url(gridUrl)
                            .addHeader("Authorization", "Bearer $apiKey")
                            .build()

                        client.newCall(gridRequest).execute().use { gridResponse ->
                            if (gridResponse.isSuccessful) {
                                val gridBody = gridResponse.body?.string()
                                val artData = gson.fromJson(gridBody, SGDBArtResponse::class.java)
                                coverUrl = artData.data.firstOrNull()?.url
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val addToLibrary = {
        if (!isAddingToLibrary && gameObjectId != null && gameShop != null) {
            // Check login state before attempting
            if (Settings.userId.isBlank() || Settings.accessToken.isBlank()) {
                android.widget.Toast.makeText(mainActivity, "Faça login para adicionar jogos à biblioteca", android.widget.Toast.LENGTH_SHORT).show()
            } else {
            isAddingToLibrary = true
            scope.launch(Dispatchers.IO) {
                try {
                    // Revalidate session first to ensure fresh token
                    HydraApi.revalidateSession()

                    if (Settings.accessToken.isBlank()) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(mainActivity, "Sessão expirada. Faça login novamente.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    val client = HydraApi.getClient()
                    val gson = Gson()

                    // Match PC API format: PUT /profile/games/{shop}/{objectId}
                    // with playTimeDeltaInSeconds (required number) and lastTimePlayed (valid ISO date)
                    val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }.format(java.util.Date())

                    val body = mapOf(
                        "playTimeDeltaInSeconds" to 0,
                        "lastTimePlayed" to nowIso
                    )

                    val requestBody = gson.toJson(body).toRequestBody("application/json".toMediaTypeOrNull())
                    val url = "https://hydra-api-us-east-1.losbroxas.org/profile/games/$gameShop/$gameObjectId"
                    val request = Request.Builder()
                        .url(url)
                        .put(requestBody)
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            withContext(Dispatchers.Main) {
                                isAlreadyInLibrary = true
                                android.widget.Toast.makeText(mainActivity, "Adicionado à biblioteca!", android.widget.Toast.LENGTH_SHORT).show()
                                android.util.Log.d("LibraryAdd", "Game added: objectId=$gameObjectId, shop=$gameShop")
                            }
                        } else {
                            val errorBody = response.body?.string()?.take(300)
                            val httpCode = response.code
                            android.util.Log.e("LibraryAdd", "Failed with $httpCode: $errorBody (request: shop=$gameShop, objectId=$gameObjectId)")

                            val message = when (httpCode) {
                                401 -> "Sessão expirada. Faça login novamente."
                                403 -> "Sem permissão para adicionar este jogo."
                                404 -> "Jogo não encontrado na API."
                                409 -> "Jogo já está na biblioteca."
                                else -> "Erro ao adicionar: HTTP $httpCode"
                            }
                            if (httpCode == 401) {
                                Settings.accessToken = ""
                                Settings.refreshToken = ""
                                Settings.userId = ""
                                Settings.tokenExpiration = 0L
                            }
                            android.widget.Toast.makeText(mainActivity, message, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LibraryAdd", "Network error", e)
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(mainActivity, "Falha na rede: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) { isAddingToLibrary = false }
                }
            }
            }
        }
    }

    val removeFromLibrary = {
        if (!isRemovingFromLibrary && gameObjectId != null && gameShop != null) {
            isRemovingFromLibrary = true
            scope.launch(Dispatchers.IO) {
                try {
                    HydraApi.revalidateSession()

                    if (Settings.accessToken.isBlank()) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(mainActivity, "Sessão expirada.", android.widget.Toast.LENGTH_SHORT).show()
                            isRemovingFromLibrary = false
                        }
                        return@launch
                    }

                    val client = HydraApi.getClient()
                    // DELETE /profile/games/{objectId} — same as HydraPc
                    val url = "https://hydra-api-us-east-1.losbroxas.org/profile/games/$gameObjectId"
                    val request = Request.Builder()
                        .url(url)
                        .delete()
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            withContext(Dispatchers.Main) {
                                isAlreadyInLibrary = false
                                android.widget.Toast.makeText(mainActivity, "Removido da biblioteca!", android.widget.Toast.LENGTH_SHORT).show()
                                android.util.Log.d("LibraryRemove", "Game removed: objectId=$gameObjectId, shop=$gameShop")
                            }
                        } else {
                            val errorBody = response.body?.string()?.take(300)
                            val httpCode = response.code
                            android.util.Log.e("LibraryRemove", "Failed with $httpCode: $errorBody")

                            val message = when (httpCode) {
                                401 -> "Sessão expirada. Faça login novamente."
                                404 -> "Jogo não encontrado na biblioteca."
                                else -> "Erro ao remover: HTTP $httpCode"
                            }
                            if (httpCode == 401) {
                                Settings.accessToken = ""
                                Settings.refreshToken = ""
                                Settings.userId = ""
                                Settings.tokenExpiration = 0L
                            }
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(mainActivity, message, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LibraryRemove", "Network error", e)
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(mainActivity, "Falha na rede: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) { isRemovingFromLibrary = false }
                }
            }
        }
    }

    /* ------------ review helpers ------------ */
    fun loadReviews(
        client: okhttp3.OkHttpClient,
        gson: Gson,
        shop: String?,
        objectId: String?,
        sortBy: String,
        onSuccess: (List<HydraReview>, Int) -> Unit
    ) {
        if (shop == null || objectId == null) return
        val reviewsUrl = "https://hydra-api-us-east-1.losbroxas.org/games/$shop/$objectId/reviews?take=20&skip=0&sortBy=$sortBy"
        client.newCall(Request.Builder().url(reviewsUrl).build()).execute().use { response ->
            if (response.isSuccessful) {
                val reviewsResp = gson.fromJson(response.body?.string(), HydraReviewsResponse::class.java)
                onSuccess(reviewsResp.reviews ?: emptyList(), reviewsResp.totalCount ?: 0)
            }
        }
    }

    val handleVote = { reviewId: String, isUpvote: Boolean ->
        scope.launch(Dispatchers.IO) {
            try {
                val client = HydraApi.getClient()
                val url = "https://hydra-api-us-east-1.losbroxas.org/games/$gameShop/$gameObjectId/reviews/$reviewId/${if (isUpvote) "upvote" else "downvote"}"
                val req = Request.Builder().url(url).put("".toRequestBody()).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            hydraReviews = hydraReviews.map { r ->
                                if (r.id == reviewId) {
                                    val wasUp = r.hasUpvoted == true
                                    val wasDown = r.hasDownvoted == true
                                    if (isUpvote) {
                                        r.copy(
                                            hasUpvoted = !wasUp,
                                            hasDownvoted = false,
                                            upvotes = (r.upvotes ?: 0) + if (!wasUp) 1 else -1,
                                            downvotes = (r.downvotes ?: 0) - if (wasDown) 1 else 0
                                        )
                                    } else {
                                        r.copy(
                                            hasDownvoted = !wasDown,
                                            hasUpvoted = false,
                                            downvotes = (r.downvotes ?: 0) + if (!wasDown) 1 else -1,
                                            upvotes = (r.upvotes ?: 0) - if (wasUp) 1 else 0
                                        )
                                    }
                                } else r
                            }
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val handleDeleteReview = { reviewId: String ->
        scope.launch(Dispatchers.IO) {
            try {
                val client = HydraApi.getClient()
                val url = "https://hydra-api-us-east-1.losbroxas.org/games/$gameShop/$gameObjectId/reviews/$reviewId"
                val req = Request.Builder().url(url).delete().build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            showConfirmDeleteReview = false
                            hasUserReviewed = false
                            loadReviews(HydraApi.getClient(), Gson(), gameShop, gameObjectId, reviewSort) { reviews, total ->
                                hydraReviews = reviews
                                allReviews = reviews
                                totalReviewsCount = total
                            }
                            android.widget.Toast.makeText(mainActivity, "Avaliação removida!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val handleCreateReview = { reviewHtml: String, score: Int ->
        scope.launch(Dispatchers.IO) {
            try {
                val client = HydraApi.getClient()
                val gson = Gson()
                val body = gson.toJson(HydraReviewCreateRequest(reviewHtml, score)).toRequestBody("application/json".toMediaTypeOrNull())
                val url = "https://hydra-api-us-east-1.losbroxas.org/games/$gameShop/$gameObjectId/reviews"
                val req = Request.Builder().url(url).post(body).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            showReviewFormDialog = false
                            hasUserReviewed = true
                            loadReviews(HydraApi.getClient(), Gson(), gameShop, gameObjectId, reviewSort) { reviews, total ->
                                hydraReviews = reviews
                                allReviews = reviews
                                totalReviewsCount = total
                            }
                            android.widget.Toast.makeText(mainActivity, "Avaliação enviada!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(mainActivity, "Erro ao enviar avaliação: HTTP ${response.code}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(gameTitle, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (Settings.userId.isNotBlank()) {
                        Box {
                            if (!isAlreadyInLibrary) {
                                IconButton(
                                    onClick = { addToLibrary() },
                                    enabled = !isAddingToLibrary
                                ) {
                                    if (isAddingToLibrary) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.LibraryAdd,
                                            contentDescription = "Adicionar à Biblioteca",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            } else {
                                IconButton(
                                    onClick = { showLibraryMenu = true },
                                    enabled = !isRemovingFromLibrary
                                ) {
                                    if (isRemovingFromLibrary) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.LibraryAddCheck,
                                            contentDescription = "Na Biblioteca",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showLibraryMenu,
                                    onDismissRequest = { showLibraryMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Remover da biblioteca") },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showLibraryMenu = false
                                            removeFromLibrary()
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Section
            Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                AsyncImage(
                    model = steamDetails?.get("background") ?: gameAssets?.libraryHeroImageUrl ?: coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.5f
                )

                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomStart) {
                    if (gameAssets?.logoImageUrl != null) {
                        AsyncImage(
                            model = gameAssets?.logoImageUrl,
                            contentDescription = null,
                            modifier = Modifier.height(80.dp).widthIn(max = 250.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = gameTitle,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = { showDownloadDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    if (isSearchingSources) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isResolvingLink) "PROCESSANDO..." else "BAIXAR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                if (isResolvingLink) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
                }

                // Info Cards
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val developers = (steamDetails?.get("developers") as? List<*>)?.joinToString(", ") ?: "Desconhecido"
                    val releaseDate = (steamDetails?.get("release_date") as? Map<*, *>)?.get("date") as? String ?: "Desconhecida"

                    Card(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Desenvolvedor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(developers, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                    Card(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Lançamento", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(releaseDate, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                    if (gameStats != null) {
                        Card(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Jogadores", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text("${gameStats?.playerCount ?: 0}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Genres & Categories
                val genres = (steamDetails?.get("genres") as? List<Map<String, Any>>)?.mapNotNull { it["description"] as? String }
                val categories = (steamDetails?.get("categories") as? List<Map<String, Any>>)?.mapNotNull { it["description"] as? String }

                if (!genres.isNullOrEmpty() || !categories.isNullOrEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        genres?.forEach { genre ->
                            SuggestionChip(onClick = {}, label = { Text(genre, style = MaterialTheme.typography.labelSmall) })
                        }
                        categories?.forEach { category ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(category, style = MaterialTheme.typography.labelSmall) },
                                colors = SuggestionChipDefaults.suggestionChipColors(labelColor = MaterialTheme.colorScheme.secondary)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Description
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Sobre o Jogo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        val descriptionHtml = steamDetails?.get("detailed_description") as? String ?: "Sem descrição disponível."

                        Box(modifier = Modifier.animateContentSize()) {
                            AndroidView(
                                factory = { context ->
                                    TextView(context).apply {
                                        setTextColor(0xFFFFFFFF.toInt())
                                        textSize = 14f
                                        if (!isDescriptionExpanded) {
                                            maxLines = 10
                                            ellipsize = android.text.TextUtils.TruncateAt.END
                                        }
                                    }
                                },
                                update = { view ->
                                    view.text = HtmlCompat.fromHtml(descriptionHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
                                    view.maxLines = if (isDescriptionExpanded) Integer.MAX_VALUE else 10
                                }
                            )
                        }

                        if (descriptionHtml.length > 500) {
                            TextButton(
                                onClick = { isDescriptionExpanded = !isDescriptionExpanded },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Icon(if (isDescriptionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isDescriptionExpanded) "VER MENOS" else "VER MAIS")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Reviews Section
                Text("Avaliações da Comunidade", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (totalReviewsCount > 0) "$totalReviewsCount ${if (totalReviewsCount == 1) "avaliação" else "avaliações"}" else "Sem avaliações ainda",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (Settings.accessToken.isNotBlank() && gameObjectId != null && !hasUserReviewed) {
                        Button(
                            onClick = { showReviewFormDialog = true },
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Avaliar", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Sort toggle
                if (hydraReviews.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("newest" to "Mais recentes", "mostVoted" to "Mais votadas").forEach { (sortByVal, label) ->
                            FilterChip(
                                selected = reviewSort == sortByVal,
                                onClick = {
                                    reviewSort = sortByVal
                                    scope.launch(Dispatchers.IO) {
                                        loadReviews(HydraApi.getClient(), Gson(), gameShop, gameObjectId, reviewSort) { reviews, total ->
                                            hydraReviews = reviews
                                            allReviews = reviews
                                            totalReviewsCount = total
                                        }
                                    }
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (hydraReviews.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.StarBorder, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Nenhuma avaliação ainda", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                } else {
                    hydraReviews.forEach { review ->
                        val isOwnReview = review.user?.id == Settings.userId
                        ReviewItem(
                            review = review,
                            isOwnReview = isOwnReview,
                            onUpvote = { handleVote(it, true) },
                            onDownvote = { handleVote(it, false) },
                            onDelete = { handleDeleteReview(it) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Media Gallery
                val screenshots = steamDetails?.get("screenshots") as? List<Map<String, Any>>
                if (!screenshots.isNullOrEmpty()) {
                    Text("Galeria", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(screenshots) { screenshot ->
                            Card(modifier = Modifier.width(280.dp).height(160.dp)) {
                                AsyncImage(
                                    model = screenshot["path_thumbnail"],
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // Requirements
                val requirements = steamDetails?.get("pc_requirements") as? Map<String, Any>
                if (requirements != null) {
                    Text("Requisitos do Sistema", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    val minimum = requirements["minimum"] as? String
                    val recommended = requirements["recommended"] as? String

                    if (minimum != null) {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Monitor, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Mínimos", fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                AndroidView(
                                    factory = { context -> TextView(context).apply { textSize = 12f; setTextColor(0xFFCCCCCC.toInt()) } },
                                    update = { view -> view.text = HtmlCompat.fromHtml(minimum, HtmlCompat.FROM_HTML_MODE_LEGACY) }
                                )
                            }
                        }
                    }

                    if (recommended != null) {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Monitor, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Recomendados", fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                AndroidView(
                                    factory = { context -> TextView(context).apply { textSize = 12f; setTextColor(0xFFCCCCCC.toInt()) } },
                                    update = { view -> view.text = HtmlCompat.fromHtml(recommended, HtmlCompat.FROM_HTML_MODE_LEGACY) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    if (showDownloadDialog) {
        ModalBottomSheet(
            onDismissRequest = { showDownloadDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Opções de Download",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (repacks.isEmpty() && localRepacks.isEmpty() && gameUris.isEmpty()) {
                    Text("Nenhuma fonte de download encontrada para este jogo.", modifier = Modifier.padding(vertical = 32.dp))
                }

                // API Repacks
                repacks.forEach { repack ->
                    DownloadOptionItem(
                        title = repack.title ?: "Sem título",
                        subtitle = "${repackerName(repack)} • ${repack.fileSize ?: "Desconhecido"}${if (repack.uploadDate != null) " • ${repack.uploadDate}" else ""}",
                        uris = repack.uris ?: emptyList(),
                        navController = navController,
                        mainActivity = mainActivity,
                        onLoading = { isResolvingLink = it }
                    )
                }

                // Local Search Repacks
                localRepacks.forEach { repack ->
                    DownloadOptionItem(
                        title = repack.title,
                        subtitle = "Fonte: ${repack.sourceName}${if (repack.fileSize != null) " • ${repack.fileSize}" else ""}",
                        uris = repack.uris,
                        navController = navController,
                        mainActivity = mainActivity,
                        onLoading = { isResolvingLink = it }
                    )
                }

                // Fallback Direct Links
                if (repacks.isEmpty() && localRepacks.isEmpty()) {
                    gameUris.forEach { uri ->
                        DownloadOptionItem(
                            title = "Link Direto",
                            subtitle = uri,
                            uris = listOf(uri),
                            navController = navController,
                            mainActivity = mainActivity,
                            onLoading = { isResolvingLink = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    /* ------------ review form dialog ------------ */
    if (showReviewFormDialog) {
        ReviewFormDialog(
            onDismiss = { showReviewFormDialog = false },
            onSubmit = handleCreateReview
        )
    }

    /* ------------ confirm delete review dialog ------------ */
    if (showConfirmDeleteReview) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteReview = false },
            title = { Text("Remover avaliação") },
            text = { Text("Tem certeza que deseja remover sua avaliação deste jogo? Esta ação não pode ser desfeita.") },
            confirmButton = {
                Button(onClick = {
                    // find the user's review for this game
                    val myReview = hydraReviews.find { it.user?.id == Settings.userId }
                    if (myReview?.id != null) handleDeleteReview(myReview.id)
                    else showConfirmDeleteReview = false
                }, colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Remover")
                }
            },
            dismissButton = { TextButton(onClick = { showConfirmDeleteReview = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ReviewItem(
    review: HydraReview,
    isOwnReview: Boolean,
    onUpvote: (String) -> Unit,
    onDownvote: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val reviewId = review.id ?: return
    val scoreText = when (review.score) {
        1 -> "Muito Negativa"
        2 -> "Negativa"
        3 -> "Neutra"
        4 -> "Positiva"
        5 -> "Muito Positiva"
        else -> ""
    }
    val scoreColor = when (review.score) {
        in 1..2 -> Color(0xFFFF4444)
        3 -> Color(0xFFFFD700)
        in 4..5 -> Color(0xFF4CAF50)
        else -> Color.Unspecified
    }
    val playTimeFormatted = (review.playTimeInSeconds ?: 0L).let { s ->
        val h = s / 3600
        if (h > 0) "${h}h jogadas" else "${s / 60}min jogadas"
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = review.user?.profileImageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(review.user?.displayName ?: "Usuário Hydra", style = MaterialTheme.typography.labelLarge)
                        if (review.score != null && scoreText.isNotBlank()) {
                            Surface(shape = RoundedCornerShape(4.dp), color = scoreColor.copy(alpha = 0.15f)) {
                                Text(scoreText, style = MaterialTheme.typography.labelSmall, color = scoreColor, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }
                    if (review.playTimeInSeconds != null && review.playTimeInSeconds > 0) {
                        Text(playTimeFormatted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (isOwnReview) {
                    IconButton(onClick = { onDelete(reviewId) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remover", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Stars display
            Row {
                repeat(5) { index ->
                    Icon(
                        imageVector = if (index < (review.score ?: 0)) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFFFFD700)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            AndroidView(
                factory = { context -> TextView(context).apply { textSize = 13f; setTextColor(0xFFEEEEEE.toInt()) } },
                update = { view -> view.text = HtmlCompat.fromHtml(review.reviewHtml ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Vote buttons
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (review.hasUpvoted == true) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color.Transparent,
                        modifier = Modifier
                            .clickable { onUpvote(reviewId) }
                            .padding(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Útil",
                                modifier = Modifier.size(16.dp),
                                tint = if (review.hasUpvoted == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("${review.upvotes ?: 0}", style = MaterialTheme.typography.labelSmall,
                                color = if (review.hasUpvoted == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (review.hasDownvoted == true) Color(0xFFFF4444).copy(alpha = 0.15f) else Color.Transparent,
                        modifier = Modifier
                            .clickable { onDownvote(reviewId) }
                            .padding(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.StarBorder,
                                contentDescription = "Não útil",
                                modifier = Modifier.size(16.dp),
                                tint = if (review.hasDownvoted == true) Color(0xFFFF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("${review.downvotes ?: 0}", style = MaterialTheme.typography.labelSmall,
                                color = if (review.hasDownvoted == true) Color(0xFFFF4444) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewFormDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, Int) -> Unit
) {
    var reviewText by remember { mutableStateOf("") }
    var score by remember { mutableStateOf(0) }
    val maxChars = 1000
    val remaining = maxChars - reviewText.length

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Avaliar este jogo", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // Score selector
                Text("Sua nota:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { s ->
                        val colors = when (s) {
                            in 1..2 -> listOf(Color(0xFFFF4444), Color(0xFFFF8888))
                            3 -> listOf(Color(0xFFFFD700), Color(0xFFFFEE88))
                            in 4..5 -> listOf(Color(0xFF4CAF50), Color(0xFF88CC88))
                            else -> listOf(Color.Unspecified, Color.Unspecified)
                        }
                        val isSelected = score >= s
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "$s estrelas",
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { score = s },
                            tint = when {
                                score == s -> colors[0]
                                isSelected -> colors[1]
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            }
                        )
                    }
                }
                if (score > 0) {
                    val scoreWords = when (score) {
                        1 -> "Muito ruim"
                        2 -> "Ruim"
                        3 -> "Neutro"
                        4 -> "Bom"
                        5 -> "Ótimo"
                        else -> ""
                    }
                    Text(scoreWords, style = MaterialTheme.typography.bodySmall, color = when (score) {
                        in 1..2 -> Color(0xFFFF4444)
                        3 -> Color(0xFFFFD700)
                        in 4..5 -> Color(0xFF4CAF50)
                        else -> Color.Unspecified
                    })
                }
                Spacer(modifier = Modifier.height(12.dp))
                // Review text
                OutlinedTextField(
                    value = reviewText,
                    onValueChange = { if (it.length <= maxChars) reviewText = it },
                    label = { Text("Seu comentário (máx. $maxChars caracteres)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp),
                    minLines = 3
                )
                Text(
                    "$remaining/$maxChars restantes",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (remaining < 50) Color(0xFFFF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(reviewHtmlToParagraph(reviewText), score) },
                enabled = score > 0 && reviewText.isNotBlank()
            ) {
                Text("Enviar avaliação")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

fun reviewHtmlToParagraph(text: String): String {
    // Simple paragraph wrapping for basic HTML
    return text.split("\n\n", "\n")
        .filter { it.isNotBlank() }
        .joinToString("") { "<p>${android.text.TextUtils.htmlEncode(it)}</p>\n" }
}

fun repackerName(repack: HydraRepack): String {
    return repack.repacker ?: "Hydra API"
}

fun getHostFromUrl(url: String): String? {
    return try {
        val uri = java.net.URI(url)
        val host = uri.host?.lowercase() ?: ""
        when {
            host.contains("gofile.io") -> "GoFile.io"
            host.contains("mediafire.com") -> "MediaFire.com"
            host.contains("mega.nz") -> "Mega.nz"
            host.contains("1fichier.com") -> "1Fichier.com"
            host.contains("pixeldrain.com") -> "PixelDrain.com"
            host.contains("qiwi.gg") -> "Qiwi.gg"
            host.contains("buzzheavier.com") -> "BuzzHeavier.com"
            host.contains("krakenfiles.com") -> "KrakenFiles.com"
            host.contains("datanodes.to") -> "DataNodes.to"
            host.contains("rapidgator.net") -> "Rapidgator.net"
            host.contains("uptobox.com") -> "Uptobox.com"
            host.contains("ddownload.com") -> "DDownload.com"
            else -> host.replace("www.", "").replaceFirstChar { it.uppercase() }.ifBlank { null }
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun DownloadOptionItem(title: String, subtitle: String, uris: List<String>, navController: NavController, mainActivity: MainActivity, onLoading: (Boolean) -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            uris.forEach { uri ->
                Button(
                    onClick = {
                        val isSupportedByScript = isUrlSupportedByScript(uri)
                        if (Settings.useDownloadScripts && isSupportedByScript) {
                            triggerAria2Download(uri, mainActivity, title, navController = navController, onLoading = onLoading)
                        } else {
                            openBrowser(uri, mainActivity, navController)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    val host = getHostFromUrl(uri)
                    val label = if (host != null) "Baixar via $host" else "Baixar Agora"
                    Text(label)
                }
            }
        }
    }
}

fun isUrlSupportedByScript(url: String): Boolean {
    val lowerUrl = url.lowercase()
    return (lowerUrl.contains("gofile.io") && Settings.useGofileScript) ||
           ((lowerUrl.contains("buzzheavier.com") || lowerUrl.contains("bzzhr.co")) && Settings.useBuzzheavierScript) ||
           (lowerUrl.contains("pixeldrain.com") && Settings.usePixeldrainScript) ||
           (lowerUrl.contains("mediafire.com") && Settings.useMediafireScript) ||
           (lowerUrl.contains("datanodes.to") && Settings.useDatanodesScript) ||
           (lowerUrl.contains("fuckingfast.co") && Settings.useFuckingfastScript) ||
           (lowerUrl.contains("rootz.so") && Settings.useRootzScript)
}


fun openBrowser(url: String, activity: MainActivity, navController: NavController) {
    if (Settings.useExternalBrowser && Settings.selectedExternalBrowserPackage.isNotBlank()) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.setPackage(Settings.selectedExternalBrowserPackage)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            navController.navigate("browser/$encodedUrl")
        }
    } else {
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        navController.navigate("browser/$encodedUrl")
    }
}

fun triggerAria2Download(
    url: String,
    activity: MainActivity,
    title: String,
    navController: NavController? = null,
    userAgent: String? = null,
    cookies: String? = null,
    referer: String? = null,
    onLoading: (Boolean) -> Unit = {}
) {
    val downloadPath = Settings.downloadPath
    val downloadId = generateGid(url)
    if (DownloadManager.activeDownloads.none { it.id == downloadId }) {
        DownloadManager.activeDownloads.add(DownloadProgress(id = downloadId, title = title, progress = 0.1f, status = "Baixando via Aria2..."))
    }

    activity.lifecycleScope.launch(Dispatchers.Main) {
        try {
            onLoading(true)
            var finalUrl = url
            var header: String? = null
            var resolutionFailed = false

            if (url.contains("gofile.io") && Settings.useGofileScript) {
                withContext(Dispatchers.IO) {
                    val id = url.trimEnd('/').split("/").lastOrNull()?.split("?")?.firstOrNull()
                    if (id != null) {
                        val token = GofileApi.authorize()
                        if (token != null) {
                            val directLink = GofileApi.getDownloadLink(id, token)
                            if (directLink != null) {
                                GofileApi.checkDownloadUrl(directLink, token)
                                finalUrl = directLink
                                header = "Cookie: accountToken=$token"
                            } else { resolutionFailed = true }
                        } else { resolutionFailed = true }
                    } else { resolutionFailed = true }
                }
            } else if ((url.contains("buzzheavier.com") || url.contains("bzzhr.co")) && Settings.useBuzzheavierScript) {
                withContext(Dispatchers.IO) {
                    val directLink = BuzzHeavierApi.getDirectLink(url)
                    if (directLink != null) { finalUrl = directLink } else { resolutionFailed = true }
                }
            } else if (url.contains("pixeldrain.com") && Settings.usePixeldrainScript) {
                withContext(Dispatchers.IO) {
                    val directLink = PixelDrainApi.unlock(url)
                    if (directLink != null) { finalUrl = directLink } else { resolutionFailed = true }
                }
            } else if (url.contains("mediafire.com") && Settings.useMediafireScript) {
                withContext(Dispatchers.IO) {
                    val directLink = MediafireApi.getDownloadUrl(url)
                    if (directLink != null) { finalUrl = directLink } else { resolutionFailed = true }
                }
            } else if (url.contains("datanodes.to") && Settings.useDatanodesScript) {
                withContext(Dispatchers.IO) {
                    val directLink = DatanodesApi.getDownloadUrl(url)
                    if (directLink != null) { finalUrl = directLink } else { resolutionFailed = true }
                }
            } else if (url.contains("fuckingfast.co") && Settings.useFuckingfastScript) {
                withContext(Dispatchers.IO) {
                    val directLink = FuckingFastApi.getDirectLink(url)
                    if (directLink != null) { finalUrl = directLink } else { resolutionFailed = true }
                }
            } else if (url.contains("rootz.so") && Settings.useRootzScript) {
                withContext(Dispatchers.IO) {
                    val directLink = RootzApi.getDownloadUrl(url)
                    if (directLink != null) { finalUrl = directLink } else { resolutionFailed = true }
                }
            }

            if (resolutionFailed) {
                onLoading(false)
                val index = DownloadManager.activeDownloads.indexOfFirst { it.id == downloadId }
                if (index != -1) { DownloadManager.activeDownloads.removeAt(index) }

                if (Settings.fallbackToBrowserOnError && navController != null) {
                    android.widget.Toast.makeText(activity, "Falha na automação. Abrindo navegador...", android.widget.Toast.LENGTH_SHORT).show()
                    openBrowser(url, activity, navController)
                } else {
                    android.widget.Toast.makeText(activity, "Erro ao processar link automático. Tente pelo navegador.", android.widget.Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            val rpcUrl = "http://localhost:${Settings.aria2RpcPort}/jsonrpc"
            val rpcSecret = Settings.aria2RpcSecret
            val maxConn = Settings.aria2MaxConnections

            val client = OkHttpClient()
            val gson = Gson()

            val params = mutableListOf<Any>()
            if (rpcSecret.isNotBlank()) {
                params.add("token:$rpcSecret")
            }
            params.add(listOf(finalUrl) as Any)
            val options = mutableMapOf<String, Any>(
                "dir" to downloadPath,
                "max-connection-per-server" to maxConn.toString(),
                "split" to maxConn.toString(),
                "user-agent" to (userAgent ?: Settings.aria2UserAgent),
                "async-dns" to "false",
                "check-certificate" to "false",
                "max-tries" to "10",
                "retry-wait" to "5",
                "file-allocation" to "none",
                "gid" to downloadId
            )

            val headersList = mutableListOf<String>()
            if (header != null) headersList.add(header!!)
            if (!cookies.isNullOrBlank()) headersList.add("Cookie: $cookies")
            if (!referer.isNullOrBlank()) headersList.add("Referer: $referer")

            if (headersList.isNotEmpty()) {
                options["header"] = headersList
            }

            params.add(options)

            val rpcRequestMap = mapOf(
                "jsonrpc" to "2.0",
                "id" to "add",
                "method" to "aria2.addUri",
                "params" to params
            )

            val requestBody = gson.toJson(rpcRequestMap).toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(rpcUrl)
                .post(requestBody)
                .build()

            withContext(Dispatchers.IO) {
                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val respBody = response.body?.string()
                            val respMap = gson.fromJson(respBody, Map::class.java)
                            val gid = respMap["result"] as? String

                            withContext(Dispatchers.Main) {
                                val index = DownloadManager.activeDownloads.indexOfFirst { it.id == downloadId }
                                if (index != -1) {
                                    DownloadManager.activeDownloads[index] = DownloadManager.activeDownloads[index].copy(gid = gid)
                                }
                                onLoading(false)
                                android.widget.Toast.makeText(activity, "Download adicionado ao Aria2", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } else {
                            onLoading(false)
                            startAria2InTerminal(finalUrl, activity, title, downloadId, downloadPath, header, userAgent, cookies, referer)
                        }
                    }
                } catch (e: Exception) {
                    onLoading(false)
                    startAria2InTerminal(finalUrl, activity, title, downloadId, downloadPath, header, userAgent, cookies, referer)
                }
            }
        } catch (e: Exception) {
            onLoading(false)
            android.widget.Toast.makeText(activity, "Erro inesperado: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
}

private fun startAria2InTerminal(
    url: String,
    activity: MainActivity,
    title: String,
    downloadId: String,
    downloadPath: String,
    header: String? = null,
    userAgent: String? = null,
    cookies: String? = null,
    referer: String? = null
) {
    activity.lifecycleScope.launch(Dispatchers.Main) {
        val maxConn = Settings.aria2MaxConnections

        var extraArgs = ""
        if (header != null) extraArgs += " --header=\"$header\""
        if (!cookies.isNullOrBlank()) extraArgs += " --header=\"Cookie: $cookies\""
        if (!referer.isNullOrBlank()) extraArgs += " --header=\"Referer: $referer\""

        val ua = userAgent ?: Settings.aria2UserAgent

        // Run as standalone download in terminal to avoid port conflicts with daemon
        val aria2Cmd = "aria2c --dir=\"$downloadPath\" --max-connection-per-server=$maxConn --split=$maxConn " +
                "--user-agent=\"$ua\" --async-dns=false --check-certificate=false " +
                "--max-tries=10 --retry-wait=5 --file-allocation=none --gid=$downloadId$extraArgs \"$url\""

        val initialArgs = listOf("sh", "-c", aria2Cmd)

        val service = activity.sessionBinder?.getService()
        if (service != null) {
            val sessionId = "Aria2Download_$downloadId"
            val dummyView = com.termux.view.TerminalView(activity, null)
            val client = TerminalBackEnd(dummyView, activity).apply {
                this.sessionId = "Aria2Download"
            }
            activity.sessionBinder?.createSession(sessionId, client, activity, WorkingMode.ALPINE, initialArgs = initialArgs)
            android.widget.Toast.makeText(activity, "Aria2 iniciado no terminal", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
