package com.rk.terminal.ui.screens.settings

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.settings.Settings
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(navController: NavController) {
    val context = LocalContext.current
    val internalStorage = remember { Environment.getExternalStorageDirectory().absolutePath }

    // Mode: 0 = Normal, 1 = Storage Selection (Root)
    var mode by remember { mutableStateOf(0) }
    var currentPath by remember { mutableStateOf(File(internalStorage)) }

    val storageVolumes = remember {
        val list = mutableListOf<File>()
        list.add(File(internalStorage))
        val externalFilesDirs = context.getExternalFilesDirs(null)
        for (file in externalFilesDirs) {
            if (file != null) {
                val path = file.absolutePath
                if (path.contains("/storage/") && !path.contains("/emulated/0")) {
                    val rootPath = path.split("/Android/")[0]
                    val root = File(rootPath)
                    if (root.exists() && root.isDirectory) {
                        list.add(root)
                    }
                }
            }
        }
        list.distinctBy { it.absolutePath }
    }

    val files = remember(currentPath, mode) {
        if (mode == 1) emptyList()
        else currentPath.listFiles { file -> file.isDirectory }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    val canGoUp = remember(currentPath, mode) {
        if (mode == 1) false
        else {
            val isAtStorageRoot = storageVolumes.any { it.absolutePath == currentPath.absolutePath }
            !isAtStorageRoot
        }
    }

    fun goUp() {
        if (canGoUp) {
            currentPath = currentPath.parentFile!!
        } else {
            mode = 1 // Go to storage selection
        }
    }

    BackHandler {
        if (mode == 1) {
            navController.popBackStack()
        } else {
            goUp()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (mode == 1) "Selecionar Armazenamento" else "Selecionar Pasta", style = MaterialTheme.typography.titleMedium)
                        if (mode == 0) {
                            Text(currentPath.absolutePath, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (mode == 1) navController.popBackStack() else goUp()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp, modifier = Modifier.navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text("Cancelar")
                    }
                    if (mode == 0) {
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
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            if (mode == 1) {
                items(storageVolumes) { storage ->
                    val isInternal = storage.absolutePath == internalStorage
                    ListItem(
                        headlineContent = { Text(if (isInternal) "Armazenamento Interno" else "Cartão SD / Externo") },
                        supportingContent = { Text(storage.absolutePath) },
                        leadingContent = { Icon(if (isInternal) Icons.Default.Folder else Icons.Default.SdCard, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                        modifier = Modifier.clickable {
                            currentPath = storage
                            mode = 0
                        }
                    )
                }
            } else {
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
}
