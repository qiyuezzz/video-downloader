package com.example.videodownload.ui.home

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.stringResource
import com.example.videodownload.R

/**
 * Nova 风格统一对话框
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
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (showPhysicalDeleteOption) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPhysicalDeleteToggle(!isPhysicalDeleteChecked) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Checkbox(
                                checked = isPhysicalDeleteChecked,
                                onCheckedChange = { onPhysicalDeleteToggle(it) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.error
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.history_delete_file_option),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showPhysicalDeleteOption)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                    contentColor = if (showPhysicalDeleteOption)
                        MaterialTheme.colorScheme.onError
                    else
                        MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (showPhysicalDeleteOption) {
                        stringResource(R.string.common_delete)
                    } else {
                        stringResource(R.string.common_confirm)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.common_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/**
 * 检查文件是否存在，存在则执行 action，不存在则弹出提示
 */
fun checkFileAndRun(context: Context, uriString: String, action: () -> Unit) {
    try {
        val uri = uriString.toUri()
        val docFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)

        if (docFile != null && docFile.exists()) {
            action()
        } else {
            Toast.makeText(context, context.getString(R.string.history_file_missing), Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(
            context,
            context.getString(R.string.history_operation_failed, e.message.orEmpty()),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

/**
 * 通用的打开视频逻辑（带兼容性优化）
 */
fun openVideo(context: Context, uriString: String) {
    checkFileAndRun(context, uriString) {
        val uri = uriString.toUri()
        val mimeType = context.contentResolver.getType(uri) ?: "video/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, context.getString(R.string.history_choose_player))
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
