package com.example.videodownload.ui.home

import com.example.videodownload.data.model.DownloadHistoryItem
import com.example.videodownload.data.SettingsDataStore
import com.example.videodownload.util.DurationFormatter
import com.example.videodownload.util.VideoPlatform

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*
import com.example.videodownload.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadHistoryScreen(
    viewModel: HomeViewModel,
    onPlayVideo: (String, String) -> Unit
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val layoutMode by viewModel.historyLayout.collectAsStateWithLifecycle()
    val showTitle by viewModel.historyShowTitle.collectAsStateWithLifecycle()
    val platformCounts = remember(history) {
        history.groupingBy(::historyPlatform).eachCount()
    }
    val platformFilters = remember(history, platformCounts) {
        listOf(ALL_PLATFORM to history.size) + PLATFORM_ORDER.mapNotNull { platform ->
            platformCounts[platform]?.let { platform to it }
        }
    }
    var selectedPlatform by rememberSaveable { mutableStateOf(ALL_PLATFORM) }
    LaunchedEffect(platformFilters) {
        if (platformFilters.none { it.first == selectedPlatform }) selectedPlatform = ALL_PLATFORM
    }
    val filteredHistory = remember(history, selectedPlatform) {
        if (selectedPlatform == ALL_PLATFORM) history
        else history.filter { historyPlatform(it) == selectedPlatform }
    }
    val context = LocalContext.current

    var isEditMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<String>() }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletePhysicalFile by remember { mutableStateOf(false) }

    fun toggleSelection(id: String) {
        if (id in selectedItems) selectedItems.remove(id) else selectedItems.add(id)
    }

    if (showDeleteDialog) {
        NovaDeleteDialog(
            title = stringResource(R.string.history_delete_title),
            content = stringResource(R.string.history_delete_message, selectedItems.size),
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                viewModel.removeHistoryItems(selectedItems.toList(), deletePhysicalFile)
                selectedItems.clear()
                isEditMode = false
                showDeleteDialog = false
            },
            showPhysicalDeleteOption = true,
            isPhysicalDeleteChecked = deletePhysicalFile,
            onPhysicalDeleteToggle = { deletePhysicalFile = it }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (isEditMode) {
                                stringResource(R.string.common_selected_count, selectedItems.size)
                            } else {
                                stringResource(R.string.history_title)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (!isEditMode) {
                            Text(
                                when {
                                    history.isEmpty() -> stringResource(R.string.history_empty_hint)
                                    selectedPlatform == ALL_PLATFORM -> stringResource(R.string.history_total, history.size)
                                    else -> stringResource(
                                        R.string.history_platform_total,
                                        platformDisplayName(selectedPlatform),
                                        filteredHistory.size,
                                    )
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        if (isEditMode) {
                            FilledTonalIconButton(onClick = {
                                val visibleIds = filteredHistory.map { it.id }
                                if (visibleIds.all { it in selectedItems }) {
                                    selectedItems.removeAll(visibleIds.toSet())
                                } else {
                                    selectedItems.addAll(visibleIds.filter { it !in selectedItems })
                                }
                            }) {
                                Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.common_select_all))
                            }
                            FilledTonalIconButton(
                                onClick = { if (selectedItems.isNotEmpty()) showDeleteDialog = true },
                                enabled = selectedItems.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.history_delete_selected),
                                    tint = if (selectedItems.isNotEmpty())
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                            TextButton(onClick = {
                                isEditMode = false
                                selectedItems.clear()
                            }) {
                                Text(
                                    stringResource(R.string.common_cancel),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            // 列表模式下隐藏标题会让单条视频占满屏宽，体验差，故禁用
                            val titleToggleEnabled = layoutMode != SettingsDataStore.HISTORY_LAYOUT_LIST
                            IconButton(
                                onClick = { viewModel.setHistoryShowTitle(!showTitle) },
                                enabled = titleToggleEnabled,
                            ) {
                                Icon(
                                    imageVector = if (showTitle) {
                                        Icons.Outlined.Title
                                    } else {
                                        Icons.Outlined.HideSource
                                    },
                                    contentDescription = stringResource(
                                        if (titleToggleEnabled) {
                                            if (showTitle) R.string.history_hide_title
                                            else R.string.history_show_title
                                        } else {
                                            R.string.history_title_toggle_disabled
                                        }
                                    ),
                                    tint = if (!titleToggleEnabled) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    } else if (showTitle) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            IconButton(onClick = {
                                viewModel.setHistoryLayout(nextHistoryLayout(layoutMode))
                            }) {
                                Icon(
                                    imageVector = when (layoutMode) {
                                        SettingsDataStore.HISTORY_LAYOUT_LIST -> Icons.Filled.GridView
                                        SettingsDataStore.HISTORY_LAYOUT_GRID -> Icons.Filled.ViewModule
                                        else -> Icons.AutoMirrored.Filled.ViewList
                                    },
                                    contentDescription = when (layoutMode) {
                                        SettingsDataStore.HISTORY_LAYOUT_LIST -> stringResource(R.string.history_switch_two_columns)
                                        SettingsDataStore.HISTORY_LAYOUT_GRID -> stringResource(R.string.history_switch_three_columns)
                                        else -> stringResource(R.string.history_switch_list)
                                    },
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            IconButton(onClick = { isEditMode = true }) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.common_edit),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            if (history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier
                                    .size(88.dp),
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.VideoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(38.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = stringResource(R.string.history_no_content),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.history_no_content_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(platformFilters, key = { it.first }) { (platform, count) ->
                                FilterChip(
                                    selected = selectedPlatform == platform,
                                    onClick = { selectedPlatform = platform },
                                    label = { Text("${platformDisplayName(platform)} · $count") },
                                    leadingIcon = if (selectedPlatform == platform) {
                                        {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    } else null,
                                )
                            }
                        }
                        if (layoutMode != SettingsDataStore.HISTORY_LAYOUT_LIST) {
                            val compact = layoutMode == SettingsDataStore.HISTORY_LAYOUT_COMPACT_GRID
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(if (compact) 3 else 2),
                                modifier = Modifier.weight(1f),
                                contentPadding = if (compact) {
                                    PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                } else {
                                    HISTORY_CONTENT_PADDING
                                },
                                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                            ) {
                                items(filteredHistory.size, key = { filteredHistory[it].id }) { index ->
                                    val item = filteredHistory[index]
                                    NovaHistoryGridCard(
                                        item = item,
                                        compact = compact,
                                        isEditMode = isEditMode,
                                        isSelected = selectedItems.contains(item.id),
                                        showTitle = showTitle,
                                        onClick = {
                                            if (isEditMode) {
                                                toggleSelection(item.id)
                                            } else {
                                                checkFileAndRun(context, item.fileUri) {
                                                    onPlayVideo(item.fileUri, item.title)
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = HISTORY_CONTENT_PADDING,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(filteredHistory, key = { it.id }) { item ->
                                    NovaHistoryCard(
                                        item = item,
                                        isEditMode = isEditMode,
                                        isSelected = selectedItems.contains(item.id),
                                        onClick = {
                                            if (isEditMode) {
                                                toggleSelection(item.id)
                                            } else {
                                                checkFileAndRun(context, item.fileUri) {
                                                    onPlayVideo(item.fileUri, item.title)
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun NovaHistoryGridCard(
    item: DownloadHistoryItem,
    compact: Boolean,
    isEditMode: Boolean,
    isSelected: Boolean,
    showTitle: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(if (compact) 14.dp else 20.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                // 显示标题时固定最小高度避免同行卡片参差；
                // 隐藏标题时移除约束，缩略图按 16:9 紧凑排列，类似相册。
                .then(
                    if (showTitle) {
                        Modifier.heightIn(min = if (compact) 150.dp else 188.dp)
                    } else {
                        Modifier
                    }
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                historyPreviewModel(item)?.let { thumbnail ->
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.45f)) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(if (compact) 4.dp else 6.dp)
                            .size(if (compact) 14.dp else 18.dp),
                    )
                }
                item.durationMillis?.let { duration ->
                    val text = DurationFormatter.format(duration) ?: return@let
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                    ) {
                        Text(
                            text = text,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                if (isEditMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = Color.White,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
            if (showTitle) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (compact) 8.dp else 12.dp),
                ) {
                    Text(
                        text = item.title,
                        style = if (compact) {
                            MaterialTheme.typography.bodySmall
                        } else {
                            MaterialTheme.typography.titleSmall
                        },
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = formatDate(item.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun NovaHistoryCard(
    item: DownloadHistoryItem,
    isEditMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val cardPadding by animateDpAsState(if (isSelected) 4.dp else 0.dp, label = "Padding")
    val borderColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        else Color.Transparent,
        label = "BorderColor"
    )
    val containerColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface,
        label = "Color"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(cardPadding)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        border = BorderStroke(
            1.dp,
            if (isEditMode) borderColor else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEditMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 媒体预览：列表模式始终显示标题，缩略图保持固定尺寸
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                historyPreviewModel(item)?.let { thumbnail ->
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                item.durationMillis?.let { duration ->
                    val text = DurationFormatter.format(duration) ?: return@let
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp),
                    ) {
                        Text(
                            text = text,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatDate(item.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun historyPlatform(item: DownloadHistoryItem): String =
    item.platform.ifBlank { VideoPlatform.folderName(item.webpageUrl) }

/** 网络封面不可用时让 Coil 从本地视频 URI 解码首帧。 */
internal fun historyPreviewModel(item: DownloadHistoryItem): String? =
    item.thumbnailUrl?.takeIf { it.startsWith("https://", ignoreCase = true) }
        ?: item.fileUri.takeIf(String::isNotBlank)

private const val ALL_PLATFORM = "__all__"
private val PLATFORM_ORDER = listOf("Bilibili", "X", "Instagram", "YouTube", "其他")

@Composable
private fun platformDisplayName(platform: String): String = when (platform) {
    ALL_PLATFORM -> stringResource(R.string.history_all)
    "其他" -> stringResource(R.string.history_other)
    else -> platform
}

internal fun nextHistoryLayout(current: Int): Int = when (current) {
    SettingsDataStore.HISTORY_LAYOUT_LIST -> SettingsDataStore.HISTORY_LAYOUT_GRID
    SettingsDataStore.HISTORY_LAYOUT_GRID -> SettingsDataStore.HISTORY_LAYOUT_COMPACT_GRID
    else -> SettingsDataStore.HISTORY_LAYOUT_LIST
}

private val HISTORY_CONTENT_PADDING = PaddingValues(
    start = 20.dp,
    top = 4.dp,
    end = 20.dp,
    bottom = 20.dp,
)
