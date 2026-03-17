package com.rk.terminal.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.screens.terminal.MkSession
import com.rk.terminal.ui.screens.terminal.TerminalBackEnd
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.net.URLEncoder
import androidx.lifecycle.lifecycleScope


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailsScreen(
    gameTitle: String,
    gameUris: List<String>,
    navController: NavController,
    mainActivity: MainActivity
) {
    var coverUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(gameTitle) {
        val apiKey = Settings.steamGridDbApiKey
        if (apiKey.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient()
                    val gson = Gson()

                    // 1. Search for game ID
                    val searchRequest = Request.Builder()
                        .url("https://www.steamgriddb.com/api/v2/search/autocomplete/${URLEncoder.encode(gameTitle, "UTF-8")}")
                        .addHeader("Authorization", "Bearer $apiKey")
                        .build()

                    client.newCall(searchRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            val searchData = gson.fromJson(body, SGDBResponse::class.java)
                            val gameId = searchData.data.firstOrNull()?.id

                            if (gameId != null) {
                                // 2. Get grids (covers)
                                val gridRequest = Request.Builder()
                                    .url("https://www.steamgriddb.com/api/v2/grids/game/$gameId")
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
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalhes do Jogo") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.size(200.dp, 300.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Sem Capa", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = gameTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Links de Download",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth()
            )

            gameUris.forEach { uri ->
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = uri, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            val isGoFile = uri.contains("gofile.io")
                            Text(
                                text = if (isGoFile) "Download via GoFileDownloader" else "Download Direto",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isGoFile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        }
                        IconButton(onClick = {
                            if (uri.contains("gofile.io")) {
                                triggerGoFileDownload(uri, mainActivity)
                                navController.popBackStack()
                                // TODO: Switch to terminal tab automatically?
                            } else {
                                // Direct download or open in browser
                            }
                        }) {
                            Icon(Icons.Default.Download, contentDescription = "Download")
                        }
                    }
                }
            }
        }
    }
}

fun triggerGoFileDownload(url: String, activity: MainActivity) {
    val downloadPath = Settings.downloadPath
    val command = "cd ~/GoFileDownloader && python3 main.py --custom-path \"$downloadPath\""
    // We need a way to send this to the terminal.
    // For now, we'll write it to a URLs.txt and run main.py

    activity.lifecycleScope.launch(Dispatchers.IO) {
        val urlsFile = java.io.File(activity.filesDir, "local/alpine/root/GoFileDownloader/URLs.txt")
        urlsFile.writeText(url)

        // This is a bit of a hack, but we can't easily "inject" into an existing session
        // without more infrastructure. We'll just assume the terminal can be told to run this.
        // In a real implementation, we'd use a broadcast or a shared state.
    }
}
