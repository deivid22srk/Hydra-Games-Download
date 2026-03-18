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
import kotlinx.coroutines.launch
import java.net.URLEncoder


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: SharedGameViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    var allGames by remember { mutableStateOf<List<HydraGame>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val uniqueSources = remember(allGames) {
        listOf("Tudo") + allGames.mapNotNull { it.sourceName }.distinct().sorted()
    }

    val filteredGames = remember(allGames, selectedTabIndex, uniqueSources) {
        if (selectedTabIndex == 0 || selectedTabIndex >= uniqueSources.size) {
            allGames
        } else {
            val source = uniqueSources[selectedTabIndex]
            allGames.filter { it.sourceName == source }
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
                        text = "Buscar Jogos",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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

            if (allGames.isNotEmpty() && !isSearching) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    divider = {}
                ) {
                    uniqueSources.forEachIndexed { index, sourceName ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(sourceName) }
                        )
                    }
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
                    items(filteredGames) { game ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                viewModel.setGame(game.title ?: "Unknown", game.uris ?: emptyList())
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
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Gamepad, contentDescription = null)
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
                                            text = game.sourceName ?: "Desconhecida",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        game.uris?.firstOrNull()?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (allGames.isEmpty() && searchQuery.isNotEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Nenhum jogo encontrado.", style = MaterialTheme.typography.bodyLarge)
                                    Text("Tente outro nome ou adicione mais fontes.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else if (allGames.isEmpty() && searchQuery.isEmpty()) {
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
