package com.example.videodownload.navigation

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.videodownload.ui.home.DownloadHistoryScreen
import com.example.videodownload.ui.home.DownloadTasksScreen
import com.example.videodownload.ui.home.HomeScreen
import com.example.videodownload.ui.home.HomeViewModel
import com.example.videodownload.ui.home.VideoPlayerScreen
import com.example.videodownload.ui.settings.AppUpdateViewModel
import com.example.videodownload.ui.settings.SettingsScreen
import com.example.videodownload.R

sealed class Screen(
    val route: String,
    @param:StringRes val titleRes: Int,
    val selectedIcon: ImageVector? = null,
    val unselectedIcon: ImageVector? = null
) {
    data object Home : Screen("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home)
    data object Downloads : Screen("downloads", R.string.nav_videos, Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary)
    data object Settings : Screen("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

private data class PlayerRequest(val uri: String, val title: String)

@Composable
fun AppNavigation() {
    val homeViewModel: HomeViewModel = viewModel()
    val appUpdateViewModel: AppUpdateViewModel = viewModel()
    val bottomNavItems = listOf(Screen.Home, Screen.Downloads, Screen.Settings)
    var selectedRoute by rememberSaveable { mutableStateOf(Screen.Home.route) }
    var playerRequest by remember { mutableStateOf<PlayerRequest?>(null) }
    var showDownloadTasks by rememberSaveable { mutableStateOf(false) }
    val stateHolder = rememberSaveableStateHolder()

    playerRequest?.let { request ->
        BackHandler { playerRequest = null }
        VideoPlayerScreen(
            uriString = request.uri,
            title = request.title,
            onNavigateBack = { playerRequest = null },
        )
        return
    }

    if (showDownloadTasks) {
        BackHandler { showDownloadTasks = false }
        DownloadTasksScreen(
            viewModel = homeViewModel,
            onNavigateBack = { showDownloadTasks = false },
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                            val selected = selectedRoute == screen.route
                            val title = stringResource(screen.titleRes)
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                    .clickable { selectedRoute = screen.route },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon!! else screen.unselectedIcon!!,
                                    contentDescription = title,
                                    tint = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    title,
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
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            stateHolder.SaveableStateProvider(selectedRoute) {
                when (selectedRoute) {
                    Screen.Downloads.route -> DownloadHistoryScreen(
                        viewModel = homeViewModel,
                        onPlayVideo = { uri, title -> playerRequest = PlayerRequest(uri, title) },
                    )
                    Screen.Settings.route -> SettingsScreen(appUpdateViewModel = appUpdateViewModel)
                    else -> HomeScreen(
                        viewModel = homeViewModel,
                        onOpenDownloads = { showDownloadTasks = true },
                    )
                }
            }
        }
    }
}
