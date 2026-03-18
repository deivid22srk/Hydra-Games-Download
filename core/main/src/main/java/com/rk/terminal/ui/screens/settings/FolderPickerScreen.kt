package com.rk.terminal.ui.screens.settings

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.settings.Settings
import com.rk.components.compose.preferences.base.PreferenceLayout
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(navController: NavController) {
    var currentPath by remember { mutableStateOf(File(Environment.getExternalStorageDirectory().absolutePath)) }
    val files = remember(currentPath) {
        currentPath.listFiles { file -> file.isDirectory }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    BackHandler {
        if (currentPath.parentFile != null && currentPath.absolutePath != "/") {
            currentPath = currentPath.parentFile!!
        } else {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Selecionar Pasta", style = MaterialTheme.typography.titleMedium)
                        Text(currentPath.absolutePath, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentPath.parentFile != null && currentPath.absolutePath != "/") {
                            currentPath = currentPath.parentFile!!
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        Settings.downloadPath = currentPath.absolutePath
                        navController.popBackStack()
                    }) {
                        Text("Selecionar")
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            items(files) { file ->
                ListItem(
                    headlineContent = { Text(file.name) },
                    leadingContent = { Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { currentPath = file }
                )
            }
            if (files.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Esta pasta está vazia", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
