package com.rk.terminal.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
