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
import java.security.MessageDigest

fun generateGid(url: String): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(url.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(16)
}

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
    var gameStats by remember { mutableStateOf<HydraGameStats?>(null) }
    var gameAssets by remember { mutableStateOf<HydraGameAssets?>(null) }
    var repacks by remember { mutableStateOf<List<HydraRepack>>(emptyList()) }
    var steamDetails by remember { mutableStateOf<Map<String, Any>?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(gameTitle, gameObjectId, gameShop) {
        if (gameObjectId != null && gameShop != null) {
            withContext(Dispatchers.IO) {
                val client = OkHttpClient()
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
                        client.newCall(Request.Builder().url("http://store.steampowered.com/api/appdetails?appids=$gameObjectId&l=brazilian").build()).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string()
                                val data = gson.fromJson(body, Map::class.java)
                                val gameData = (data[gameObjectId] as? Map<*, *>)?.get("data") as? Map<String, Any>
                                steamDetails = gameData
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (coverUrl != null) return@LaunchedEffect

        val apiKey = Settings.steamGridDbApiKey
        if (apiKey.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalhes do Jogo") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
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
            Box(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                AsyncImage(
                    model = gameAssets?.libraryHeroImageUrl ?: coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.6f
                )
                if (gameAssets?.logoImageUrl != null) {
                    AsyncImage(
                        model = gameAssets?.logoImageUrl,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).height(120.dp).padding(16.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = gameTitle,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // Info Section
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        val developers = (steamDetails?.get("developers") as? List<*>)?.joinToString(", ") ?: "Desconhecido"
                        val publishers = (steamDetails?.get("publishers") as? List<*>)?.joinToString(", ") ?: "Desconhecido"
                        val releaseDate = (steamDetails?.get("release_date") as? Map<*, *>)?.get("date") as? String ?: "Desconhecida"

                        Text("Desenvolvedor: $developers", style = MaterialTheme.typography.bodySmall)
                        Text("Editora: $publishers", style = MaterialTheme.typography.bodySmall)
                        Text("Lançamento: $releaseDate", style = MaterialTheme.typography.bodySmall)
                    }
                    if (gameStats != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${gameStats?.playerCount ?: 0}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Jogadores Ativos", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Description Section
                Text("Sobre o Jogo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                val description = steamDetails?.get("short_description") as? String ?: "Sem descrição disponível."
                Text(text = description, style = MaterialTheme.typography.bodyMedium)

                Spacer(modifier = Modifier.height(32.dp))

                // Download Options
                Text("Opções de Download", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                if (repacks.isNotEmpty()) {
                    repacks.forEach { repack ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = repack.title ?: "Sem título", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(text = "${repack.repacker} • ${repack.fileSize ?: "Desconhecido"}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                repack.uris?.forEach { uri ->
                                    Button(
                                        onClick = {
                                            val encodedUrl = URLEncoder.encode(uri, "UTF-8")
                                            navController.navigate("browser/$encodedUrl")
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Baixar Repack", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                } else if (gameUris.isNotEmpty()) {
                    // Fallback to simple uris if no repacks found
                    gameUris.forEach { uri ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = uri, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                                IconButton(onClick = {
                                    val encodedUrl = URLEncoder.encode(uri, "UTF-8")
                                    navController.navigate("browser/$encodedUrl")
                                }) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                }
                            }
                        }
                    }
                } else {
                    Text("Nenhum link de download disponível.", modifier = Modifier.padding(vertical = 16.dp))
                }
            }
        }
    }
}

fun triggerAria2Download(url: String, activity: MainActivity, title: String) {
    val downloadPath = Settings.downloadPath
    val downloadId = generateGid(url)
    if (activeDownloads.none { it.id == downloadId }) {
        activeDownloads.add(DownloadProgress(id = downloadId, title = title, progress = 0.1f, status = "Baixando via Aria2..."))
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
                "async-dns" to "false",
                "gid" to downloadId
            ))

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
                                val index = activeDownloads.indexOfFirst { it.id == downloadId }
                                if (index != -1) {
                                    activeDownloads[index] = activeDownloads[index].copy(gid = gid)
                                }
                                android.widget.Toast.makeText(activity, "Download adicionado ao Aria2", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } else {
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
        val maxConn = Settings.aria2MaxConnections

        // Run as standalone download in terminal to avoid port conflicts with daemon
        val aria2Cmd = "aria2c --dir=\"$downloadPath\" --max-connection-per-server=$maxConn --split=$maxConn " +
                "--user-agent=\"${Settings.aria2UserAgent}\" --async-dns=false --gid=$downloadId \"$url\""

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
