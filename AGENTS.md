# Repository Guidelines

## 项目结构与模块组织

本仓库是单模块 Android 应用。业务代码位于 `app/src/main/java/com/example/videodownload/`：`parser/` 负责站点解析，`downloader/` 负责下载与 SAF 写入，`data/` 管理模型和 DataStore，`ui/` 存放 Compose 页面与主题，`navigation/` 定义路由。资源和清单位于 `app/src/main/res/` 与 `app/src/main/AndroidManifest.xml`。本地单元测试放在 `app/src/test/`，设备测试放在 `app/src/androidTest/`。依赖版本统一维护在 `gradle/libs.versions.toml`；不要直接修改 `release_v*/` 中的发布 APK。

## 构建、测试与开发命令

- `./gradlew assembleDebug`：编译可调试 APK。
- `./gradlew assembleRelease`：生成启用压缩和资源收缩的发布 APK。
- `./gradlew test`：运行全部 JVM 单元测试。
- `./gradlew testDebugUnitTest --tests "com.example.videodownload.util.UrlNormalizerTest"`：运行指定测试类。
- `./gradlew connectedAndroidTest`：在已连接设备或模拟器上运行 AndroidX 测试。
- `./gradlew lint`：执行 Android 静态检查；提交前处理新增告警。
- `./gradlew clean`：清除构建产物，用于排查缓存问题。

## 编码风格与命名约定

使用 Kotlin、Java 11 兼容级别和 4 空格缩进，遵循 Kotlin 官方风格。类、Composable 与密封类使用 `PascalCase`，函数和属性使用 `camelCase`，常量使用 `UPPER_SNAKE_CASE`。私有 `MutableStateFlow` 以 `_` 开头，并只公开只读 `StateFlow`。UI 使用 Jetpack Compose/Material3；异步工作放入 `viewModelScope`，阻塞 I/O 使用 `Dispatchers.IO`。Compose 依赖由 BOM 管理，不要单独指定版本。

## 测试指南

JVM 测试使用 JUnit 4，文件命名为 `<Subject>Test.kt`；设备/UI 测试使用 AndroidX JUnit、Espresso 和 Compose UI Test。解析器、URL 去重及下载状态变更应优先补充单元测试；依赖设备、存储授权或真实界面的行为放入 `androidTest`。仓库未设硬性覆盖率门槛，但修复缺陷时应加入回归测试。

## 提交与 Pull Request

提交记录采用中文 Conventional Commit 风格，例如 `feat: 支持新站点解析`、`fix: 修复重复下载检测`、`refactor: 简化状态管理`。每个提交聚焦一个逻辑变更，避免提交密钥、签名文件和本机 IDE 配置。PR 应说明动机、主要改动和验证命令，关联相关 issue；涉及 UI 时附前后截图，涉及下载或解析时列出已验证的平台与示例 URL 类型。

## 安全与配置

不要提交 API 密钥、Cookie、用户下载内容或真实账号数据。新增网络请求时复用统一的请求头与超时策略，并确认明文流量、文件权限及备份规则不会扩大数据暴露范围。

## 全局语言规则

本仓库的后续回复、说明文档、代码注释、Git 提交信息及 Pull Request 内容均使用中文。技术标识符、命令、API 名称和无法准确翻译的专有名词可保留英文。
