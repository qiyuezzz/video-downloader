package com.example.videodownload.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector? = null,
    val unselectedIcon: ImageVector? = null
) {
    data object Home : Screen("home", "首页", Icons.Filled.Home, Icons.Outlined.Home)
    data object Downloads : Screen("downloads", "下载", Icons.Filled.Download, Icons.Outlined.Download)
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
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentDestination?.route in bottomNavItems.map { it.route }) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon!! else screen.unselectedIcon!!,
                                    contentDescription = null
                                )
                            },
                            label = {
                                Text(
                                    screen.title,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            ),
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
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
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
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() },
                popExitTransition = { fadeOut() },
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
