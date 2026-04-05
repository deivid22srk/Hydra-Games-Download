package com.rk.terminal.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.rk.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(navController: NavController, viewModel: SharedGameViewModel) {
    var libraryGames by remember { mutableStateOf<List<HydraGame>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedShop by remember { mutableStateOf<String?>(null) }
    var loginRequired by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val fetchLibrary = {
        isLoading = true
        fetchError = null
        loginRequired = false

        scope.launch(Dispatchers.IO) {
            try {
                // Revalidate session first in case token is expiring
                HydraApi.revalidateSession()

                val userId = Settings.userId
                if (userId.isBlank() || Settings.accessToken.isBlank()) {
                    loginRequired = true
                    libraryGames = emptyList()
                    withContext(Dispatchers.Main) { isLoading = false }
                    return@launch
                }

                val client = HydraApi.getClient()
                val gson = Gson()
                val url = "https://hydra-api-us-east-1.losbroxas.org/users/$userId/library"
                val request = Request.Builder().url(url).build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val data = try {
                            gson.fromJson(body, object : TypeToken<Map<String, Any>>() {}.type)
                        } catch (e: JsonSyntaxException) {
                            null
                        }

                        val libraryArray = data?.get("library") as? java.util.ArrayList<*>

                        val games = if (libraryArray != null) {
                            gson.fromJson(
                                gson.toJson(libraryArray),
                                object : TypeToken<List<HydraGame>>() {}.type
                            ) ?: emptyList()
                        } else {
                            // Fallback: try parsing the response as a direct list
                            try {
                                gson.fromJson(body, object : TypeToken<List<HydraGame>>() {}.type) ?: emptyList()
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }

                        withContext(Dispatchers.Main) {
                            libraryGames = games
                            if (games.isEmpty()) {
                                android.util.Log.d("LibraryScreen", "Library is empty (no games added yet)")
                            } else {
                                android.util.Log.d("LibraryScreen", "Loaded ${games.size} games from library")
                            }
                            isLoading = false
                        }
                    } else if (response.code == 401) {
                        withContext(Dispatchers.Main) {
                            loginRequired = true
                            libraryGames = emptyList()
                            isLoading = false
                        }
                    } else {
                        val errorBody = response.body?.string()?.take(200)
                        android.util.Log.e("LibraryScreen", "API returned ${response.code}: $errorBody")
                        withContext(Dispatchers.Main) {
                            fetchError = "Erro ao carregar: HTTP ${response.code}"
                            isLoading = false
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LibraryScreen", "Fetch error", e)
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    fetchError = "Erro de rede: ${e.message}"
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchLibrary()
    }

    val filteredGames = libraryGames.filter {
        val matchesSearch = it.title?.contains(searchQuery, ignoreCase = true) == true
        val matchesShop = selectedShop == null || it.shop == selectedShop
        matchesSearch && matchesShop
    }

    val shops = libraryGames.mapNotNull { it.shop }.distinct()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Minha Biblioteca", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { fetchLibrary() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Atualizar")
                        }
                    }
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Pesquisar na biblioteca...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Shop Filters
                if (shops.isNotEmpty()) {
                    ScrollableTabRow(
                        selectedTabIndex = if (selectedShop == null) 0 else shops.indexOf(selectedShop) + 1,
                        edgePadding = 16.dp,
                        divider = {},
                        containerColor = Color.Transparent,
                        indicator = {}
                    ) {
                        FilterChip(
                            selected = selectedShop == null,
                            onClick = { selectedShop = null },
                            label = { Text("Todos") },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        shops.forEach { shop ->
                            FilterChip(
                                selected = selectedShop == shop,
                                onClick = { selectedShop = shop },
                                label = { Text(shop.uppercase()) },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (loginRequired || Settings.accessToken.isBlank()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Faça login para ver sua biblioteca", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else if (fetchError != null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(fetchError ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { fetchLibrary() }) {
                        Text("Tentar novamente")
                    }
                }
            }
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filteredGames.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LibraryBooks, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Nenhum jogo encontrado na biblioteca",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Adicione jogos a partir da tela de detalhes do jogo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(padding)
            ) {
                items(filteredGames) { game ->
                    LibraryGameCard(game, navController, viewModel)
                }
            }
        }
    }
}

@Composable
fun LibraryGameCard(game: HydraGame, navController: NavController, viewModel: SharedGameViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                viewModel.setGame(game.title ?: "Unknown", emptyList())
                viewModel.selectedGameObjectId = game.objectId
                viewModel.selectedGameShop = game.shop
                viewModel.selectedGameCover = game.libraryImageUrl
                val encodedTitle = URLEncoder.encode(game.title ?: "Unknown", "UTF-8")
                navController.navigate("game_details/$encodedTitle")
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column {
            Box(modifier = Modifier.height(180.dp).fillMaxWidth()) {
                AsyncImage(
                    model = game.libraryImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = game.title ?: "Sem nome",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.CENTER_VERTICALLY) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatPlayTime(game.playTimeInSeconds ?: 0L),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp
                    )
                }

                game.lastTimePlayed?.let { lastPlayed ->
                    Text(
                        text = "Visto: ${formatLastPlayed(lastPlayed)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

fun formatPlayTime(seconds: Long): String {
    val minutes = seconds / 60
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    return "${hours}h ${minutes % 60}m"
}

fun formatLastPlayed(isoDate: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val date = sdf.parse(isoDate)
        val outSdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        outSdf.format(date!!)
    } catch (e: Exception) {
        isoDate.split("T").firstOrNull() ?: "Desconhecido"
    }
}
