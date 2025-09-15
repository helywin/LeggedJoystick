/*********************************************************************************
 * FileName: ConnectionDialog.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 连接状态弹窗
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.helywin.leggedjoystick.data.ConnectionState

/**
 * 连接状态对话框
 */
@Composable
fun ConnectionDialog(
    connectionState: ConnectionState,
    onDismiss: () -> Unit,
    onCancel: () -> Unit
) {
    if (connectionState == ConnectionState.CONNECTING) {
        Dialog(
            onDismissRequest = { /* 连接中时不允许点击外部关闭 */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            ConnectionDialogContent(
                state = connectionState,
                onCancel = onCancel
            )
        }
    } else if (connectionState in listOf(
        ConnectionState.CONNECTION_FAILED,
        ConnectionState.CONNECTION_TIMEOUT
    )) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            ConnectionDialogContent(
                state = connectionState,
                onDismiss = onDismiss
            )
        }
    }
}

/**
 * 连接对话框内容
 */
@Composable
private fun ConnectionDialogContent(
    state: ConnectionState,
    onCancel: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (state) {
                ConnectionState.CONNECTING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "连接中...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = "正在尝试连接到机器人",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Button(
                        onClick = { onCancel?.invoke() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("取消连接")
                    }
                }
                
                ConnectionState.CONNECTION_FAILED -> {
                    Text(
                        text = "❌",
                        fontSize = 48.sp
                    )
                    
                    Text(
                        text = "连接失败",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                    
                    Text(
                        text = "无法连接到指定的服务器，请检查网络设置",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Button(
                        onClick = { onDismiss?.invoke() }
                    ) {
                        Text("确定")
                    }
                }
                
                ConnectionState.CONNECTION_TIMEOUT -> {
                    Text(
                        text = "⏰",
                        fontSize = 48.sp
                    )
                    
                    Text(
                        text = "连接超时",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                    
                    Text(
                        text = "连接服务器超时，请检查服务器是否正在运行",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Button(
                        onClick = { onDismiss?.invoke() }
                    ) {
                        Text("确定")
                    }
                }
                
                else -> {}
            }
        }
    }
}