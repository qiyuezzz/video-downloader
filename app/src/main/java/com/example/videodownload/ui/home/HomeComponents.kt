package com.example.videodownload.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Nova 风格的统一样式对话框
 */
@Composable
fun NovaDeleteDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    showPhysicalDeleteOption: Boolean = false,
    isPhysicalDeleteChecked: Boolean = false,
    onPhysicalDeleteToggle: (Boolean) -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(content)
                if (showPhysicalDeleteOption) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPhysicalDeleteToggle(!isPhysicalDeleteChecked) }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = isPhysicalDeleteChecked,
                            onCheckedChange = { onPhysicalDeleteToggle(it) }
                        )
                        Text(
                            "同时删除本地视频文件", 
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showPhysicalDeleteOption) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (showPhysicalDeleteOption) "删除" else "确定", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 检查文件是否存在，存在则执行 action，不存在则弹出提示
 */
fun checkFileAndRun(context: Context, uriString: String, action: () -> Unit) {
    try {
        val uri = Uri.parse(uriString)
        val docFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
        
        if (docFile != null && docFile.exists()) {
            action()
        } else {
            Toast.makeText(context, "视频文件不存在或已被删除", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 通用的打开视频逻辑（带兼容性优化）
 */
fun openVideo(context: Context, uriString: String) {
    checkFileAndRun(context, uriString) {
        val uri = Uri.parse(uriString)
        val mimeType = context.contentResolver.getType(uri) ?: "video/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val chooser = Intent.createChooser(intent, "选择播放器播放视频")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
