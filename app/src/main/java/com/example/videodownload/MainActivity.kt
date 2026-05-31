package com.example.videodownload

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.videodownload.navigation.AppNavigation
import com.example.videodownload.ui.theme.VideoDownloadTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoDownloadTheme {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }
}
