# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 语言要求

所有回复、文档、代码注释、Git 提交信息等均使用中文。

## Build & Test Commands

```bash
# 构建 debug APK
./gradlew assembleDebug

# 构建 release APK
./gradlew assembleRelease

# 运行全部单元测试
./gradlew test

# 运行单个单元测试类
./gradlew testDebugUnitTest --tests "com.example.videodownload.ExampleUnitTest"

# 运行设备/模拟器测试
./gradlew connectedAndroidTest

# 清理构建
./gradlew clean
```

## 项目架构

单模块 Android 应用（`app/`），用于嗅探和下载 X/Twitter 视频。

**技术栈**: Kotlin 2.2 + Jetpack Compose + Material3，compileSdk 36，minSdk 29，Java 11 兼容。

**构建工具**: Gradle 9.4.1（Kotlin DSL），依赖版本统一管理于 `gradle/libs.versions.toml`，在 build script 中以 `libs.xxx` 引用。Compose BOM `2026.02.01` 控制所有 Compose 库版本，不要单独指定 Compose 库版本号。

### 数据流与核心组件

```
用户粘贴链接 → HomeScreen 剪贴板读取
    → VideoSniffer（隐藏 WebView）拦截页面中的视频流 URL
    → HomeViewModel 管理解析状态（ParseState sealed class）
    → VideoDownloader（OkHttp）通过 SAF DocumentFile 写入用户选定目录
    → DownloadState 反馈下载进度/结果
```

**关键数据模型** (`data/model/VideoInfo.kt`):
- `VideoInfo` — 视频标题、缩略图、格式列表、来源 URL
- `VideoFormat` — 单个格式的 ID、质量、扩展名、文件大小、下载地址、高度
- `DownloadState` — sealed class: `Idle` / `Progress(percent)` / `Success(fileName)` / `Error(message)`

**导航路由** (`navigation/AppNavigation.kt`):
- `Routes.HOME` = "home"（起始页）
- `Routes.SETTINGS` = "settings"

### 包结构

| 包 | 职责 |
|---|---|
| `data.model` | 数据类和密封类 |
| `data` | DataStore Preferences 设置持久化 |
| `downloader` | OkHttp 下载引擎 + SAF 文件写入 |
| `navigation` | NavHost 路由定义 |
| `ui.home` | 主页面 Composable + ViewModel + VideoSniffer |
| `ui.settings` | 设置页面 Composable + ViewModel |
| `ui.theme` | Material3 主题（Color / Theme / Type） |

## 代码规范

- **UI**: 100% Jetpack Compose，无 XML 布局。使用 `Scaffold`、`TopAppBar`、`Card` 等 Material3 组件
- **状态管理**: ViewModel 中使用 `MutableStateFlow`（私以下划线前缀 `_parseState`），公开暴露为 `StateFlow`；Composable 中通过 `collectAsState()` 收集
- **ViewModel**: 继承 `AndroidViewModel`（需要 Application context 访问 DataStore、OkHttp、剪贴板）。无依赖注入框架，手动实例化
- **协程**: `viewModelScope.launch` 处理异步；IO 操作通过 `withContext(Dispatchers.IO)`
- **命名**: Composable 函数 PascalCase；私有辅助 Composable 用描述性名称（如 `PasteSection`、`VideoList`、`QualitySelector`）；常量放 companion object
- **WebView 模式**: `VideoSniffer` 通过 `AndroidView` 嵌入 WebView（`Modifier.size(1.dp)` 近乎不可见），在 `shouldInterceptRequest` 中拦截视频 URL，页面加载后通过 JS 注入自动播放 `<video>` 元素
