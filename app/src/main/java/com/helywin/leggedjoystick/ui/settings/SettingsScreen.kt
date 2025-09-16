/*********************************************************************************
 * FileName: SettingsScreen.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 设置页面UI
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helywin.leggedjoystick.data.AppSettings
import timber.log.Timber

/**
 * 设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSettings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onBackClick: () -> Unit
) {
    var zmqIp by remember { mutableStateOf(currentSettings.zmqIp) }
    var zmqPort by remember { mutableStateOf(currentSettings.zmqPort.toString()) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 顶部应用栏
        TopAppBar(
            title = { Text("设置") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
        
        // 设置内容
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ZMQ连接设置
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ZMQ连接设置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    // IP地址输入
                    OutlinedTextField(
                        value = zmqIp,
                        onValueChange = { zmqIp = it },
                        label = { Text("IP地址") },
                        placeholder = { Text("127.0.0.1") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    // 端口输入
                    OutlinedTextField(
                        value = zmqPort,
                        onValueChange = { value ->
                            // 只允许数字输入
                            if (value.all { it.isDigit() } && value.length <= 5) {
                                zmqPort = value
                            }
                        },
                        label = { Text("端口") },
                        placeholder = { Text("33445") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    
                    // 保存按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                val port = zmqPort.toIntOrNull() ?: currentSettings.zmqPort
                                val newSettings = currentSettings.copy(
                                    zmqIp = zmqIp.trim(),
                                    zmqPort = port
                                )
                                onSettingsChange(newSettings)
                                Timber.i("ZMQ设置已保存: IP=$zmqIp, Port=$port")
                            }
                        ) {
                            Text("保存")
                        }
                    }
                }
            }
            
            // 应用信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "应用信息",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text("版本: 1.0.0")
                    Text("作者: helywin")
                    Text("描述: 四足机器人遥控器")
                }
            }
            
            // 使用说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "使用说明",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text("• 配置ZMQ服务器的IP地址和端口")
                    Text("• 点击连接按钮连接到机器人")
                    Text("• 使用摇杆控制机器人移动")
                    Text("• 选择不同模式控制机器人状态")
                    Text("• 狂暴模式下速度限制提升到2倍")
                }
            }
        }
    }
}