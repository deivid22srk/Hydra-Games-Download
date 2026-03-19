package com.rk.terminal.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.net.URLEncoder
import coil.compose.AsyncImage


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: SharedGameViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    var allGames by remember { mutableStateOf<List<HydraGame>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Tendências", "Semanal", "Conquistas", "Busca")

    suspend fun fetchHydraCatalogue(endpoint: String) {
        isSearching = true
        val results = withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val gson = Gson()
            val sources = Settings.hydraSources.filter { it.isEnabled }.map { it.url }

            val urlBuilder = StringBuilder("https://hydra-api-us-east-1.losbroxas.org/catalogue/$endpoint?take=20&skip=0")
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
        allGames = results
        isSearching = false
    }

    LaunchedEffect(selectedTabIndex) {
        when (selectedTabIndex) {
            0 -> fetchHydraCatalogue("hot")
            1 -> fetchHydraCatalogue("weekly")
            2 -> fetchHydraCatalogue("achievements")
            3 -> { /* Search mode, don't fetch automatically */ }
        }
    }

    val surpriseMe = {
        isSearching = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient()
                    val request = Request.Builder().url("https://steam250.com/most_played").build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            // Regex to extract title and objectId (Steam app ID)
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
                allGames = listOf(result)
            }
            isSearching = false
        }
    }

    val performSearch = {
        if (searchQuery.isNotBlank()) {
            isSearching = true
            selectedTabIndex = 0
            val sources = Settings.hydraSources.filter { it.isEnabled }
            scope.launch {
                val results = withContext(Dispatchers.IO) {
                    val list = mutableListOf<HydraGame>()
                    val client = OkHttpClient()
                    val gson = Gson()

                    sources.forEach { config ->
                        try {
                            val request = Request.Builder().url(config.url).build()
                            client.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val body = response.body?.string()
                                    if (!body.isNullOrBlank()) {
                                        val source = gson.fromJson(body, HydraSource::class.java)
                                        val sourceName = source?.name ?: config.url.split("/").getOrNull(2) ?: "Desconhecida"
                                        source?.downloads?.filterNotNull()?.forEach { game ->
                                            game.sourceName = sourceName
                                            list.add(game)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    list
                }

                allGames = results.filter { it.title?.contains(searchQuery, ignoreCase = true) == true }
                isSearching = false
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
                    TextButton(onClick = { surpriseMe() }) {
                        Text("SURPREENDA-ME")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            if (selectedTabIndex == 3) {
                Box(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Digite o nome do jogo...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                TextButton(onClick = performSearch) {
                                    Text("BUSCAR")
                                }
                            }
                        }
                    )
                }
            }

            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(allGames) { game ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
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
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(80.dp, 45.dp)
                                ) {
                                    if (game.libraryImageUrl != null) {
                                        AsyncImage(
                                            model = game.libraryImageUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Gamepad, contentDescription = null)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = game.title ?: "Sem nome",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = game.shop ?: game.sourceName ?: "Desconhecida",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (allGames.isEmpty() && selectedTabIndex == 3 && searchQuery.isNotEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Nenhum jogo encontrado.", style = MaterialTheme.typography.bodyLarge)
                                    Text("Tente outro nome ou adicione mais fontes.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else if (allGames.isEmpty() && selectedTabIndex == 3 && searchQuery.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Use a barra de pesquisa para buscar jogos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
