package com.rk.terminal.ui.navHosts


import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.animations.NavigationAnimationTransitions
import com.rk.terminal.ui.routes.MainActivityRoutes
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rk.terminal.ui.screens.container.MainContainer
import com.rk.terminal.ui.screens.customization.Customization
import com.rk.terminal.ui.screens.downloader.Downloader
import com.rk.terminal.ui.screens.home.GameDetailsScreen
import com.rk.terminal.ui.screens.home.HomeScreen
import com.rk.terminal.ui.screens.home.LibraryScreen
import com.rk.terminal.ui.screens.home.SearchScreen
import com.rk.terminal.ui.screens.home.ProfileScreen
import com.rk.terminal.ui.screens.home.HydraSourcesScreen
import com.rk.terminal.ui.screens.home.BrowserScreen
import com.rk.terminal.ui.screens.downloader.SetupExtraScreen
import com.rk.terminal.ui.screens.home.SharedGameViewModel
import com.rk.terminal.ui.screens.settings.Aria2Settings
import com.rk.terminal.ui.screens.settings.FolderPickerScreen
import com.rk.terminal.ui.screens.settings.Settings
import com.rk.terminal.ui.screens.terminal.Rootfs
import com.rk.terminal.ui.screens.terminal.TerminalScreen

var showStatusBar = mutableStateOf(Settings.statusBar)
var horizontal_statusBar = mutableStateOf(Settings.horizontal_statusBar)

fun showStatusBar(show: Boolean,window: Window){
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q){
        if (show){
            window.decorView.windowInsetsController!!.show(
                android.view.WindowInsets.Type.statusBars()
            )
        }else{
            window.decorView.windowInsetsController!!.hide(
                android.view.WindowInsets.Type.statusBars()
            )
        }
    }else{
        if (show){
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.statusBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }else{
            WindowInsetsControllerCompat(window,window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.statusBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
}


@Composable
fun UpdateStatusBar(mainActivityActivity: MainActivity,show: Boolean = true){
    LaunchedEffect(show) {
        showStatusBar(show = show, window = mainActivityActivity.window)
    }
}

@Composable
fun MainActivityNavHost(modifier: Modifier = Modifier,navController: NavHostController,mainActivity: MainActivity) {
    val sharedGameViewModel: SharedGameViewModel = viewModel()
    NavHost(
        navController = navController,
        startDestination = MainActivityRoutes.MainScreen.route,
        enterTransition = { NavigationAnimationTransitions.enterTransition },
        exitTransition = { NavigationAnimationTransitions.exitTransition },
        popEnterTransition = { NavigationAnimationTransitions.popEnterTransition },
        popExitTransition = { NavigationAnimationTransitions.popExitTransition },
    ) {

        composable(MainActivityRoutes.MainScreen.route) {
            if (Rootfs.isFullyInstalled.value){
                val config = LocalConfiguration.current
                if (Configuration.ORIENTATION_LANDSCAPE == config.orientation){
                    UpdateStatusBar(mainActivity, show = horizontal_statusBar.value)
                }else{
                    UpdateStatusBar(mainActivity, show = showStatusBar.value)
                }

                MainContainer(mainActivity = mainActivity, navController = navController, sharedGameViewModel = sharedGameViewModel)
            }else{
                Downloader(mainActivity = mainActivity, navController = navController)
            }
        }
        composable(MainActivityRoutes.Settings.route) {
            UpdateStatusBar(mainActivity,show = true)
            Settings(navController = navController, mainActivity = mainActivity)
        }
        composable(MainActivityRoutes.Customization.route){
            UpdateStatusBar(mainActivity,show = true)
            Customization()
        }
        composable(MainActivityRoutes.Home.route) {
            UpdateStatusBar(mainActivity, show = true)
            HomeScreen(navController = navController, viewModel = sharedGameViewModel)
        }
        composable(MainActivityRoutes.HydraSources.route) {
            UpdateStatusBar(mainActivity, show = true)
            HydraSourcesScreen()
        }
        composable(MainActivityRoutes.FolderPicker.route) {
            UpdateStatusBar(mainActivity, show = true)
            FolderPickerScreen(navController = navController)
        }
        composable(MainActivityRoutes.Aria2Settings.route) {
            UpdateStatusBar(mainActivity, show = true)
            Aria2Settings(navController = navController)
        }
        composable(MainActivityRoutes.Browser.route) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            UpdateStatusBar(mainActivity, show = true)
            BrowserScreen(url = url, mainActivity = mainActivity, navController = navController)
        }
        composable(MainActivityRoutes.SetupExtra.route) {
            UpdateStatusBar(mainActivity, show = true)
            SetupExtraScreen(navController = navController)
        }
        composable(MainActivityRoutes.GameDetails.route) {
            UpdateStatusBar(mainActivity, show = true)
            GameDetailsScreen(
                viewModel = sharedGameViewModel,
                navController = navController,
                mainActivity = mainActivity
            )
        }
        composable(MainActivityRoutes.Search.route) {
            UpdateStatusBar(mainActivity, show = true)
            SearchScreen(navController = navController, viewModel = sharedGameViewModel)
        }
        composable(MainActivityRoutes.Library.route) {
            UpdateStatusBar(mainActivity, show = true)
            LibraryScreen(navController = navController, viewModel = sharedGameViewModel)
        }
        composable(MainActivityRoutes.Profile.route) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")
            UpdateStatusBar(mainActivity, show = true)
            ProfileScreen(navController = navController, userIdArg = userId)
        }
    }
}