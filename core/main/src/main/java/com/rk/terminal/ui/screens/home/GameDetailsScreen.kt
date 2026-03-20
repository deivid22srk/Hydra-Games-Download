package com.rk.terminal.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
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

data class LocalRepack(
    val title: String,
    val sourceName: String,
    val uris: List<String>,
    val fileSize: String? = null
)

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
    var localRepacks by remember { mutableStateOf<List<LocalRepack>>(emptyList()) }
    var steamDetails by remember { mutableStateOf<Map<String, Any>?>(null) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var isSearchingSources by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(gameTitle, gameObjectId, gameShop) {
        if (gameObjectId != null && gameShop != null) {
            isSearchingSources = true
            withContext(Dispatchers.IO) {
                val client = HydraApi.getClient()
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
                        val steamUrl = "https://store.steampowered.com/api/appdetails?appids=$gameObjectId&l=pt"
                        client.newCall(Request.Builder().url(steamUrl).build()).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string()
                                val data = gson.fromJson(body, Map::class.java)
                                val gameData = (data[gameObjectId] as? Map<*, *>)?.get("data") as? Map<String, Any>
                                steamDetails = gameData
                            }
                        }
                    }

                    // Local Sources Search
                    val localResults = mutableListOf<LocalRepack>()
                    Settings.hydraSources.filter { it.isEnabled }.forEach { config ->
                        try {
                            client.newCall(Request.Builder().url(config.url).build()).execute().use { response ->
                                if (response.isSuccessful) {
                                    val source = gson.fromJson(response.body?.string(), HydraSource::class.java)
                                    val sourceName = source?.name ?: config.url.split("/").getOrNull(2) ?: "Desconhecida"
                                    source?.downloads?.filter {
                                        it.title?.contains(gameTitle, ignoreCase = true) == true ||
                                        gameTitle.contains(it.title ?: "", ignoreCase = true)
                                    }?.forEach { game ->
                                        localResults.add(LocalRepack(
                                            title = game.title ?: "Sem nome",
                                            sourceName = sourceName,
                                            uris = game.uris ?: emptyList(),
                                            fileSize = game.fileSize
                                        ))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    localRepacks = localResults

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isSearchingSources = false
                }
            }
        }

        if (coverUrl != null) return@LaunchedEffect

        val apiKey = Settings.steamGridDbApiKey
        if (apiKey.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val client = HydraApi.getClient()
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
                title = { Text(gameTitle, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
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
            Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                AsyncImage(
                    model = steamDetails?.get("background") ?: gameAssets?.libraryHeroImageUrl ?: coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.5f
                )

                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomStart) {
                    if (gameAssets?.logoImageUrl != null) {
                        AsyncImage(
                            model = gameAssets?.logoImageUrl,
                            contentDescription = null,
                            modifier = Modifier.height(80.dp).widthIn(max = 250.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = gameTitle,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = { showDownloadDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    if (isSearchingSources) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("BAIXAR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                // Info Cards
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val developers = (steamDetails?.get("developers") as? List<*>)?.joinToString(", ") ?: "Desconhecido"
                    val releaseDate = (steamDetails?.get("release_date") as? Map<*, *>)?.get("date") as? String ?: "Desconhecida"

                    Card(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Desenvolvedor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(developers, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                    Card(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Lançamento", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(releaseDate, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                    if (gameStats != null) {
                        Card(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Jogadores", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text("${gameStats?.playerCount ?: 0}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Description
                Text("Sobre o Jogo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                val descriptionHtml = steamDetails?.get("detailed_description") as? String ?: "Sem descrição disponível."
                AndroidView(
                    factory = { context ->
                        TextView(context).apply {
                            setTextColor(0xFFFFFFFF.toInt()) // Workaround for dark theme
                            textSize = 14f
                        }
                    },
                    update = { view ->
                        view.text = HtmlCompat.fromHtml(descriptionHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Media Gallery
                val screenshots = steamDetails?.get("screenshots") as? List<Map<String, Any>>
                if (!screenshots.isNullOrEmpty()) {
                    Text("Galeria", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(screenshots) { screenshot ->
                            Card(modifier = Modifier.width(280.dp).height(160.dp)) {
                                AsyncImage(
                                    model = screenshot["path_thumbnail"],
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // Requirements
                val requirements = steamDetails?.get("pc_requirements") as? Map<String, Any>
                if (requirements != null) {
                    Text("Requisitos do Sistema", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    val minimum = requirements["minimum"] as? String
                    val recommended = requirements["recommended"] as? String

                    if (minimum != null) {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Monitor, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Mínimos", fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                AndroidView(
                                    factory = { context -> TextView(context).apply { textSize = 12f; setTextColor(0xFFCCCCCC.toInt()) } },
                                    update = { view -> view.text = HtmlCompat.fromHtml(minimum, HtmlCompat.FROM_HTML_MODE_LEGACY) }
                                )
                            }
                        }
                    }

                    if (recommended != null) {
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Monitor, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Recomendados", fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                AndroidView(
                                    factory = { context -> TextView(context).apply { textSize = 12f; setTextColor(0xFFCCCCCC.toInt()) } },
                                    update = { view -> view.text = HtmlCompat.fromHtml(recommended, HtmlCompat.FROM_HTML_MODE_LEGACY) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    if (showDownloadDialog) {
        ModalBottomSheet(
            onDismissRequest = { showDownloadDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Opções de Download",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (repacks.isEmpty() && localRepacks.isEmpty() && gameUris.isEmpty()) {
                    Text("Nenhuma fonte de download encontrada para este jogo.", modifier = Modifier.padding(vertical = 32.dp))
                }

                // API Repacks
                repacks.forEach { repack ->
                    DownloadOptionItem(
                        title = repack.title ?: "Sem título",
                        subtitle = "${repackerName(repack)} • ${repack.fileSize ?: "Desconhecido"}",
                        uris = repack.uris ?: emptyList(),
                        navController = navController
                    )
                }

                // Local Search Repacks
                localRepacks.forEach { repack ->
                    DownloadOptionItem(
                        title = repack.title,
                        subtitle = "Fonte: ${repack.sourceName}${if (repack.fileSize != null) " • ${repack.fileSize}" else ""}",
                        uris = repack.uris,
                        navController = navController
                    )
                }

                // Fallback Direct Links
                if (repacks.isEmpty() && localRepacks.isEmpty()) {
                    gameUris.forEach { uri ->
                        DownloadOptionItem(
                            title = "Link Direto",
                            subtitle = uri,
                            uris = listOf(uri),
                            navController = navController
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

fun repackerName(repack: HydraRepack): String {
    return repack.repacker ?: "Hydra API"
}

fun getHostFromUrl(url: String): String? {
    return try {
        val uri = java.net.URI(url)
        val host = uri.host?.lowercase() ?: ""
        when {
            host.contains("gofile.io") -> "GoFile.io"
            host.contains("mediafire.com") -> "MediaFire.com"
            host.contains("mega.nz") -> "Mega.nz"
            host.contains("1fichier.com") -> "1Fichier.com"
            host.contains("pixeldrain.com") -> "PixelDrain.com"
            host.contains("qiwi.gg") -> "Qiwi.gg"
            host.contains("buzzheavier.com") -> "BuzzHeavier.com"
            host.contains("krakenfiles.com") -> "KrakenFiles.com"
            host.contains("datanodes.to") -> "DataNodes.to"
            host.contains("rapidgator.net") -> "Rapidgator.net"
            host.contains("uptobox.com") -> "Uptobox.com"
            host.contains("ddownload.com") -> "DDownload.com"
            else -> host.replace("www.", "").replaceFirstChar { it.uppercase() }.ifBlank { null }
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun DownloadOptionItem(title: String, subtitle: String, uris: List<String>, navController: NavController) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            uris.forEach { uri ->
                Button(
                    onClick = {
                        val encodedUrl = URLEncoder.encode(uri, "UTF-8")
                        navController.navigate("browser/$encodedUrl")
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    val host = getHostFromUrl(uri)
                    val label = if (host != null) "Baixar via $host" else "Baixar Agora"
                    Text(label)
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
