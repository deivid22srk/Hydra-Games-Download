package com.rk.terminal.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.launch

data class HydraGame(
    @SerializedName("name") val name: String,
    @SerializedName("uris") val uris: List<String>? = null
)

data class HydraSource(
    @SerializedName("downloads") val downloads: List<HydraGame>? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    var searchQuery by remember { mutableStateOf("") }
    var games by remember { mutableStateOf<List<HydraGame>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Pesquisar jogo") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = {
                    if (searchQuery.isNotBlank()) {
                        isSearching = true
                        val sources = Settings.hydraSources
                        scope.launch {
                            val allGames = withContext(Dispatchers.IO) {
                                val list = mutableListOf<HydraGame>()
                                val client = OkHttpClient()
                                val gson = Gson()

                                sources.forEach { url ->
                                    try {
                                        val request = Request.Builder().url(url).build()
                                        client.newCall(request).execute().use { response ->
                                            if (response.isSuccessful) {
                                                val body = response.body?.string()
                                                val source = gson.fromJson(body, HydraSource::class.java)
                                                source.downloads?.let { list.addAll(it) }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                list
                            }

                            val filtered = allGames.filter { it.name.contains(searchQuery, ignoreCase = true) }
                            games = filtered
                            isSearching = false
                        }
                    }
                }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(games) { game ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = game.name, style = MaterialTheme.typography.titleMedium)
                            game.uris?.firstOrNull()?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (games.isEmpty() && searchQuery.isNotEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Nenhum jogo encontrado.")
                        }
                    }
                }
            }
        }
    }
}
