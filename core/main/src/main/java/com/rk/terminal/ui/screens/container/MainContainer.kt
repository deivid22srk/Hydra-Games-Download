package com.rk.terminal.ui.screens.container

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.screens.home.DownloadsScreen
import com.rk.terminal.ui.screens.home.HomeScreen
import com.rk.terminal.ui.screens.settings.Settings
import com.rk.terminal.ui.screens.terminal.TerminalScreen

import com.rk.terminal.ui.screens.home.SharedGameViewModel

@Composable
fun MainContainer(
    mainActivity: MainActivity,
    navController: NavController,
    sharedGameViewModel: SharedGameViewModel
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Início") },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Terminal") },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("Downloads") },
                    icon = { Icon(Icons.Default.Download, contentDescription = null) }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    label = { Text("Configurações") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(WindowInsets.navigationBars)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(navController = navController, viewModel = sharedGameViewModel)
                1 -> TerminalScreen(mainActivityActivity = mainActivity, navController = navController)
                2 -> DownloadsScreen()
                3 -> Settings(navController = navController, mainActivity = mainActivity)
            }
        }
    }
}
