package com.rk.terminal.ui.routes

sealed class MainActivityRoutes(val route: String) {
    data object Settings : MainActivityRoutes("settings")
    data object Customization : MainActivityRoutes("customization")
    data object MainScreen : MainActivityRoutes("main")
    data object Home : MainActivityRoutes("home")
    data object HydraSources : MainActivityRoutes("hydra_sources")
}