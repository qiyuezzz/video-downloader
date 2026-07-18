package com.example.videodownload.ui.home

import com.example.videodownload.data.model.DownloadHistoryItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadHistoryScreen(
    viewModel: HomeViewModel,
    onPlayVideo: (String, String) -> Unit
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val platformCounts = remember(history) {
        history.groupingBy(::historyPlatform).eachCount()
    }
    val platformFilters = remember(history, platformCounts) {
        listOf("全部" to history.size) + PLATFORM_ORDER.mapNotNull { platform ->
            platformCounts[platform]?.let { platform to it }
        }
    }
    var selectedPlatform by rememberSaveable { mutableStateOf("全部") }
    var useGridLayout by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(platformFilters) {
        if (platformFilters.none { it.first == selectedPlatform }) selectedPlatform = "全部"
    }
    val filteredHistory = remember(history, selectedPlatform) {
        if (selectedPlatform == "全部") history
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
            title = "确认删除",
            content = "确定要从列表中删除选中的 ${selectedItems.size} 项记录吗？",
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
                            if (isEditMode) "已选择 ${selectedItems.size} 项" else "下载历史",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (!isEditMode) {
                            Text(
                                when {
                                    history.isEmpty() -> "保存的视频会出现在这里"
                                    selectedPlatform == "全部" -> "共 ${history.size} 个本地视频"
                                    else -> "$selectedPlatform · ${filteredHistory.size} 个视频"
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
                                Icon(Icons.Filled.SelectAll, contentDescription = "全选")
                            }
                            FilledTonalIconButton(
                                onClick = { if (selectedItems.isNotEmpty()) showDeleteDialog = true },
                                enabled = selectedItems.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除选中",
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
                                    "取消",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            IconButton(onClick = { useGridLayout = !useGridLayout }) {
                                Icon(
                                    if (useGridLayout) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                                    contentDescription = if (useGridLayout) "切换到列表" else "切换到网格",
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            IconButton(onClick = { isEditMode = true }) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "编辑",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
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
                                text = "还没有下载内容",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "返回首页粘贴视频链接，选择画质后即可保存到本地。",
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
                                    label = { Text("$platform · $count") },
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
                        if (useGridLayout) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.weight(1f),
                                contentPadding = HISTORY_CONTENT_PADDING,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(filteredHistory.size, key = { filteredHistory[it].id }) { index ->
                                    val item = filteredHistory[index]
                                    NovaHistoryGridCard(
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
    isEditMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
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
        shadowElevation = if (isSelected) 3.dp else 1.dp,
    ) {
        Column {
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
                        modifier = Modifier.padding(6.dp).size(18.dp),
                    )
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
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
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
        shadowElevation = if (isSelected) 4.dp else 1.dp,
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

            // 媒体预览
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

private val PLATFORM_ORDER = listOf("Bilibili", "X", "Instagram", "YouTube", "其他")

private val HISTORY_CONTENT_PADDING = PaddingValues(
    start = 20.dp,
    top = 4.dp,
    end = 20.dp,
    bottom = 20.dp,
)
