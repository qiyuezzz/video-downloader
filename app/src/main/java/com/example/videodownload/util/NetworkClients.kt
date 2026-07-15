package com.example.videodownload.util

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** 复用连接池，避免各解析器重复创建 HTTP 客户端。 */
object NetworkClients {
    val standard: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val noRedirect: OkHttpClient by lazy {
        standard.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
