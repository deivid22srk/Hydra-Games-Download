package com.rk.terminal.ui.screens.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.settings.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Aria2Settings(navController: NavController) {
    var rpcSecret by remember { mutableStateOf(Settings.aria2RpcSecret) }
    var rpcPort by remember { mutableStateOf(Settings.aria2RpcPort.toString()) }
    var maxConnections by remember { mutableStateOf(Settings.aria2MaxConnections.toString()) }
    var userAgent by remember { mutableStateOf(Settings.aria2UserAgent) }
    var maxTries by remember { mutableStateOf(Settings.aria2MaxTries.toString()) }
    var retryWait by remember { mutableStateOf(Settings.aria2RetryWait.toString()) }
    var timeout by remember { mutableStateOf(Settings.aria2Timeout.toString()) }
    var fileAllocation by remember { mutableStateOf(Settings.aria2FileAllocation) }
    var minSplitSize by remember { mutableStateOf(Settings.aria2MinSplitSize) }
    var maxDownloadLimit by remember { mutableStateOf(Settings.aria2MaxDownloadLimit) }
    var continueDownload by remember { mutableStateOf(Settings.aria2ContinueDownload) }
    var useDownloadScripts by remember { mutableStateOf(Settings.useDownloadScripts) }
    var useGofileScript by remember { mutableStateOf(Settings.useGofileScript) }
    var useBuzzheavierScript by remember { mutableStateOf(Settings.useBuzzheavierScript) }
    var usePixeldrainScript by remember { mutableStateOf(Settings.usePixeldrainScript) }
    var useMediafireScript by remember { mutableStateOf(Settings.useMediafireScript) }
    var useDatanodesScript by remember { mutableStateOf(Settings.useDatanodesScript) }
    var useFuckingfastScript by remember { mutableStateOf(Settings.useFuckingfastScript) }
    var useRootzScript by remember { mutableStateOf(Settings.useRootzScript) }
    var fallbackToBrowserOnError by remember { mutableStateOf(Settings.fallbackToBrowserOnError) }
    var useExternalBrowser by remember { mutableStateOf(Settings.useExternalBrowser) }
    var selectedBrowserPackage by remember { mutableStateOf(Settings.selectedExternalBrowserPackage) }
    var autoSaveInterval by remember { mutableStateOf(Settings.aria2AutoSaveInterval.toString()) }

    var showBrowserDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    PreferenceLayout(label = "Configurações Aria2") {
        PreferenceGroup(heading = "RPC") {
            OutlinedTextField(
                value = rpcSecret,
                onValueChange = {
                    rpcSecret = it
                    Settings.aria2RpcSecret = it
                },
                label = { Text("RPC Secret") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = rpcPort,
                onValueChange = {
                    rpcPort = it
                    it.toIntOrNull()?.let { port -> Settings.aria2RpcPort = port }
                },
                label = { Text("Porta RPC") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        PreferenceGroup(heading = "Downloads") {
            OutlinedTextField(
                value = maxConnections,
                onValueChange = {
                    maxConnections = it
                    it.toIntOrNull()?.let { conn -> Settings.aria2MaxConnections = conn }
                },
                label = { Text("Máximo de Conexões por Servidor") },
                supportingText = { Text("Padrão: 5") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = minSplitSize,
                onValueChange = {
                    minSplitSize = it
                    Settings.aria2MinSplitSize = it
                },
                label = { Text("Tamanho Mínimo de Split") },
                supportingText = { Text("Ex: 20M, 1G. Padrão: 20M") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = maxDownloadLimit,
                onValueChange = {
                    maxDownloadLimit = it
                    Settings.aria2MaxDownloadLimit = it
                },
                label = { Text("Limite Máximo de Download") },
                supportingText = { Text("Ex: 1M, 10K. 0 = ilimitado. Padrão: 0") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = userAgent,
                onValueChange = {
                    userAgent = it
                    Settings.aria2UserAgent = it
                },
                label = { Text("User Agent") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        PreferenceGroup(heading = "Tentativas e Timeouts") {
            OutlinedTextField(
                value = maxTries,
                onValueChange = {
                    maxTries = it
                    it.toIntOrNull()?.let { tries -> Settings.aria2MaxTries = tries }
                },
                label = { Text("Máximo de Tentativas") },
                supportingText = { Text("Padrão: 10") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = retryWait,
                onValueChange = {
                    retryWait = it
                    it.toIntOrNull()?.let { wait -> Settings.aria2RetryWait = wait }
                },
                label = { Text("Tempo de Espera entre Tentativas (segundos)") },
                supportingText = { Text("Padrão: 5") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = timeout,
                onValueChange = {
                    timeout = it
                    it.toIntOrNull()?.let { time -> Settings.aria2Timeout = time }
                },
                label = { Text("Timeout de Conexão (segundos)") },
                supportingText = { Text("Padrão: 60") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        PreferenceGroup(heading = "Avançado") {
            OutlinedTextField(
                value = fileAllocation,
                onValueChange = {
                    fileAllocation = it
                    Settings.aria2FileAllocation = it
                },
                label = { Text("Método de Alocação de Arquivo") },
                supportingText = { Text("Opções: none, prealloc, falloc. Padrão: none") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = autoSaveInterval,
                onValueChange = {
                    autoSaveInterval = it
                    it.toIntOrNull()?.let { interval -> Settings.aria2AutoSaveInterval = interval }
                },
                label = { Text("Intervalo de Auto-Save (segundos)") },
                supportingText = { Text("Padrão: 60") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Continuar Downloads Incompletos")
                Switch(
                    checked = continueDownload,
                    onCheckedChange = {
                        continueDownload = it
                        Settings.aria2ContinueDownload = it
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Usar Automação de Downloads")
                Switch(
                    checked = useDownloadScripts,
                    onCheckedChange = {
                        useDownloadScripts = it
                        Settings.useDownloadScripts = it
                    }
                )
            }
        }

        PreferenceGroup(heading = "Navegador e Fallback") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Fallback para Navegador em caso de erro")
                Switch(
                    checked = fallbackToBrowserOnError,
                    onCheckedChange = {
                        fallbackToBrowserOnError = it
                        Settings.fallbackToBrowserOnError = it
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Usar Navegador Externo")
                Switch(
                    checked = useExternalBrowser,
                    onCheckedChange = {
                        useExternalBrowser = it
                        Settings.useExternalBrowser = it
                    }
                )
            }

            if (useExternalBrowser) {
                val pm = context.packageManager
                val browserName = try {
                    if (selectedBrowserPackage.isBlank()) "Nenhum selecionado"
                    else pm.getApplicationLabel(pm.getApplicationInfo(selectedBrowserPackage, 0)).toString()
                } catch (e: Exception) { "Desconhecido" }

                ListItem(
                    headlineContent = { Text("Selecionar Navegador Externo") },
                    supportingContent = { Text(browserName) },
                    leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
                    modifier = Modifier.clickable { showBrowserDialog = true }
                )
            }
        }

        if (useDownloadScripts) {
            PreferenceGroup(heading = "Scripts Específicos") {
                ScriptToggle("GoFile", useGofileScript) {
                    useGofileScript = it
                    Settings.useGofileScript = it
                }
                ScriptToggle("BuzzHeavier", useBuzzheavierScript) {
                    useBuzzheavierScript = it
                    Settings.useBuzzheavierScript = it
                }
                ScriptToggle("PixelDrain", usePixeldrainScript) {
                    usePixeldrainScript = it
                    Settings.usePixeldrainScript = it
                }
                ScriptToggle("MediaFire", useMediafireScript) {
                    useMediafireScript = it
                    Settings.useMediafireScript = it
                }
                ScriptToggle("DataNodes", useDatanodesScript) {
                    useDatanodesScript = it
                    Settings.useDatanodesScript = it
                }
                ScriptToggle("FuckingFast", useFuckingfastScript) {
                    useFuckingfastScript = it
                    Settings.useFuckingfastScript = it
                }
                ScriptToggle("Rootz", useRootzScript) {
                    useRootzScript = it
                    Settings.useRootzScript = it
                }
            }
        }
    }

    if (showBrowserDialog) {
        val browsers = remember {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).map { it.activityInfo }
        }

        AlertDialog(
            onDismissRequest = { showBrowserDialog = false },
            title = { Text("Escolha o Navegador") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(browsers) { browser ->
                        val label = browser.loadLabel(context.packageManager).toString()
                        ListItem(
                            headlineContent = { Text(label) },
                            supportingContent = { Text(browser.packageName) },
                            modifier = Modifier.clickable {
                                selectedBrowserPackage = browser.packageName
                                Settings.selectedExternalBrowserPackage = browser.packageName
                                showBrowserDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBrowserDialog = false }) { Text("Fechar") }
            }
        )
    }
}

@Composable
private fun ScriptToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
