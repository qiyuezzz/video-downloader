package com.example.videodownload.ui.home

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

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

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = {
                Column {
                    Text("确定要从列表中删除选中的 ${selectedItems.size} 项记录吗？")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { deletePhysicalFile = !deletePhysicalFile }
                    ) {
                        Checkbox(
                            checked = deletePhysicalFile,
                            onCheckedChange = { deletePhysicalFile = it }
                        )
                        Text("同时删除本地视频文件", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeHistoryItems(selectedItems.toList(), deletePhysicalFile)
                        selectedItems.clear()
                        isEditMode = false
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "已选择 ${selectedItems.size} 项" else "下载历史") },
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
                            IconButton(onClick = {
                                if (selectedItems.isNotEmpty()) {
                                    showDeleteDialog = true
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除选中", tint = MaterialTheme.colorScheme.error)
                            }
                            TextButton(onClick = { 
                                isEditMode = false
                                selectedItems.clear()
                            }) {
                                Text("取消")
                            }
                        } else {
                            TextButton(onClick = { isEditMode = true }) {
                                Text("编辑")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(text = "暂无下载记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history, key = { it.id }) { item ->
                    HistoryCard(
                        item = item,
                        isEditMode = isEditMode,
                        isSelected = selectedItems.contains(item.id),
                        onSelect = {
                            if (selectedItems.contains(item.id)) {
                                selectedItems.remove(item.id)
                            } else {
                                selectedItems.add(item.id)
                            }
                        },
                        onClick = {
                            if (isEditMode) {
                                if (selectedItems.contains(item.id)) {
                                    selectedItems.remove(item.id)
                                } else {
                                    selectedItems.add(item.id)
                                }
                            } else {
                                // 检查文件是否存在
                                val uri = Uri.parse(item.fileUri)
                                val docFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                                if (docFile != null && docFile.exists()) {
                                    onPlayVideo(item.fileUri, item.title)
                                } else {
                                    Toast.makeText(context, "视频文件不存在或已被删除", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryCard(
    item: DownloadHistoryItem,
    isEditMode: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEditMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelect() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Box(
                modifier = Modifier
                    .size(width = 100.dp, height = 56.dp)
                    .clip(RoundedCornerShape(4.dp))
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
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
