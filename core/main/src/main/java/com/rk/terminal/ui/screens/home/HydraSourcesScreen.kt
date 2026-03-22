package com.rk.terminal.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.settings.Settings
import com.rk.components.compose.preferences.base.PreferenceLayout

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HydraSourcesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sources = remember { mutableStateListOf<HydraSourceConfig>().apply { addAll(Settings.hydraSources) } }
    var showAddDialog by remember { mutableStateOf(false) }
    var newSourceUrl by remember { mutableStateOf("") }
    val sourceInfoMap = remember { mutableStateMapOf<String, HydraSource>() }
    val isDownloading = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(sources.size) {
        sources.forEach { config ->
            val cached = HydraSourceCache.getSource(context, config.url)
            if (cached != null) {
                sourceInfoMap[config.url] = cached
            }
        }
    }

    val downloadSource = { config: HydraSourceConfig ->
        scope.launch(Dispatchers.IO) {
            isDownloading[config.url] = true
            try {
                val client = HydraApi.getClient()
                val gson = Gson()
                val request = Request.Builder().url(config.url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        val source = gson.fromJson(body, HydraSource::class.java)
                        if (source != null) {
                            HydraSourceCache.saveSource(context, config.url, source)
                            withContext(Dispatchers.Main) {
                                sourceInfoMap[config.url] = source
                                val index = sources.indexOfFirst { it.url == config.url }
                                if (index != -1) {
                                    sources[index] = config.copy(lastDownloaded = System.currentTimeMillis())
                                    Settings.hydraSources = sources.toList()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Erro ao baixar: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally {
                isDownloading[config.url] = false
            }
        }
    }

    PreferenceLayout(label = "Fontes Hydra") {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Gerencie suas fontes de dados para busca de jogos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (sources.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Nenhuma fonte adicionada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sources.forEach { config ->
                        val info = sourceInfoMap[config.url]
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = info?.name ?: config.url,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    if (info != null) {
                                        Text(
                                            text = "${info.downloads?.size ?: 0} jogos disponíveis",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (info?.name != null) {
                                        Text(
                                            text = config.url,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    if (config.lastDownloaded != null) {
                                        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(config.lastDownloaded))
                                        Text(
                                            text = "Último download: $date",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }

                                if (isDownloading[config.url] == true) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    IconButton(onClick = { downloadSource(config) }) {
                                        Icon(
                                            imageVector = if (config.lastDownloaded == null) Icons.Default.Download else Icons.Default.Refresh,
                                            contentDescription = "Baixar/Rebaixar"
                                        )
                                    }
                                }

                                Switch(
                                    checked = config.isEnabled,
                                    onCheckedChange = { isEnabled ->
                                        val index = sources.indexOfFirst { it.url == config.url }
                                        if (index != -1) {
                                            sources[index] = config.copy(isEnabled = isEnabled)
                                            Settings.hydraSources = sources.toList()
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                IconButton(onClick = {
                                    sources.removeIf { it.url == config.url }
                                    Settings.hydraSources = sources.toList()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remover",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ADICIONAR NOVA FONTE")
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Adicionar Fonte Hydra") },
                text = {
                    Column {
                        Text(
                            "Insira a URL do arquivo JSON da fonte Hydra.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = newSourceUrl,
                            onValueChange = { newSourceUrl = it },
                            placeholder = { Text("https://example.com/source.json") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (newSourceUrl.isNotBlank()) {
                            if (sources.none { it.url == newSourceUrl }) {
                                val config = HydraSourceConfig(newSourceUrl)
                                sources.add(config)
                                Settings.hydraSources = sources.toList()
                                downloadSource(config)
                            }
                            newSourceUrl = ""
                            showAddDialog = false
                        }
                    }) {
                        Text("Adicionar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
