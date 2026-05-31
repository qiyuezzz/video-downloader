package com.example.videodownload.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.videodownload.ui.home.DownloadHistoryScreen
import com.example.videodownload.ui.home.HomeScreen
import com.example.videodownload.ui.home.HomeViewModel
import com.example.videodownload.ui.home.VideoPlayerScreen
import com.example.videodownload.ui.settings.SettingsScreen
import java.net.URLEncoder

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    data object Home : Screen("home", "首页", Icons.Default.Home)
    data object Downloads : Screen("downloads", "下载", Icons.Default.Download)
    data object Settings : Screen("settings", "设置")
    data object VideoPlayer : Screen("video_player/{uri}/{title}", "播放器") {
        fun createRoute(uri: String, title: String): String {
            val encodedUri = URLEncoder.encode(uri, "UTF-8")
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            return "video_player/$encodedUri/$encodedTitle"
        }
    }
}

@Composable
fun AppNavigation(navController: NavHostController) {
    val homeViewModel: HomeViewModel = viewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val bottomNavItems = listOf(Screen.Home, Screen.Downloads)

    Scaffold(
        bottomBar = {
            // 只在主页面和下载页面显示底部导航
            if (currentDestination?.route in bottomNavItems.map { it.route }) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { screen.icon?.let { Icon(it, contentDescription = null) } },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController, 
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    viewModel = homeViewModel
                )
            }
            composable(Screen.Downloads.route) {
                DownloadHistoryScreen(
                    viewModel = homeViewModel,
                    onPlayVideo = { uri, title ->
                        navController.navigate(Screen.VideoPlayer.createRoute(uri, title))
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                Screen.VideoPlayer.route,
                arguments = listOf(
                    navArgument("uri") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val uri = backStackEntry.arguments?.getString("uri") ?: ""
                val title = backStackEntry.arguments?.getString("title") ?: ""
                VideoPlayerScreen(
                    uriString = uri,
                    title = title,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
