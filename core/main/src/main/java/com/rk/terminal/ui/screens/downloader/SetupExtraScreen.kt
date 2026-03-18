package com.rk.terminal.ui.screens.downloader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import com.rk.terminal.ui.routes.MainActivityRoutes
import com.rk.settings.Settings as AppSettings
import com.rk.terminal.ui.screens.home.HydraSourceConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupExtraScreen(navController: NavHostController) {
    val context = LocalContext.current
    var downloadPath by remember { mutableStateOf(AppSettings.downloadPath) }
    var hydraUrl by remember { mutableStateOf("") }

    // Permission state
    var hasAllFilesAccess by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }
        )
    }

    // Refresh permission state when coming back
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasAllFilesAccess = Environment.isExternalStorageManager()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Configuração Inicial") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "Quase lá! Vamos configurar algumas coisas importantes.",
                style = MaterialTheme.typography.titleMedium
            )

            // 1. Storage Permission
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Permissão de Acesso a Arquivos", fontWeight = FontWeight.Bold)
                Text(
                    "O ReTerminal precisa de acesso total aos arquivos para gerenciar downloads e o sistema Alpine.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                            context.startActivity(intent)
                        }
                    },
                    enabled = !hasAllFilesAccess,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (hasAllFilesAccess) "Permissão Concedida" else "Conceder Permissão")
                }
            }

            // 2. Download Path
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Pasta de Download", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = AppSettings.downloadPath,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Caminho atual") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { navController.navigate(MainActivityRoutes.FolderPicker.route) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                        }
                    }
                )
            }

            // 3. Hydra API
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3. Fonte Hydra (Opcional)", fontWeight = FontWeight.Bold)
                Text(
                    "Adicione um link de API do Hydra Launcher para buscar jogos.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = hydraUrl,
                    onValueChange = { hydraUrl = it },
                    placeholder = { Text("https://exemplo.com/api.json") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Source, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (hydraUrl.isNotBlank()) {
                        val currentSources = AppSettings.hydraSources.toMutableList()
                        if (currentSources.none { it.url == hydraUrl }) {
                            currentSources.add(HydraSourceConfig(hydraUrl, true))
                            AppSettings.hydraSources = currentSources
                        }
                    }
                    AppSettings.isExtraSetupComplete = true
                    navController.navigate(MainActivityRoutes.MainScreen.route) {
                        popUpTo(MainActivityRoutes.MainScreen.route) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = hasAllFilesAccess
            ) {
                Text("Finalizar")
            }
        }
    }
}
