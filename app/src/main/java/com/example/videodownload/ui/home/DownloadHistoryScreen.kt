package com.example.videodownload.ui.home

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        if (isEditMode) "已选择 ${selectedItems.size} 项" else "下载历史",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                actions = {
                    if (history.isNotEmpty()) {
                        if (isEditMode) {
                            IconButton(onClick = {
                                if (selectedItems.size == history.size) {
                                    selectedItems.clear()
                                } else {
                                    selectedItems.clear()
                                    selectedItems.addAll(history.map { it.id })
                                }
                            }) {
                                Icon(Icons.Default.SelectAll, contentDescription = "全选")
                            }
                            IconButton(
                                onClick = { if (selectedItems.isNotEmpty()) showDeleteDialog = true },
                                enabled = selectedItems.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.Delete, 
                                    contentDescription = "删除选中", 
                                    tint = if (selectedItems.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Gray
                                )
                            }
                            TextButton(onClick = { 
                                isEditMode = false
                                selectedItems.clear()
                            }) {
                                Text("取消", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            IconButton(onClick = { isEditMode = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = history.isEmpty(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "HistoryListTransition"
        ) { isEmpty ->
            if (isEmpty) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History, 
                            contentDescription = null, 
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "下载列表空空如也", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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
    val transition = updateTransition(isSelected, label = "SelectedState")
    val cardPadding by transition.animateDp(label = "Padding") { selected -> if (selected) 4.dp else 0.dp }
    val containerColor by transition.animateColor(label = "Color") { selected -> 
        if (selected) MaterialTheme.colorScheme.primaryContainer 
        else MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(cardPadding)
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态指示器
            if (isEditMode) {
                RadioButton(
                    selected = isSelected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            // 媒体预览
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 68.dp)
                    .clip(RoundedCornerShape(16.dp))
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
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatDate(item.timestamp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
