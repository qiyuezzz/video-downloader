# 视频下载器

一款使用 Kotlin 与 Jetpack Compose 开发的 Android 视频解析、下载和本地管理应用。应用支持从公开的视频页面链接提取可下载资源，选择画质后保存到用户指定目录，并在应用内管理下载任务和本地视频。

## 主要功能

- 解析 Bilibili、X / Twitter、Instagram、YouTube 等平台的公开链接。
- 解析完成后查看封面、标题、格式、清晰度和预计文件大小。
- 使用系统文件访问框架（SAF）选择保存目录，无需申请传统存储权限。
- 支持后台下载、进度展示、单任务暂停与继续、全部暂停与全部继续。
- 下载任务支持断点续传、长按多选删除和应用重启后恢复。
- 自动按平台建立分类目录，并扫描保存目录中的已有视频。
- 本地视频支持列表、双列和三列布局，以及应用内播放。
- 支持浅色模式、深色模式和跟随系统。
- 支持中文和英文，首次启动时选择语言，也可在设置中随时切换。
- 支持在设置页更新 yt-dlp 解析引擎。

## 平台解析方式

应用采用“专用解析器优先，yt-dlp 兼容回退”的策略：

| 平台 | 首选解析方式 | 失败后的处理 |
| --- | --- | --- |
| Bilibili | 原生接口解析器 | 回退到 yt-dlp |
| X / Twitter | 专用接口解析器 | 回退到 yt-dlp |
| Instagram | 匿名公开内容解析器 | 回退到 yt-dlp |
| YouTube 及其他兼容站点 | yt-dlp | 返回详细错误信息 |

受登录状态、地区、年龄限制、私密内容、平台接口变化或链接失效影响，部分内容可能无法解析。应用不内置账号登录或 Cookie 管理功能。

## 使用方法

1. 打开“设置”，选择视频保存目录。
2. 在首页粘贴公开视频页面链接并开始解析。
3. 选择需要的清晰度或格式，确认下载。
4. 点击首页右上角的下载图标查看任务进度。
5. 在“视频”页面查看、播放和管理本地视频。

下载列表中的任务可直接点击暂停或继续；顶部按钮可控制全部任务。长按任务可以进入多选删除模式。删除任务记录不会自动删除已写入的文件片段。

## 运行要求

- Android 10（API 29）及以上版本。
- 当前构建仅包含 `arm64-v8a` 架构。
- 下载和解析需要网络连接。
- 部分后台下载场景需要通知权限；拒绝通知权限不会阻止普通下载，但系统可能隐藏下载通知。

## 技术栈

- Kotlin、Java 11
- Jetpack Compose、Material 3
- ViewModel、StateFlow、DataStore
- WorkManager
- Android DocumentFile / SAF
- Media3 ExoPlayer
- OkHttp
- yt-dlp Android、FFmpeg
- Coil、Coil Video

## 项目结构

```text
app/src/main/java/com/example/videodownload/
├─ data/          数据模型、设置和下载记录持久化
├─ downloader/    后台下载、进度更新和 SAF 文件写入
├─ navigation/    页面导航与底部导航栏
├─ parser/        平台专用解析器和 yt-dlp 回退解析
├─ ui/
│  ├─ home/       首页、下载任务、本地视频和播放器
│  ├─ settings/   设置页面
│  └─ theme/      Material 3 主题与配色
└─ util/          URL 规范化和平台识别等工具
```

单元测试位于 `app/src/test/`，设备测试位于 `app/src/androidTest/`。依赖版本统一维护在 `gradle/libs.versions.toml`。

## 本地构建

建议使用支持当前 Android Gradle Plugin 的 Android Studio，并安装 Android SDK 36。项目最低使用 Java 11 编译。

Windows：

```powershell
.\gradlew.bat assembleDebug
```

macOS 或 Linux：

```bash
./gradlew assembleDebug
```

生成的调试 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 测试与检查

```powershell
# JVM 单元测试
.\gradlew.bat test

# Android Lint
.\gradlew.bat lint

# 构建 Release APK
.\gradlew.bat assembleRelease

# 连接设备或模拟器后运行设备测试
.\gradlew.bat connectedAndroidTest
```

Release 签名可通过以下 Gradle 属性配置：

```properties
RELEASE_STORE_FILE=/path/to/keystore
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=your_key_alias
RELEASE_KEY_PASSWORD=your_key_password
```

请勿将签名文件、密码、Cookie、用户下载内容或真实账号数据提交到仓库。

## 隐私与使用说明

- 应用仅在设备本地保存设置、下载任务和视频记录。
- 保存目录由用户通过 Android 系统目录选择器授权。
- 视频解析和下载会直接访问对应平台或解析引擎所需的网络地址。
- 请仅下载自己拥有权利或获得授权的内容，并遵守内容平台条款及所在地区法律。

## 已知限制

- 私密、登录可见、地区限制或年龄限制内容通常无法直接解析。
- 平台网页或接口调整后，专用解析器可能暂时失效，可尝试更新 yt-dlp。
- 部分服务器不支持 HTTP Range，请求中断后可能无法从原进度续传。
- 当前发布配置仅面向 64 位 ARM Android 设备。
