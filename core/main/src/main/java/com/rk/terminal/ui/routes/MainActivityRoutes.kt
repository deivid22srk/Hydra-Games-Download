package com.rk.terminal.ui.routes

sealed class MainActivityRoutes(val route: String) {
    data object Settings : MainActivityRoutes("settings")
    data object Customization : MainActivityRoutes("customization")
    data object MainScreen : MainActivityRoutes("main")
    data object Home : MainActivityRoutes("home")
    data object HydraSources : MainActivityRoutes("hydra_sources")
    data object MainContainer : MainActivityRoutes("main_container")
    data object FolderPicker : MainActivityRoutes("folder_picker")
    data object GameDetails : MainActivityRoutes("game_details/{title}")
    data object Search : MainActivityRoutes("search")
    data object Profile : MainActivityRoutes("profile?userId={userId}")
    data object Library : MainActivityRoutes("library")
    data object Downloads : MainActivityRoutes("downloads")
    data object Aria2Settings : MainActivityRoutes("aria2_settings")
    data object Browser : MainActivityRoutes("browser/{url}")
    data object SetupExtra : MainActivityRoutes("setup_extra")
}