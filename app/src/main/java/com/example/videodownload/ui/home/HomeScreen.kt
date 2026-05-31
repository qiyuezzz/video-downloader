package com.example.videodownload.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.videodownload.data.model.DownloadState
import com.example.videodownload.data.model.VideoFormat
import com.example.videodownload.data.model.VideoInfo

fun openVideo(context: Context, uriString: String) {
    try {
        val uri = Uri.parse(uriString)
        val docFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
        
        if (docFile == null || !docFile.exists()) {
            Toast.makeText(context, "视频文件不存在或已被删除", Toast.LENGTH_SHORT).show()
            return
        }

        val mimeType = context.contentResolver.getType(uri) ?: "video/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val chooser = Intent.createChooser(intent, "选择播放器播放视频")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开视频: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val parseState by viewModel.parseState.collectAsState()
    val downloadTasks by viewModel.downloadTasks.collectAsState()
    val clipboardUrl by viewModel.clipboardUrl.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    var showBottomSheet by remember { mutableStateOf(false) }

    // 每次进入或回到页面时读取剪贴板
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
            }
        }
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
            TopAppBar(
                title = { Text("视频下载器") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
                contentColor = MaterialTheme.colorScheme.onPrimary
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
            // 主体内容区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 粘贴链接区域
                PasteSection(
                    clipboardUrl = clipboardUrl,
                    onPaste = { url -> viewModel.parseUrl(url) },
                    onClear = { viewModel.resetParseState() },
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 解析状态展示
                when (val state = parseState) {
                    is ParseState.Idle -> { 
                         Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Text(text = "请粘贴视频链接开始解析", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    is ParseState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is ParseState.Success -> {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "解析成功！", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = state.videoInfo.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { showBottomSheet = true }) {
                                    Text("打开选集列表")
                                }
                            }
                        }
                    }
                    is ParseState.Error -> {
                        ErrorCard(message = state.message)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.resetParseState() }, modifier = Modifier.fillMaxWidth()) {
                            Text("重试")
                        }
                    }
                }
            }

            // 正在下载的任务
            val activeTasks = downloadTasks.filter { it.state !is DownloadState.Success && it.state !is DownloadState.Error }
            if (activeTasks.isNotEmpty()) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Text(
                            text = "正在下载 (${activeTasks.size})",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(activeTasks, key = { it.id }) { task ->
                                DownloadTaskItem(task = task, onRemove = {})
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadTaskItem(task: DownloadTask, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.width(180.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                    AsyncImage(model = task.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(text = task.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                when (val state = task.state) {
                    is DownloadState.Idle -> Text("等待中...", style = MaterialTheme.typography.labelSmall)
                    is DownloadState.Progress -> {
                        LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("${state.percent}%", style = MaterialTheme.typography.labelSmall)
                    }
                    else -> {} 
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
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text = "解析结果", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            if (previewUrl != null) {
                VideoPreview(
                    videoUrl = previewUrl!!,
                    webpageUrl = videoInfo.webpageUrl,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                TextButton(onClick = { previewUrl = null }) {
                    Text("关闭预览")
                }
            }

            Text("请勾选要下载的内容：", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(videoInfo.formats) { format ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                selectedFormats = if (selectedFormats.contains(format)) {
                                    selectedFormats - format
                                } else {
                                    selectedFormats + format
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = selectedFormats.contains(format), onCheckedChange = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .aspectRatio(16f/9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                                .clickable { previewUrl = format.url },
                            contentAlignment = Alignment.Center
                        ) {
                            if (format.thumbnailUrl != null) {
                                AsyncImage(model = format.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop)
                            }
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = format.quality, style = MaterialTheme.typography.bodyLarge)
                            Text(text = format.ext.uppercase() + (format.filesize?.let { " - ${formatSize(it)}" } ?: ""), 
                                 style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onDownload(selectedFormats.toList()) },
                enabled = selectedFormats.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (selectedFormats.isNotEmpty()) "立即下载 (${selectedFormats.size})" else "请选择视频")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PasteSection(
    clipboardUrl: String?,
    onPaste: (String) -> Unit,
    onClear: () -> Unit,
) {
    var manualUrl by remember { mutableStateOf("") }

    LaunchedEffect(clipboardUrl) {
        if (clipboardUrl == null) manualUrl = ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "粘贴视频链接", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = manualUrl,
                onValueChange = { manualUrl = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://...") },
                singleLine = true,
                trailingIcon = {
                    if (manualUrl.isNotEmpty()) {
                        IconButton(onClick = { manualUrl = ""; onClear() }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { if (manualUrl.isNotBlank()) onPaste(manualUrl) },
                enabled = manualUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("解析")
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("error", message))
                Toast.makeText(context, "已复制错误日志", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
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
