package com.rk.terminal.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.settings.Settings
import com.rk.components.compose.preferences.base.PreferenceLayout

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HydraSourcesScreen() {
    val sources = remember { mutableStateListOf<HydraSourceConfig>().apply { addAll(Settings.hydraSources) } }
    var showAddDialog by remember { mutableStateOf(false) }
    var newSourceUrl by remember { mutableStateOf("") }
    val sourceInfoMap = remember { mutableStateMapOf<String, HydraSource>() }

    LaunchedEffect(sources.size) {
        withContext(Dispatchers.IO) {
            val client = HydraApi.getClient()
            val gson = Gson()
            sources.forEach { config ->
                try {
                    val request = Request.Builder().url(config.url).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            val source = gson.fromJson(body, HydraSource::class.java)
                            if (source != null) {
                                withContext(Dispatchers.Main) {
                                    sourceInfoMap[config.url] = source
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
                                sources.add(HydraSourceConfig(newSourceUrl))
                                Settings.hydraSources = sources.toList()
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
