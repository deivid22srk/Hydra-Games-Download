package com.rk.terminal.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HydraSourcesScreen() {
    // Force recomposition when Settings.hydraSources changes
    var sources by remember { mutableStateOf(Settings.hydraSources) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newSourceUrl by remember { mutableStateOf("") }

    PreferenceLayout(label = "Fontes Hydra") {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Gerencie suas fontes de dados para busca de jogos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (sources.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
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
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sources) { url ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = url,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                                IconButton(onClick = {
                                    val newList = Settings.hydraSources.toMutableList()
                                    newList.remove(url)
                                    Settings.hydraSources = newList
                                    sources = newList
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
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
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
                            val newList = Settings.hydraSources.toMutableList()
                            if (!newList.contains(newSourceUrl)) {
                                newList.add(newSourceUrl)
                                Settings.hydraSources = newList
                                sources = newList
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
