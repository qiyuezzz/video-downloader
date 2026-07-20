package com.example.videodownload.ui.settings

import android.content.Intent
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.videodownload.data.SettingsDataStore
import com.example.videodownload.R
import com.example.videodownload.util.AppLanguage

private val QUALITY_OPTIONS = listOf(
    SettingsDataStore.QUALITY_BEST to R.string.settings_quality_best,
    SettingsDataStore.QUALITY_720P to null,
    SettingsDataStore.QUALITY_480P to null,
)

private val THEME_OPTIONS = listOf(
    SettingsDataStore.THEME_SYSTEM to R.string.settings_theme_system,
    SettingsDataStore.THEME_LIGHT to R.string.settings_theme_light,
    SettingsDataStore.THEME_DARK to R.string.settings_theme_dark,
)

private val LANGUAGE_OPTIONS = listOf(
    AppLanguage.CHINESE to R.string.settings_language_chinese,
    AppLanguage.ENGLISH to R.string.settings_language_english,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appUpdateViewModel: AppUpdateViewModel,
    viewModel: SettingsViewModel = viewModel(),
) {
    val saveLocation by viewModel.saveLocation.collectAsStateWithLifecycle()
    val saveLocationName by viewModel.saveLocationName.collectAsStateWithLifecycle()
    val preferredQuality by viewModel.preferredQuality.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val wifiOnlyDownload by viewModel.wifiOnlyDownload.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val currentYtDlpVersion by viewModel.currentYtDlpVersion.collectAsStateWithLifecycle()
    val historyRestoreState by viewModel.historyRestoreState.collectAsStateWithLifecycle()
    val appUpdateState by appUpdateViewModel.appUpdateState.collectAsStateWithLifecycle()
    val appCurrentVersion by appUpdateViewModel.currentVersion.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val selectedLanguage = AppLanguage.selectedLanguage(context) ?: AppLanguage.CHINESE

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setSaveLocation(it.toString())
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.settings_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ---- 保存位置 ----
            SettingsSectionHeader(title = stringResource(R.string.settings_save_location), icon = Icons.Outlined.Folder)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { directoryPickerLauncher.launch(null) },
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = saveLocationName ?: if (saveLocation == null) {
                                stringResource(R.string.settings_choose_directory)
                            } else {
                                stringResource(R.string.settings_directory_selected)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        if (saveLocation == null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_directory_missing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when (val state = historyRestoreState) {
                                    HistoryRestoreState.Idle -> stringResource(R.string.settings_directory_idle)
                                    HistoryRestoreState.Scanning -> stringResource(R.string.settings_directory_scanning)
                                    is HistoryRestoreState.Success -> if (state.restoredCount > 0) {
                                        stringResource(R.string.settings_directory_restored, state.restoredCount)
                                    } else {
                                        stringResource(R.string.settings_directory_no_new)
                                    }
                                    is HistoryRestoreState.Error -> stringResource(R.string.settings_directory_scan_failed, state.message)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (historyRestoreState is HistoryRestoreState.Error) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ---- 外观 ----
            SettingsSectionHeader(title = stringResource(R.string.settings_appearance), icon = Icons.Outlined.Palette)
            Spacer(modifier = Modifier.height(8.dp))
            CompactOptionSelector(
                options = THEME_OPTIONS,
                selectedValue = themeMode,
                onSelected = viewModel::setThemeMode,
            )

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionHeader(title = stringResource(R.string.settings_language), icon = Icons.Outlined.Language)
            Spacer(modifier = Modifier.height(8.dp))
            CompactOptionSelector(
                options = LANGUAGE_OPTIONS,
                selectedValue = selectedLanguage,
                onSelected = { language ->
                    if (language != selectedLanguage) {
                        AppLanguage.setLanguage(context, language)
                        context.findActivity()?.recreate()
                    }
                },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ---- 画质偏好 ----
            SettingsSectionHeader(title = stringResource(R.string.settings_quality), icon = Icons.Outlined.Tune)
            Spacer(modifier = Modifier.height(8.dp))
            CompactOptionSelector(
                options = QUALITY_OPTIONS,
                selectedValue = preferredQuality,
                onSelected = viewModel::setPreferredQuality,
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ---- 下载 ----
            SettingsSectionHeader(title = stringResource(R.string.settings_download), icon = Icons.Outlined.Download)
            Spacer(modifier = Modifier.height(8.dp))
            SettingsToggleRow(
                icon = Icons.Outlined.Wifi,
                title = stringResource(R.string.settings_wifi_only),
                subtitle = stringResource(R.string.settings_wifi_only_subtitle),
                checked = wifiOnlyDownload,
                onCheckedChange = viewModel::setWifiOnlyDownload,
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ---- yt-dlp 引擎 ----
            SettingsSectionHeader(title = stringResource(R.string.settings_parser_engine), icon = Icons.Outlined.Build)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Build,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_parser_engine),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val (subtitle, subtitleColor) = when (val state = updateState) {
                                is UpdateState.Updating ->
                                    stringResource(R.string.settings_updating) to MaterialTheme.colorScheme.primary
                                is UpdateState.Success ->
                                    state.version to MaterialTheme.colorScheme.secondary
                                is UpdateState.Error ->
                                    state.message to MaterialTheme.colorScheme.error
                                else ->
                                    (currentYtDlpVersion
                                        ?.let { stringResource(R.string.settings_ytdlp_current_version, it) }
                                        ?: stringResource(R.string.settings_ytdlp_version_unknown)) to
                                        MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = subtitleColor,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        when (val state = updateState) {
                            is UpdateState.Updating -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = {}, enabled = false) {
                                    Text(stringResource(R.string.settings_updating))
                                }
                            }
                            is UpdateState.Success -> {
                                TextButton(onClick = { viewModel.updateYoutubeDl() }) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.settings_update_again))
                                }
                            }
                            is UpdateState.Error -> {
                                TextButton(onClick = { viewModel.updateYoutubeDl() }) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.settings_retry_update))
                                }
                            }
                            else -> {
                                TextButton(onClick = { viewModel.updateYoutubeDl() }) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.settings_check_update))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ---- 应用更新 ----
            SettingsSectionHeader(title = stringResource(R.string.settings_app_update), icon = Icons.Outlined.SystemUpdate)
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.SystemUpdate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_app_update),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val (subtitle, subtitleColor) = when (val state = appUpdateState) {
                                is AppUpdateState.Checking ->
                                    stringResource(R.string.settings_app_update_checking) to MaterialTheme.colorScheme.primary
                                is AppUpdateState.UpToDate ->
                                    stringResource(R.string.settings_app_update_uptodate) to MaterialTheme.colorScheme.secondary
                                is AppUpdateState.UpdateAvailable ->
                                    stringResource(R.string.settings_app_update_available, state.version) to MaterialTheme.colorScheme.primary
                                is AppUpdateState.Downloading ->
                                    stringResource(R.string.settings_app_update_downloading, state.progress) to MaterialTheme.colorScheme.primary
                                is AppUpdateState.ReadyToInstall ->
                                    stringResource(R.string.settings_app_update_ready) to MaterialTheme.colorScheme.secondary
                                is AppUpdateState.Error ->
                                    state.message to MaterialTheme.colorScheme.error
                                else ->
                                    (appCurrentVersion
                                        ?.let { stringResource(R.string.settings_app_update_current_version, it) }
                                        ?: stringResource(R.string.settings_app_update_version_unknown)) to
                                        MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = subtitleColor,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        when (val state = appUpdateState) {
                            is AppUpdateState.Checking -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = {}, enabled = false) {
                                    Text(stringResource(R.string.settings_app_update_checking))
                                }
                            }
                            is AppUpdateState.UpToDate -> {
                                TextButton(onClick = { appUpdateViewModel.checkForUpdates(force = true) }) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.settings_app_update_check_again))
                                }
                            }
                            is AppUpdateState.UpdateAvailable -> {
                                Button(
                                    onClick = { appUpdateViewModel.downloadUpdate() },
                                    enabled = state.apkUrl.isNotBlank()
                                ) {
                                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (state.apkUrl.isBlank()) stringResource(R.string.settings_app_update_no_apk)
                                        else stringResource(R.string.settings_app_update_download)
                                    )
                                }
                            }
                            is AppUpdateState.Downloading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            is AppUpdateState.ReadyToInstall -> {
                                Button(onClick = { appUpdateViewModel.installUpdate() }) {
                                    Icon(Icons.Outlined.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.settings_app_update_install))
                                }
                            }
                            is AppUpdateState.Error -> {
                                TextButton(onClick = { appUpdateViewModel.checkForUpdates(force = true) }) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.settings_app_update_retry))
                                }
                            }
                            else -> {
                                TextButton(onClick = { appUpdateViewModel.checkForUpdates(force = true) }) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.settings_app_update_check))
                                }
                            }
                        }
                    }

                    // 复杂态在主行下方展开详细内容
                    when (val state = appUpdateState) {
                        is AppUpdateState.UpdateAvailable -> {
                            if (state.releaseNotes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.releaseNotes.take(MAX_RELEASE_NOTES_LENGTH),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp),
                                        maxLines = 4,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (state.apkUrl.isBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { openUrl(context, state.htmlUrl) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Outlined.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.settings_app_update_view_release))
                                }
                            }
                        }
                        is AppUpdateState.Downloading -> {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { state.progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                        else -> {}
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactOptionSelector(
    options: List<Pair<String, Int?>>,
    selectedValue: String,
    onSelected: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            options.forEachIndexed { index, (value, labelRes) ->
                SegmentedButton(
                    selected = selectedValue == value,
                    onClick = { onSelected(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    colors = SegmentedButtonDefaults.colors(
                        // 选中态沿用主题 primary 系，避免默认 secondary 带来的青绿色与主题不统一
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        // 所有选项均不单独描边，分组边框仅由外层 Surface 提供，避免选中色覆盖边框造成断裂感
                        activeBorderColor = Color.Transparent,
                        inactiveContainerColor = Color.Transparent,
                        inactiveBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                    icon = {},
                ) {
                    Text(
                        labelRes?.let { stringResource(it) } ?: value,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 用系统浏览器打开 URL，失败时静默忽略（无可用浏览器时不应崩溃）。 */
private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

/** 发布说明展示长度上限，避免过长内容撑爆卡片。 */
private const val MAX_RELEASE_NOTES_LENGTH = 280

@Composable
private fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 设置页开关行：左侧图标 + 标题/副标题 + 右侧 Switch。
 * 视觉风格与"保存位置"卡片保持一致。
 */
@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}
