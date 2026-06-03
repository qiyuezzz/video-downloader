package com.example.videodownload.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.videodownload.data.model.DownloadState
import com.example.videodownload.data.model.VideoFormat
import com.example.videodownload.data.model.VideoInfo
import com.example.videodownload.ui.theme.NovaGradientPrimary
import com.example.videodownload.ui.theme.NovaGradientPrimaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val isDark = isSystemInDarkTheme()
    val primaryGradient = if (isDark) NovaGradientPrimaryDark else NovaGradientPrimary
    
    val parseState by viewModel.parseState.collectAsState()
    val downloadTasks by viewModel.downloadTasks.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    var showBottomSheet by remember { mutableStateOf(false) }
    var duplicateConfirmEvent by remember { mutableStateOf<HomeEvent.ShowDuplicateConfirm?>(null) }

    // Lifecycle listener for clipboard
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.readClipboard()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is HomeEvent.ShowDownloadOptions -> {
                    showBottomSheet = true
                }
                is HomeEvent.ShowDuplicateConfirm -> {
                    duplicateConfirmEvent = event
                }
            }
        }
    }

    if (duplicateConfirmEvent != null) {
        NovaDeleteDialog(
            title = "重复下载提示",
            content = "检测到您已经下载过该视频，是否仍要重复下载？",
            onDismiss = { duplicateConfirmEvent = null },
            onConfirm = {
                duplicateConfirmEvent?.let { event ->
                    viewModel.downloadVideos(event.videoInfo, event.formats, force = true)
                }
                duplicateConfirmEvent = null
                showBottomSheet = false
            }
        )
    }

    if (showBottomSheet && parseState is ParseState.Success) {
        val successState = parseState as ParseState.Success
        DownloadBottomSheet(
            videoInfo = successState.videoInfo,
            onDismiss = { showBottomSheet = false },
            onDownload = { formats ->
                viewModel.downloadVideos(successState.videoInfo, formats)
                showBottomSheet = false
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "视频下载器",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.pasteAndParse() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = if (isDark) MaterialTheme.colorScheme.onPrimary else Color.White,
                shape = CircleShape,
                modifier = Modifier.shadow(8.dp, CircleShape)
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = "粘贴并解析")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Nova 风格的粘贴区域
                NovaPasteSection(
                    onPaste = { url -> viewModel.parseUrl(url) },
                    onClear = { viewModel.resetParseState() },
                    primaryGradient = primaryGradient
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 动态状态切换动画
                AnimatedContent(
                    targetState = parseState,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.9f)).togetherWith(fadeOut())
                    },
                    label = "ParseStateTransition"
                ) { state ->
                    when (state) {
                        is ParseState.Idle -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Link, 
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "粘贴视频链接开始探索", 
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is ParseState.Loading -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("正在极速解析中...", fontWeight = FontWeight.Medium)
                            }
                        }
                        is ParseState.Success -> {
                            NovaResultCard(
                                videoInfo = state.videoInfo,
                                onShowOptions = { showBottomSheet = true }
                            )
                        }
                        is ParseState.Error -> {
                            NovaErrorCard(
                                message = state.message,
                                onRetry = { viewModel.resetParseState() }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }

            // 底部正在下载区域
            val activeTasks = downloadTasks.filter { it.state !is DownloadState.Success }
            if (activeTasks.isNotEmpty()) {
                NovaActiveDownloadList(
                    activeTasks = activeTasks,
                    onResume = { viewModel.resumeDownload(it) },
                    onRemove = { viewModel.removeIncompleteTask(it) },
                    onResumeAll = { viewModel.resumeAllDownloads() }
                )
            }
        }
    }
}

@Composable
private fun NovaPasteSection(
    onPaste: (String) -> Unit,
    onClear: () -> Unit,
    primaryGradient: Brush
) {
    var manualUrl by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(20.dp)
    ) {
        OutlinedTextField(
            value = manualUrl,
            onValueChange = { manualUrl = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("输入或粘贴视频链接", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                focusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            trailingIcon = {
                if (manualUrl.isNotEmpty()) {
                    IconButton(onClick = { manualUrl = ""; onClear() }) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { if (manualUrl.isNotBlank()) onPaste(manualUrl) },
            enabled = manualUrl.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (manualUrl.isNotBlank()) primaryGradient else Brush.linearGradient(listOf(Color.Gray, Color.Gray))),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("立即解析", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun NovaResultCard(videoInfo: VideoInfo, onShowOptions: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onShowOptions() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.VideoLibrary, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "解析完成",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = videoInfo.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            IconButton(onClick = onShowOptions) {
                Icon(
                    Icons.Default.ChevronRight, 
                    contentDescription = "查看选项",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun NovaErrorCard(message: String, onRetry: () -> Unit) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "解析遇到了点小麻烦",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(14.dp)) {
                    Text("再次尝试")
                }
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(message))
                        copied = true
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (copied) "已复制" else "复制错误")
                }
            }
        }
    }
}

@Composable
private fun NovaActiveDownloadList(
    activeTasks: List<DownloadTask>,
    onResume: (String) -> Unit,
    onRemove: (String) -> Unit,
    onResumeAll: () -> Unit,
) {
    val hasResumable = activeTasks.any { it.state is DownloadState.Interrupted || it.state is DownloadState.Error }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 20.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "正在处理",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                    Text("${activeTasks.size}", color = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onPrimary else Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                if (hasResumable) {
                    TextButton(
                        onClick = onResumeAll,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("全部继续", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                items(activeTasks, key = { it.id }) { task ->
                    DownloadTaskItem(
                        task = task,
                        onResume = { onResume(task.id) },
                        onRemove = { onRemove(task.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadTaskItem(
    task: DownloadTask,
    onResume: () -> Unit = {},
    onRemove: () -> Unit = {},
) {
    val isInterrupted = task.state is DownloadState.Interrupted
    val isError = task.state is DownloadState.Error

    Card(
        modifier = Modifier.width(200.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (task.thumbnailUrl != null) {
                    AsyncImage(
                        model = task.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // 进度覆盖层
                when (task.state) {
                    is DownloadState.Progress -> {
                        val progress = task.state.percent
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (progress >= 0) {
                                CircularProgressIndicator(
                                    progress = { progress / 100f },
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                            } else {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                    is DownloadState.Interrupted -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PauseCircle,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    is DownloadState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    else -> {}
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                when (val state = task.state) {
                    is DownloadState.Idle -> Text("正在排队...", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    is DownloadState.Progress -> {
                        if (state.percent >= 0) {
                            Text("已完成 ${state.percent}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("正在下载...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    is DownloadState.Interrupted -> {
                        Text("下载中断", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    is DownloadState.Error -> {
                        Text("下载失败", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    else -> {}
                }
                // 中断或失败时显示操作按钮
                if (isInterrupted || isError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = onResume,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("续传", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = onRemove,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("移除", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadBottomSheet(
    videoInfo: VideoInfo,
    onDismiss: () -> Unit,
    onDownload: (List<VideoFormat>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedFormats by remember { mutableStateOf(setOfNotNull(videoInfo.formats.firstOrNull())) }
    var previewUrl by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 40.dp) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = "选择要下载的内容", 
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = videoInfo.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(20.dp))

            if (previewUrl != null) {
                Box(modifier = Modifier.clip(RoundedCornerShape(20.dp))) {
                    VideoPreview(
                        videoUrl = previewUrl!!,
                        webpageUrl = videoInfo.webpageUrl,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TextButton(
                    onClick = { previewUrl = null },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("关闭预览", color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text("选择下载画质：", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(videoInfo.formats) { format ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selectedFormats.contains(format)) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable {
                                selectedFormats = if (selectedFormats.contains(format)) {
                                    selectedFormats - format
                                } else {
                                    selectedFormats + format
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedFormats.contains(format), 
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .aspectRatio(16f/9f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black)
                                .clickable { previewUrl = format.url },
                            contentAlignment = Alignment.Center
                        ) {
                            if (format.thumbnailUrl != null) {
                                AsyncImage(model = format.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop)
                            }
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(text = format.quality, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(
                                text = format.ext.uppercase() + (format.filesize?.let { " • ${formatSize(it)}" } ?: ""), 
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onDownload(selectedFormats.toList()) },
                enabled = selectedFormats.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    if (selectedFormats.isNotEmpty()) "立即下载 (${selectedFormats.size} 个视频)" else "请至少选择一个视频",
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun formatSize(bytes: Long): String {
    val KB = 1024L
    val MB = KB * 1024
    val GB = MB * 1024
    return when {
        bytes < KB -> "$bytes B"
        bytes < MB -> "${bytes / KB} KB"
        bytes < GB -> "${"%.1f".format(bytes.toDouble() / MB)} MB"
        else -> "${"%.2f".format(bytes.toDouble() / GB)} GB"
    }
}
