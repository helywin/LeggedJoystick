/*********************************************************************************
 * FileName: SettingsScreen.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 设置页面UI
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helywin.leggedjoystick.BuildConfig
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
    val context = LocalContext.current

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
                        text = "连接设置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // IP地址输入
                    OutlinedTextField(
                        value = zmqIp,
                        onValueChange = { zmqIp = it },
                        label = { Text("IP地址") },
                        placeholder = { Text("127.0.0.1") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Computer,
                                contentDescription = "IP地址"
                            )
                        },
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
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "端口"
                            )
                        },
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
                                Toast.makeText(
                                    context,
                                    "设置已保存",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "保存",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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

                    Text("版本: ${BuildConfig.VERSION_NAME}")
                    Text("作者: helywin")
                    Text("描述: 天马智行机器狗遥控器")
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
                    Text("• 配置服务器的IP地址和端口，然后保存")
                    Text("• 点击连接按钮连接到机器人，等待连接成功")
                    Text("• 手动模式下可以遥控机器狗，自动模式下机器人自主导航")
                    Text("• 选择不同模式控制机器人状态，站立模式才能移动")
                    Text("• 使用物理或者虚拟摇杆控制机器人移动，左边是线速度控制，右边是角速度控制")
                    Text("• 狂暴模式开启时前后速度提升到2m/s，关闭时为1m/s")
                }
            }
        }
    }
}