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
                                    triggerGoFileDownload(uri, mainActivity, gameTitle)
                                } else if (uri.contains("buzzheavier.com") || uri.contains("bzzhr.co")) {
                                    triggerBuzzHeavierDownload(uri, mainActivity, gameTitle)
                                } else {
                                    val encodedUrl = URLEncoder.encode(uri, "UTF-8")
                                    navController.navigate("browser/$encodedUrl")
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

fun triggerAria2Download(url: String, activity: MainActivity, title: String) {
    val downloadPath = Settings.downloadPath
    val downloadId = url.hashCode().toString()
    if (activeDownloads.none { it.id == downloadId }) {
        activeDownloads.add(DownloadProgress(downloadId, title, 0.1f, "Baixando via Aria2..."))
    }

    activity.lifecycleScope.launch(Dispatchers.Main) {
        try {
            val rpcUrl = "http://localhost:${Settings.aria2RpcPort}/jsonrpc"
            val rpcSecret = Settings.aria2RpcSecret
            val maxConn = Settings.aria2MaxConnections

            val client = OkHttpClient()
            val gson = Gson()

            val params = mutableListOf<Any>()
            if (rpcSecret.isNotBlank()) {
                params.add("token:$rpcSecret")
            }
            params.add(listOf(url))
            params.add(mapOf(
                "dir" to downloadPath,
                "max-connection-per-server" to maxConn.toString(),
                "split" to maxConn.toString(),
                "user-agent" to Settings.aria2UserAgent,
                "async-dns" to "false"
            ))

            val rpcRequestMap = mapOf(
                "jsonrpc" to "2.0",
                "id" to downloadId,
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
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(activity, "Download adicionado ao Aria2", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } else {
                            // If RPC fails, try starting aria2c in terminal
                            startAria2InTerminal(url, activity, title, downloadId, downloadPath)
                        }
                    }
                } catch (e: Exception) {
                    startAria2InTerminal(url, activity, title, downloadId, downloadPath)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private fun startAria2InTerminal(url: String, activity: MainActivity, title: String, downloadId: String, downloadPath: String) {
    activity.lifecycleScope.launch(Dispatchers.Main) {
        val rpcSecret = Settings.aria2RpcSecret
        val rpcPort = Settings.aria2RpcPort
        val maxConn = Settings.aria2MaxConnections

        val aria2Cmd = "aria2c --enable-rpc --rpc-listen-all=false --rpc-listen-port=$rpcPort " +
                (if (rpcSecret.isNotBlank()) "--rpc-secret=\"$rpcSecret\" " else "") +
                "--dir=\"$downloadPath\" --max-connection-per-server=$maxConn --split=$maxConn " +
                "--user-agent=\"${Settings.aria2UserAgent}\" --async-dns=false \"$url\""

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

fun triggerGoFileDownload(url: String, activity: MainActivity, title: String) {
    val downloadPath = Settings.downloadPath

    // Add to Downloads UI
    val downloadId = url.hashCode().toString()
    if (activeDownloads.none { it.id == downloadId }) {
        activeDownloads.add(DownloadProgress(downloadId, title, 0.1f, "Baixando do GoFile..."))
    }

    activity.lifecycleScope.launch(Dispatchers.Main) {
        try {
            val initialArgs = listOf("sh", "-c", "cd ~/GoFileDownloader && python3 downloader.py \"$url\" --custom-path \"$downloadPath\"")

            val service = activity.sessionBinder?.getService()
            if (service != null) {
                val sessionId = "GoFileDownload"
                var session = activity.sessionBinder?.getSession(sessionId)

                if (session == null) {
                    val dummyView = com.termux.view.TerminalView(activity, null)
                    val client = TerminalBackEnd(dummyView, activity).apply {
                        this.sessionId = sessionId
                    }
                    session = activity.sessionBinder?.createSession(sessionId, client, activity, WorkingMode.ALPINE, initialArgs = initialArgs)
                } else {
                    val cmd = "cd ~/GoFileDownloader && python3 downloader.py \"$url\" --custom-path \"$downloadPath\"\n"
                    session.write(cmd)
                }

                android.widget.Toast.makeText(activity, "Download iniciado no terminal (GoFileDownload)", android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun triggerBuzzHeavierDownload(url: String, activity: MainActivity, title: String) {
    val downloadPath = Settings.downloadPath

    val downloadId = url.hashCode().toString()
    if (activeDownloads.none { it.id == downloadId }) {
        activeDownloads.add(DownloadProgress(downloadId, title, 0.1f, "Baixando do BuzzHeavier..."))
    }

    activity.lifecycleScope.launch(Dispatchers.Main) {
        try {
            val initialArgs = listOf("sh", "-c", "mkdir -p \"$downloadPath\" && cd \"$downloadPath\" && python3 ~/buzzheavier-downloader/bhdownload.py \"$url\"")

            val service = activity.sessionBinder?.getService()
            if (service != null) {
                val sessionId = "BuzzHeavierDownload"
                var session = activity.sessionBinder?.getSession(sessionId)

                if (session == null) {
                    val dummyView = com.termux.view.TerminalView(activity, null)
                    val client = TerminalBackEnd(dummyView, activity).apply {
                        this.sessionId = sessionId
                    }
                    session = activity.sessionBinder?.createSession(sessionId, client, activity, WorkingMode.ALPINE, initialArgs = initialArgs)
                } else {
                    val cmd = "mkdir -p \"$downloadPath\" && cd \"$downloadPath\" && python3 ~/buzzheavier-downloader/bhdownload.py \"$url\"\n"
                    session.write(cmd)
                }

                android.widget.Toast.makeText(activity, "Download iniciado no terminal (BuzzHeavierDownload)", android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
