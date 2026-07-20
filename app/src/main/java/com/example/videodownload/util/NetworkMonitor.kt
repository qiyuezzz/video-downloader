package com.example.videodownload.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * 网络状态检测工具。
 */
object NetworkMonitor {

    /**
     * 判断当前是否通过 WiFi 联网。
     * 基于 [NetworkCapabilities.TRANSPORT_WIFI] 判定，对计费热点返回 false。
     * 调用方应在 [Dispatchers.IO] 协程内调用以避免主线程阻塞。
     */
    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
