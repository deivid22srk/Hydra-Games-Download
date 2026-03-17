package com.rk.terminal.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.settings.Settings
import com.rk.components.compose.preferences.base.PreferenceLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HydraSourcesScreen() {
    var sources by remember { mutableStateOf(Settings.hydraSources) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newSourceUrl by remember { mutableStateOf("") }

    PreferenceLayout(label = "Fontes Hydra") {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (sources.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Nenhuma fonte adicionada.")
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(sources) { url ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = url, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = {
                                    val newList = sources.toMutableList()
                                    newList.remove(url)
                                    sources = newList
                                    Settings.hydraSources = newList
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Adicionar Fonte")
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Adicionar Nova Fonte") },
                text = {
                    OutlinedTextField(
                        value = newSourceUrl,
                        onValueChange = { newSourceUrl = it },
                        label = { Text("URL da API (JSON)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newSourceUrl.isNotBlank()) {
                            val newList = sources.toMutableList()
                            if (!newList.contains(newSourceUrl)) {
                                newList.add(newSourceUrl)
                                sources = newList
                                Settings.hydraSources = newList
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
