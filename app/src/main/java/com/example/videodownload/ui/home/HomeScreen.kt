package com.example.videodownload.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.videodownload.data.model.DownloadState
import com.example.videodownload.data.model.DownloadTask
import com.example.videodownload.data.model.VideoFormat
import com.example.videodownload.data.model.VideoInfo
import com.example.videodownload.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
) {
    val parseState by viewModel.parseState.collectAsStateWithLifecycle()
    val downloadTasks by viewModel.downloadTasks.collectAsStateWithLifecycle()

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
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.pasteAndParse() },
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 3.dp,
                ),
                icon = {
                    Icon(
                        Icons.Outlined.ContentPasteGo,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                text = {
                    Text("粘贴解析", fontWeight = FontWeight.Bold)
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                // 输入解析区域
                NovaPasteSection(
                    onPaste = { url -> viewModel.parseUrl(url) },
                    onClear = { viewModel.resetParseState() },
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 动态状态切换
                AnimatedContent(
                    targetState = parseState,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.95f)).togetherWith(fadeOut())
                    },
                    label = "ParseStateTransition"
                ) { state ->
                    when (state) {
                        is ParseState.Idle -> {
                            NovaIdleHint()
                        }
                        is ParseState.Loading -> {
                            NovaLoadingIndicator()
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

// ============================================================
// 输入区域
// ============================================================

@Composable
private fun NovaPasteSection(
    onPaste: (String) -> Unit,
    onClear: () -> Unit,
) {
    var manualUrl by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "解析新视频",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "粘贴公开的视频页面链接",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = manualUrl,
                onValueChange = { manualUrl = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                placeholder = {
                    Text(
                        "输入或粘贴视频链接",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                trailingIcon = {
                    if (manualUrl.isNotEmpty()) {
                        IconButton(onClick = { manualUrl = ""; onClear() }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { if (manualUrl.isNotBlank()) onPaste(manualUrl) },
                enabled = manualUrl.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(7.dp))
                Text("开始解析", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("X / Twitter", "B站", "YouTube").forEach { platform ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            platform,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// 空闲提示
// ============================================================

@Composable
private fun NovaIdleHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        Text(
            "简单三步，离线观看",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "链接解析、画质选择和下载状态都集中在一个页面。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HomeFeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Bolt,
                title = "快速解析",
                description = "自动匹配站点",
                color = MaterialTheme.colorScheme.primary,
            )
            HomeFeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.HighQuality,
                title = "自由选画质",
                description = "下载前可预览",
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun HomeFeatureCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    color: Color,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ============================================================
// 加载指示器
// ============================================================

@Composable
private fun NovaLoadingIndicator() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 32.dp)
    ) {
        CircularProgressIndicator(
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "正在解析...",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================================
// 解析结果卡片
// ============================================================

@Composable
private fun NovaResultCard(videoInfo: VideoInfo, onShowOptions: () -> Unit) {
    Surface(
        onClick = onShowOptions,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 7f)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (videoInfo.thumbnailUrl != null) {
                    AsyncImage(
                        model = videoInfo.thumbnailUrl,
                        contentDescription = videoInfo.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                                )
                            )
                    )
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("解析完成", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = videoInfo.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        "发现 ${videoInfo.formats.size} 个可下载画质",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                FilledIconButton(onClick = onShowOptions) {
                    Icon(Icons.Filled.Download, contentDescription = "选择画质")
                }
            }
        }
    }
}

// ============================================================
// 错误卡片
// ============================================================

@Composable
private fun NovaErrorCard(message: String, onRetry: () -> Unit) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "解析失败",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("重试")
                }
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(message))
                        copied = true
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (copied) "已复制" else "复制")
                }
            }
        }
    }
}

// ============================================================
// 活跃下载列表
// ============================================================

@Composable
private fun NovaActiveDownloadList(
    activeTasks: List<DownloadTask>,
    onResume: (String) -> Unit,
    onRemove: (String) -> Unit,
    onResumeAll: () -> Unit,
) {
    val hasResumable = activeTasks.any {
        it.state is DownloadState.Interrupted || it.state is DownloadState.Error
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "正在下载",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(10.dp))
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text("${activeTasks.size}", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.weight(1f))
                if (hasResumable) {
                    TextButton(
                        onClick = onResumeAll,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("全部继续", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(bottom = 20.dp)
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

// ============================================================
// 下载任务卡片
// ============================================================

@Composable
fun DownloadTaskItem(
    task: DownloadTask,
    onResume: () -> Unit = {},
    onRemove: () -> Unit = {},
) {
    val isInterrupted = task.state is DownloadState.Interrupted
    val isError = task.state is DownloadState.Error

    Surface(
        modifier = Modifier.width(190.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            // 缩略图区域
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
                when (task.state) {
                    is DownloadState.Progress -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (task.state.percent >= 0) {
                                CircularProgressIndicator(
                                    progress = { task.state.percent / 100f },
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
                                Icons.Filled.PauseCircle,
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
                                Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = NovaErrorDark.copy(alpha = 0.8f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    else -> {}
                }
            }
            // 信息区域
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
                    is DownloadState.Idle -> Text(
                        "排队中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    is DownloadState.Progress -> {
                        if (state.percent >= 0) {
                            Text(
                                "${state.percent}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                "下载中...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is DownloadState.Interrupted -> Text(
                        "已暂停",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    is DownloadState.Error -> Text(
                        "失败",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                    else -> {}
                }
                if (isInterrupted || isError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = onResume,
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("续传", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = onRemove,
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
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

// ============================================================
// 下载 Bottom Sheet
// ============================================================

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
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 40.dp) },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = "选择下载内容",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = videoInfo.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(20.dp))

            // 视频预览
            if (previewUrl != null) {
                Box(modifier = Modifier.clip(RoundedCornerShape(16.dp))) {
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
                    Text("关闭预览")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                "选择画质",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(videoInfo.formats) { format ->
                    val isSelected = selectedFormats.contains(format)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                selectedFormats = if (isSelected) selectedFormats - format
                                else selectedFormats + format
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (isSelected)
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        else
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    checkmarkColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .width(72.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black)
                                    .clickable { previewUrl = format.url },
                                contentAlignment = Alignment.Center
                            ) {
                                if (format.thumbnailUrl != null) {
                                    AsyncImage(
                                        model = format.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = format.quality,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = format.ext.uppercase() + (format.filesize?.let { " • ${formatSize(it)}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 下载按钮
            Button(
                onClick = { onDownload(selectedFormats.toList()) },
                enabled = selectedFormats.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (selectedFormats.isNotEmpty()) "下载 ${selectedFormats.size} 个视频" else "请选择视频",
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
