# Repository Guidelines

## 项目结构与模块组织

本仓库是单模块 Android 应用（`applicationId = com.example.videodownload`，`minSdk = 29`、`targetSdk/compileSdk = 36`，Java 11，Release 仅打包 `arm64-v8a`）。业务代码位于 `app/src/main/java/com/example/videodownload/`：`parser/` 负责站点解析，`downloader/` 负责下载与 SAF 写入，`data/` 管理模型和 DataStore，`ui/` 存放 Compose 页面与主题（`home/`、`settings/`、`theme/`、`components/`），`navigation/` 定义路由，`util/` 提供 `NetworkClients`、`NetworkConstants`、`UrlNormalizer`、`VideoPlatform`、`AppLanguage` 等工具。资源和清单位于 `app/src/main/res/`（含 `values/`、`values-en/`、`values-night/`）与 `app/src/main/AndroidManifest.xml`。本地单元测试放在 `app/src/test/`，设备测试放在 `app/src/androidTest/`。依赖版本统一维护在 `gradle/libs.versions.toml`；不要直接修改 `release_v*/` 中的发布 APK。

## 架构与运行时要点

- 解析遵循「专用解析器优先，yt-dlp 兼容回退」策略，由 `ParseCoordinator` 编排：先按 `supports(url)` 顺序尝试 `BilibiliNativeParser`、`TwitterApiParser`、`InstagramAnonymousParser`，全部失败或返回空 `formats` 时回退到 `YtDlpParser`。新增站点应实现 `VideoParser` 接口并注册到 `ParseCoordinator`。
- 下载由 WorkManager 承载：`VideoDownloadWorker` 是 `CoroutineWorker`，以 `foregroundServiceType=dataSync` 前台服务运行，支持断点续传与应用重启后恢复。下载状态变更应补充 `app/src/test/` 单元测试。
- 存储使用 Android SAF / `DocumentFile`，由用户在系统目录选择器中授权保存目录，**不要**申请 `READ/WRITE_EXTERNAL_STORAGE` 类权限。`AndroidManifest.xml` 中 `usesCleartextTraffic=false`、`allowBackup=false`，新增网络请求前确认是否需要调整。
- 网络请求复用 `util.NetworkClients.standard` 或 `noRedirect`，**不要**在各解析器中重复 `new OkHttpClient`；统一请求头放在 `NetworkConstants`（移动端/桌面端 UA、B 站 Referer 等）。
- `app/build.gradle.kts` 中 `jniLibs.useLegacyPackaging=true` 是 yt-dlp Android 从 `nativeLibraryDir` 解压 Python/FFmpeg 资源所必需，不要关闭。
- Release 构建启用 `minify`+`shrinkResources`，`app/proguard-rules.pro` 会**移除所有 `android.util.Log` 调用**，因此不要依赖日志做发布版问题排查；如需保留诊断信息请走其他通道。新增依赖若含反射调用，需在 `proguard-rules.pro` 中补充 keep 规则。
- Release 签名通过 Gradle 属性配置（`RELEASE_STORE_FILE`/`RELEASE_STORE_PASSWORD`/`RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD`），缺一不创建 `release` signingConfig；签名文件与密码不入库。

## 构建命令（Windows 用 `.\gradlew.bat`，macOS/Linux 用 `./gradlew`）

- `./gradlew assembleDebug`：编译可调试 APK，输出 `app/build/outputs/apk/debug/app-debug.apk`。
- `./gradlew assembleRelease`：生成启用压缩和资源收缩的发布 APK。
- `./gradlew test`：运行全部 JVM 单元测试。
- `./gradlew testDebugUnitTest --tests "com.example.videodownload.util.UrlNormalizerTest"`：运行指定测试类（已有 `parser/`、`util/`、`downloader/` 三组测试）。
- `./gradlew connectedAndroidTest`：在已连接设备或模拟器上运行 AndroidX 测试。
- `./gradlew lint`：执行 Android 静态检查；`build.gradle.kts` 中 `lint.abortOnError=true`，提交前必须处理新增告警。
- `./gradlew clean`：清除构建产物，用于排查缓存问题。

## 编码风格与命名约定

使用 Kotlin、Java 11 兼容级别和 4 空格缩进，遵循 Kotlin 官方风格。类、Composable 与密封类使用 `PascalCase`，函数和属性使用 `camelCase`，常量使用 `UPPER_SNAKE_CASE`。私有 `MutableStateFlow` 以 `_` 开头，并只公开只读 `StateFlow`。UI 使用 Jetpack Compose/Material3；异步工作放入 `viewModelScope`，阻塞 I/O 使用 `Dispatchers.IO`。Compose 依赖由 BOM 管理，不要单独指定版本。

## 测试指南

JVM 测试使用 JUnit 4，文件命名为 `<Subject>Test.kt`；设备/UI 测试使用 AndroidX JUnit、Espresso 和 Compose UI Test。解析器、URL 去重及下载状态变更应优先补充单元测试；依赖设备、存储授权或真实界面的行为放入 `androidTest`。仓库未设硬性覆盖率门槛，但修复缺陷时应加入回归测试。注意：Release 版本会被 ProGuard 移除所有 `Log` 调用，测试中不要断言日志输出。

## 提交与 Pull Request

提交记录采用中文 Conventional Commit 风格，例如 `feat: 支持新站点解析`、`fix: 修复重复下载检测`、`refactor: 简化状态管理`。每个提交聚焦一个逻辑变更，避免提交密钥、签名文件和本机 IDE 配置。PR 应说明动机、主要改动和验证命令，关联相关 issue；涉及 UI 时附前后截图，涉及下载或解析时列出已验证的平台与示例 URL 类型。

## 安全与配置

不要提交 API 密钥、Cookie、用户下载内容或真实账号数据。新增网络请求时复用 `NetworkClients` 与 `NetworkConstants` 中的统一请求头与超时策略（连接 30s、读 60s），并确认 `usesCleartextTraffic`、`allowBackup` 及备份规则不会扩大数据暴露范围。应用不内置账号登录或 Cookie 管理，解析依赖公开接口或 yt-dlp，受登录/地区/年龄限制的内容可能失败。在 `README.md`（中）和 `README_EN.md`（英）需保持内容一致，改动一端时同步另一端。

## 全局语言规则

本仓库的后续回复、说明文档、代码注释、Git 提交信息及 Pull Request 内容均使用中文。技术标识符、命令、API 名称和无法准确翻译的专有名词可保留英文。
