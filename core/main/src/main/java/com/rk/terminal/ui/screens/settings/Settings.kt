package com.rk.terminal.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.components.SettingsToggle
import com.rk.terminal.ui.routes.MainActivityRoutes
import androidx.core.net.toUri
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.SettingsSystemDaydream
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    title: @Composable () -> Unit,
    description: @Composable () -> Unit = {},
    startWidget: (@Composable () -> Unit)? = null,
    endWidget: (@Composable () -> Unit)? = null,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    PreferenceTemplate(
        modifier = modifier
            .combinedClickable(
                enabled = isEnabled,
                indication = ripple(),
                interactionSource = interactionSource,
                onClick = onClick
            ),
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        title = title,
        description = description,
        startWidget = startWidget,
        endWidget = endWidget,
        applyPaddings = false
    )

}


object WorkingMode{
    const val ALPINE = 0
    const val ANDROID = 1
}

object InputMode {
    const val DEFAULT = 0
    const val TYPE_NULL = 1
    const val VISIBLE_PASSWORD = 2
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(modifier: Modifier = Modifier, navController: NavController, mainActivity: MainActivity) {
    val context = LocalContext.current
    var selectedOption by remember { mutableIntStateOf(Settings.working_Mode) }
    var selectedInputMode by remember { mutableIntStateOf(Settings.input_mode) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    PreferenceLayout(label = stringResource(strings.settings)) {
        // --- SEÇÃO HYDRA ---
        PreferenceGroup(heading = "Hydra Launcher") {
            SettingsCard(
                title = { Text("Fontes Hydra") },
                description = { Text("Gerenciar links de API e fontes de download") },
                startWidget = {
                    Icon(imageVector = Icons.Default.Source, contentDescription = null, modifier = Modifier.padding(start = 16.dp))
                },
                endWidget = {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, modifier = Modifier.padding(16.dp))
                },
                onClick = {
                    navController.navigate(MainActivityRoutes.HydraSources.route)
                }
            )

            SettingsCard(
                title = { Text("SteamGridDB API Key") },
                description = { Text(if (Settings.steamGridDbApiKey.isEmpty()) "Não configurado" else "Configurado") },
                startWidget = {
                    Icon(imageVector = Icons.Default.Key, contentDescription = null, modifier = Modifier.padding(start = 16.dp))
                },
                onClick = {
                    showApiKeyDialog = true
                }
            )
        }

        // --- SEÇÃO DOWNLOADS ---
        PreferenceGroup(heading = "Downloads") {
            SettingsCard(
                title = { Text("Configurações Aria2") },
                description = { Text("RPC, limites de velocidade e automação") },
                startWidget = {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.padding(start = 16.dp))
                },
                endWidget = {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, modifier = Modifier.padding(16.dp))
                },
                onClick = {
                    navController.navigate(MainActivityRoutes.Aria2Settings.route)
                }
            )

            SettingsCard(
                title = { Text("Pasta de Download") },
                description = { Text(Settings.downloadPath) },
                startWidget = {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.padding(start = 16.dp))
                },
                onClick = {
                    navController.navigate(MainActivityRoutes.FolderPicker.route)
                }
            )
        }

        // --- SEÇÃO TERMINAL ---
        PreferenceGroup(heading = "Terminal") {
            SettingsCard(
                title = { Text("Modo de Operação") },
                description = { Text(if (selectedOption == WorkingMode.ALPINE) "Alpine Linux" else "Android Shell") },
                startWidget = {
                    Icon(imageVector = Icons.Default.Terminal, contentDescription = null, modifier = Modifier.padding(start = 16.dp))
                },
                onClick = {
                    selectedOption = if (selectedOption == WorkingMode.ALPINE) WorkingMode.ANDROID else WorkingMode.ALPINE
                    Settings.working_Mode = selectedOption
                }
            )

            SettingsCard(
                title = { Text(stringResource(strings.input_mode)) },
                description = {
                    val modeText = when (selectedInputMode) {
                        InputMode.DEFAULT -> stringResource(strings.input_mode_default)
                        InputMode.TYPE_NULL -> stringResource(strings.input_mode_type_null)
                        InputMode.VISIBLE_PASSWORD -> stringResource(strings.input_mode_visible_password)
                        else -> ""
                    }
                    Text(modeText)
                },
                startWidget = {
                    Icon(imageVector = Icons.Default.Build, contentDescription = null, modifier = Modifier.padding(start = 16.dp))
                },
                onClick = {
                    selectedInputMode = (selectedInputMode + 1) % 3
                    Settings.input_mode = selectedInputMode
                }
            )

            SettingsToggle(
                label = stringResource(strings.seccomp),
                description = stringResource(strings.seccomp_desc),
                showSwitch = true,
                default = Settings.seccomp,
                sideEffect = {
                    Settings.seccomp = it
                }
            )
        }

        // --- SEÇÃO INTERFACE ---
        PreferenceGroup(heading = "Interface") {
            SettingsCard(
                title = { Text(stringResource(strings.customizations)) },
                description = { Text("Temas, cores e transparência") },
                startWidget = {
                    Icon(imageVector = Icons.Default.ColorLens, contentDescription = null, modifier = Modifier.padding(start = 16.dp))
                },
                endWidget = {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, modifier = Modifier.padding(16.dp))
                },
                onClick = {
                    navController.navigate(MainActivityRoutes.Customization.route)
                }
            )
        }

        // --- SEÇÃO SISTEMA ---
        PreferenceGroup(heading = "Sistema") {
            SettingsCard(
                title = { Text(stringResource(strings.all_file_access)) },
                description = { Text("Gerenciar permissões de arquivo") },
                startWidget = {
                    Icon(imageVector = Icons.Default.Storage, contentDescription = null, modifier = Modifier.padding(start = 16.dp))
                },
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        runCatching {
                            val intent = Intent(
                                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                "package:${context.packageName}".toUri()
                            )
                            context.startActivity(intent)
                        }.onFailure {
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                    } else {
                        val intent = Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${context.packageName}".toUri()
                        )
                        context.startActivity(intent)
                    }
                }
            )
        }

        if (showApiKeyDialog) {
            var apiKey by remember { mutableStateOf(Settings.steamGridDbApiKey) }
            AlertDialog(
                onDismissRequest = { showApiKeyDialog = false },
                title = { Text("SteamGridDB API Key") },
                text = {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        Settings.steamGridDbApiKey = apiKey
                        showApiKeyDialog = false
                    }) {
                        Text("Salvar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showApiKeyDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}