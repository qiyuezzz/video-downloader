package com.example.videodownload.ui.home

import com.example.videodownload.data.model.DownloadHistoryItem

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadHistoryScreen(
    viewModel: HomeViewModel,
    onPlayVideo: (String, String) -> Unit
) {
    val history by viewModel.history.collectAsState()
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
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (isEditMode) "已选择 ${selectedItems.size} 项" else "下载历史",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                },
                actions = {
                    if (history.isNotEmpty()) {
                        if (isEditMode) {
                            FilledTonalIconButton(onClick = {
                                if (selectedItems.size == history.size) {
                                    selectedItems.clear()
                                } else {
                                    selectedItems.clear()
                                    selectedItems.addAll(history.map { it.id })
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
                            FilledTonalIconButton(onClick = { isEditMode = true }) {
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
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "下载列表空空如也",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "去首页粘贴链接开始下载吧",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    items(history, key = { it.id }) { item ->
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
                            }
                        )
                    }
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
        else MaterialTheme.colorScheme.surfaceVariant,
        label = "Color"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(cardPadding)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        shadowElevation = if (isSelected) 4.dp else 0.dp,
        border = if (isEditMode) BorderStroke(1.dp, borderColor) else null
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
                if (item.thumbnailUrl != null) {
                    AsyncImage(
                        model = item.thumbnailUrl,
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
