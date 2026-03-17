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
import java.net.URLEncoder
import androidx.lifecycle.lifecycleScope
import com.rk.terminal.ui.routes.MainActivityRoutes
import com.rk.terminal.ui.screens.settings.WorkingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailsScreen(
    viewModel: SharedGameViewModel,
    navController: NavController,
    mainActivity: MainActivity
) {
    val gameTitle = viewModel.selectedGameTitle
    val gameUris = viewModel.selectedGameUris
    var coverUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(gameTitle) {
        val apiKey = Settings.steamGridDbApiKey
        if (apiKey.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient()
                    val gson = Gson()

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

            if (gameUris.isNotEmpty()) {
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
                                    // Switch to terminal would be better here
                                } else {
                                    // Handle direct link
                                }
                            }) {
                                Icon(Icons.Default.Download, contentDescription = "Download")
                            }
                        }
                    }
                }
            } else {
                Text("Nenhum link de download disponível para este jogo.")
            }
        }
    }
}

fun triggerGoFileDownload(url: String, activity: MainActivity) {
    val downloadPath = Settings.downloadPath

    activity.lifecycleScope.launch(Dispatchers.IO) {
        try {
            // 1. Write URL to URLs.txt
            val rootfsDir = java.io.File(activity.filesDir, "local/alpine")
            val downloaderDir = java.io.File(rootfsDir, "root/GoFileDownloader")
            if (!downloaderDir.exists()) downloaderDir.mkdirs()

            val urlsFile = java.io.File(downloaderDir, "URLs.txt")
            urlsFile.writeText(url + "\n")

            // 2. Prepare command
            val cmd = "cd ~/GoFileDownloader && python3 main.py --custom-path \"$downloadPath\"\n"

            withContext(Dispatchers.Main) {
                // 3. Find or create a download session
                val service = activity.sessionBinder?.getService()
                if (service != null) {
                    val sessionId = "GoFileDownload"
                    var session = activity.sessionBinder?.getSession(sessionId)

                    if (session == null) {
                        // Create a dummy client for the background session
                        val dummyView = com.termux.view.TerminalView(activity, null)
                        val client = TerminalBackEnd(dummyView, activity)
                        session = activity.sessionBinder?.createSession(sessionId, client, activity, WorkingMode.ALPINE)
                    }

                    // 4. Inject command
                    session?.write(cmd)

                    // 5. Notify user
                    activity.runOnUiThread {
                        android.widget.Toast.makeText(activity, "Download iniciado no terminal (GoFileDownload)", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
