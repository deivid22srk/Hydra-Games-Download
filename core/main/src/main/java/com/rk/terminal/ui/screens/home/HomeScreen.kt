package com.rk.terminal.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.terminal.ui.routes.MainActivityRoutes
import com.rk.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.net.URLEncoder
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush


import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: SharedGameViewModel) {
    var trendingGames by remember { mutableStateOf<List<HydraGame>>(emptyList()) }
    var weeklyGames by remember { mutableStateOf<List<HydraGame>>(emptyList()) }
    var achievementGames by remember { mutableStateOf<List<HydraGame>>(emptyList()) }
    var isFetching by remember { mutableStateOf(false) }
    var isChoosingRandom by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun fetchHydraCatalogue(endpoint: String): List<HydraGame> {
        return withContext(Dispatchers.IO) {
            val client = HydraApi.getClient()
            val gson = Gson()
            val sources = Settings.hydraSources.filter { it.isEnabled }.map { it.url }

            val urlBuilder = StringBuilder("https://hydra-api-us-east-1.losbroxas.org/catalogue/$endpoint?take=15&skip=0")
            sources.forEach { sourceId ->
                urlBuilder.append("&downloadSourceIds[]=").append(URLEncoder.encode(sourceId, "UTF-8"))
            }

            try {
                val request = Request.Builder().url(urlBuilder.toString()).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val type = object : TypeToken<List<HydraGame>>() {}.type
                            gson.fromJson<List<HydraGame>>(body, type) ?: emptyList()
                        } else emptyList()
                    } else emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    LaunchedEffect(Unit) {
        isFetching = true
        trendingGames = fetchHydraCatalogue("hot")
        weeklyGames = fetchHydraCatalogue("weekly")
        achievementGames = fetchHydraCatalogue("achievements")
        isFetching = false
    }

    val surpriseMe = {
        if (!isChoosingRandom) {
        isChoosingRandom = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient()
                    val request = Request.Builder().url("https://steam250.com/most_played").build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val regex = """href="?https://club.steam250.com/app/(\d+)"?\s+title="?([^">]+)"?""".toRegex()
                            val matches = regex.findAll(body).toList()
                            if (matches.isNotEmpty()) {
                                val randomMatch = matches.random()
                                val steamId = randomMatch.groupValues[1]
                                val title = randomMatch.groupValues[2].replace("&#x20;", " ")
                                HydraGame(
                                    title = title,
                                    objectId = steamId,
                                    shop = "steam",
                                    libraryImageUrl = "https://shared.steamstatic.com/store_item_assets/steam/apps/$steamId/header.jpg"
                                )
                            } else null
                        } else null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            if (result != null) {
                viewModel.setGame(result.title ?: "Unknown", emptyList())
                viewModel.selectedGameObjectId = result.objectId
                viewModel.selectedGameShop = result.shop
                viewModel.selectedGameCover = result.libraryImageUrl
                val encodedTitle = URLEncoder.encode(result.title ?: "Unknown", "UTF-8")
                navController.navigate("game_details/$encodedTitle")
            }
            isChoosingRandom = false
        }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Hydra Launcher",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { navController.navigate(com.rk.terminal.ui.routes.MainActivityRoutes.Search.route) }) {
                        Icon(Icons.Default.Search, contentDescription = "Pesquisar")
                    }
                    IconButton(onClick = { navController.navigate("profile") }) {
                        if (Settings.userProfileImageUrl.isNotBlank()) {
                            AsyncImage(
                                model = Settings.userProfileImageUrl,
                                contentDescription = "Perfil",
                                modifier = Modifier.size(24.dp).clip(CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Person, contentDescription = "Perfil")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        if (isFetching) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Feature Banner (Surprise Me)
                val infiniteTransition = rememberInfiniteTransition(label = "banner")
                val offset by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1000f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(3000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "offset"
                )

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(160.dp),
                    onClick = { surpriseMe() },
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isChoosingRandom) {
                            val brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.primaryContainer,
                                ),
                                start = androidx.compose.ui.geometry.Offset(offset, offset),
                                end = androidx.compose.ui.geometry.Offset(offset + 500f, offset + 500f)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(brush)
                                    .blur(20.dp)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(24.dp)
                        ) {
                            Text(
                                "Descubra algo novo",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Deixe o Hydra escolher um jogo para você",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        if (isChoosingRandom) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.CenterEnd).padding(24.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                GameCarouselSection("Em Destaque", trendingGames, navController, viewModel)
                GameCarouselSection("Bombando na Semana", weeklyGames, navController, viewModel)
                GameCarouselSection("Conquistas Desafiadoras", achievementGames, navController, viewModel)

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun GameCarouselSection(
    title: String,
    games: List<HydraGame>,
    navController: NavController,
    viewModel: SharedGameViewModel
) {
    if (games.isEmpty()) return

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(games) { game ->
                Card(
                    modifier = Modifier.width(140.dp),
                    onClick = {
                        val gameUris = game.uris ?: game.downloadSources?.flatMap { it.uris ?: emptyList() } ?: emptyList()
                        viewModel.setGame(game.title ?: "Unknown", gameUris)
                        viewModel.selectedGameObjectId = game.objectId
                        viewModel.selectedGameShop = game.shop
                        viewModel.selectedGameCover = game.libraryImageUrl

                        val encodedTitle = URLEncoder.encode(game.title ?: "Unknown", "UTF-8")
                        navController.navigate("game_details/$encodedTitle")
                    }
                ) {
                    Column {
                        Box(modifier = Modifier.height(190.dp).fillMaxWidth()) {
                            if (game.libraryImageUrl != null) {
                                AsyncImage(
                                    model = game.libraryImageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Gamepad, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Text(
                            text = game.title ?: "Sem nome",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}
