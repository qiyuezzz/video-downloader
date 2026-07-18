package com.example.videodownload.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
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
    data object Downloads : Screen("downloads", "视频", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings, Icons.Outlined.Settings)
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

    val bottomNavItems = listOf(Screen.Home, Screen.Downloads, Screen.Settings)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentDestination?.route in bottomNavItems.map { it.route }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 12.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(66.dp)
                                .padding(7.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            bottomNavItems.forEach { screen ->
                                val selected = currentDestination?.hierarchy
                                    ?.any { it.route == screen.route } == true
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .clickable {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = if (selected) screen.selectedIcon!! else screen.unselectedIcon!!,
                                        contentDescription = screen.title,
                                        tint = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        screen.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
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
                SettingsScreen()
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
