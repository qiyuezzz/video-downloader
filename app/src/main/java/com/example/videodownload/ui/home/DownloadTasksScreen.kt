package com.example.videodownload.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.videodownload.data.model.DownloadState
import com.example.videodownload.R
import com.example.videodownload.data.model.DownloadTask
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadTasksScreen(
    viewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
) {
    val tasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val selectionMode = selectedIds.isNotEmpty()
    val hasPausableTasks = tasks.any {
        it.state is DownloadState.Idle || it.state is DownloadState.Progress
    }
    val hasResumableTasks = tasks.any {
        it.state is DownloadState.Interrupted || it.state is DownloadState.Error
    }
    val now by produceState(initialValue = System.currentTimeMillis(), tasks) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    BackHandler(enabled = selectionMode) { selectedIds = emptySet() }

    // 续传相关的 WiFi 确认弹窗（新下载确认由 HomeScreen 处理）
    var wifiResumeConfirmEvent by remember { mutableStateOf<HomeEvent.ShowWifiResumeConfirm?>(null) }
    var wifiResumeAllConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is HomeEvent.ShowWifiResumeConfirm -> wifiResumeConfirmEvent = event
                HomeEvent.ShowWifiResumeAllConfirm -> wifiResumeAllConfirm = true
                else -> Unit
            }
        }
    }

    if (wifiResumeConfirmEvent != null) {
        NovaDeleteDialog(
            title = stringResource(R.string.wifi_confirm_title),
            content = stringResource(R.string.wifi_confirm_resume_message),
            onDismiss = { wifiResumeConfirmEvent = null },
            onConfirm = {
                wifiResumeConfirmEvent?.let { event ->
                    viewModel.confirmResumeOnWifi(event.taskId)
                }
                wifiResumeConfirmEvent = null
            }
        )
    }

    if (wifiResumeAllConfirm) {
        NovaDeleteDialog(
            title = stringResource(R.string.wifi_confirm_title),
            content = stringResource(R.string.wifi_confirm_resume_all_message),
            onDismiss = { wifiResumeAllConfirm = false },
            onConfirm = {
                viewModel.confirmResumeAllOnWifi()
                wifiResumeAllConfirm = false
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectionMode) {
                            stringResource(R.string.common_selected_count, selectedIds.size)
                        } else {
                            stringResource(R.string.tasks_title)
                        },
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectionMode) selectedIds = emptySet() else onNavigateBack()
                        },
                    ) {
                        Icon(
                            if (selectionMode) Icons.Outlined.Close
                            else Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = if (selectionMode) {
                                stringResource(R.string.tasks_cancel_selection)
                            } else {
                                stringResource(R.string.common_back)
                            },
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(
                            onClick = {
                                selectedIds.forEach(viewModel::removeIncompleteTask)
                                selectedIds = emptySet()
                            },
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.tasks_delete_selected))
                        }
                    } else if (hasPausableTasks) {
                        IconButton(onClick = viewModel::pauseAllDownloads) {
                            Icon(
                                Icons.Outlined.PauseCircleOutline,
                                contentDescription = stringResource(R.string.tasks_pause_all),
                            )
                        }
                    }
                    if (!selectionMode && hasResumableTasks) {
                        IconButton(onClick = viewModel::resumeAllDownloads) {
                            Icon(
                                Icons.Outlined.PlayCircleOutline,
                                contentDescription = stringResource(R.string.tasks_resume_all),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.tasks_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tasks, key = { it.id }) { task ->
                    DownloadDetailsCard(
                        task = task,
                        now = now,
                        selected = task.id in selectedIds,
                        selectionMode = selectionMode,
                        onToggleSelection = {
                            selectedIds = if (task.id in selectedIds) {
                                selectedIds - task.id
                            } else {
                                selectedIds + task.id
                            }
                        },
                        onStartSelection = { selectedIds = selectedIds + task.id },
                        onPause = { viewModel.pauseDownload(task.id) },
                        onResume = { viewModel.resumeDownload(task.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadDetailsCard(
    task: DownloadTask,
    now: Long,
    selected: Boolean,
    selectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onStartSelection: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val state = task.state
    val downloadedBytes = (state as? DownloadState.Progress)?.downloadedBytes
        ?: task.downloadedBytes
    val totalBytes = (state as? DownloadState.Progress)?.totalBytes
        ?.takeIf { it > 0 }
        ?: task.totalBytes
    val percent = when {
        state is DownloadState.Progress && state.percent >= 0 -> state.percent
        totalBytes > 0 -> ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
        else -> -1
    }
    val pausable = state is DownloadState.Idle || state is DownloadState.Progress
    val resumable = state is DownloadState.Interrupted || state is DownloadState.Error

    Card(
        modifier = Modifier.combinedClickable(
            onClick = {
                when {
                    selectionMode -> onToggleSelection()
                    pausable -> onPause()
                    resumable -> onResume()
                }
            },
            onLongClick = onStartSelection,
        ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .aspectRatio(16f / 9f)
                        .background(Color.Black, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    task.thumbnailUrl?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (state is DownloadState.Error) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        downloadStatusText(state, percent),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state is DownloadState.Error) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                if (selected) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = stringResource(R.string.tasks_selected),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(9.dp)
                            .size(30.dp),
                    )
                } else if (!selectionMode && (pausable || resumable)) {
                    IconButton(
                        onClick = if (pausable) onPause else onResume,
                    ) {
                        Icon(
                            if (pausable) Icons.Outlined.PauseCircleOutline
                            else Icons.Outlined.PlayCircleOutline,
                            contentDescription = if (pausable) {
                                stringResource(R.string.tasks_pause)
                            } else {
                                stringResource(R.string.tasks_resume)
                            },
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (state is DownloadState.Progress && state.percent < 0) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { (percent.coerceAtLeast(0) / 100f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(9.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        R.string.tasks_size,
                        formatBytes(downloadedBytes),
                        formatBytes(totalBytes),
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        R.string.tasks_duration,
                        formatElapsed(task.startedAtMillis, task.finishedAtMillis, now),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                task.durationMillis?.let { duration ->
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(
                            R.string.tasks_video_duration,
                            formatVideoDuration(duration),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

        }
    }
}

@Composable
private fun downloadStatusText(state: DownloadState, percent: Int): String = when (state) {
    DownloadState.Idle -> stringResource(R.string.tasks_waiting)
    is DownloadState.Progress -> if (percent >= 0) {
        stringResource(R.string.tasks_downloading_percent, percent)
    } else {
        stringResource(R.string.tasks_downloading)
    }
    is DownloadState.Success -> stringResource(R.string.tasks_complete)
    is DownloadState.Error -> stringResource(R.string.tasks_failed)
    DownloadState.Interrupted -> stringResource(R.string.tasks_paused)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "--"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) {
        "${value.toLong()} ${units[unit]}"
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
    }
}

private fun formatElapsed(startedAtMillis: Long, finishedAtMillis: Long, now: Long): String {
    if (startedAtMillis <= 0) return "--"
    val endTime = finishedAtMillis.takeIf { it > 0 } ?: now
    val totalSeconds = ((endTime - startedAtMillis).coerceAtLeast(0) / 1_000)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

/** 视频时长格式化，统一委托给 [DurationFormatter]；无法识别时返回 "--"。 */
private fun formatVideoDuration(millis: Long?): String =
    com.example.videodownload.util.DurationFormatter.format(millis) ?: "--"
