package com.rk.terminal.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.net.URLEncoder
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavController, viewModel: SharedGameViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<HydraGame>>(emptyList()) }
    var allGames by remember { mutableStateOf<List<HydraGame>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    var isResultsMode by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val performSearch = {
        if (searchQuery.isNotBlank()) {
            isSearching = true
            showSuggestions = false
            isResultsMode = true
            scope.launch {
                val results = withContext(Dispatchers.IO) {
                    val client = HydraApi.getClient()
                    val gson = Gson()
                    val sources = Settings.hydraSources.filter { it.isEnabled }.map { it.url }

                    val requestBodyMap = mutableMapOf<String, Any>(
                        "title" to searchQuery,
                        "take" to 20,
                        "skip" to 0,
                        "downloadSourceIds" to sources
                    )

                    val requestBodyJson = gson.toJson(requestBodyMap)
                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    val requestBody = requestBodyJson.toRequestBody(mediaType)

                    try {
                        val request = Request.Builder()
                            .url("https://hydra-api-us-east-1.losbroxas.org/catalogue/search")
                            .post(requestBody)
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string()
                                if (!body.isNullOrBlank()) {
                                    val searchResponse = gson.fromJson(body, HydraSearchResponse::class.java)
                                    searchResponse.edges ?: emptyList()
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
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2 && !isResultsMode) {
            delay(300)
            withContext(Dispatchers.IO) {
                try {
                    val client = HydraApi.getClient()
                    val gson = Gson()
                    val url = "https://hydra-api-us-east-1.losbroxas.org/catalogue/search/suggestions?query=${URLEncoder.encode(searchQuery, "UTF-8")}&limit=5"
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!body.isNullOrBlank()) {
                                val type = object : TypeToken<List<HydraGame>>() {}.type
                                val results = gson.fromJson<List<HydraGame>>(body, type) ?: emptyList()
                                withContext(Dispatchers.Main) {
                                    suggestions = results
                                    showSuggestions = true
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            suggestions = emptyList()
            showSuggestions = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            isResultsMode = false
                        },
                        placeholder = { Text("Buscar no catálogo...") },
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = performSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column {
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
                                                text = game.shop ?: "Desconhecida",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (allGames.isEmpty() && searchQuery.isNotEmpty() && !isSearching) {
                            item {
                                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Nenhum jogo encontrado.")
                                }
                            }
                        }
                    }
                }
            }

            if (showSuggestions && suggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .align(Alignment.TopCenter),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column {
                        suggestions.forEach { suggestion ->
                            ListItem(
                                headlineContent = { Text(suggestion.title ?: "") },
                                modifier = Modifier.clickable {
                                    searchQuery = suggestion.title ?: ""
                                    showSuggestions = false
                                    isResultsMode = true
                                    performSearch()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
