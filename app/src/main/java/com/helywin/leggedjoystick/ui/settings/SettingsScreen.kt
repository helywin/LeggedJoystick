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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    var controllerPort by remember { mutableStateOf(currentSettings.controllerPort.toString()) }
    var controllerToken by remember { mutableStateOf(currentSettings.controllerToken) }
    var headRtspUrl by remember { mutableStateOf(currentSettings.headRtspUrl) }
    var tailRtspUrl by remember { mutableStateOf(currentSettings.tailRtspUrl) }
    var keepScreenOn by remember { mutableStateOf(currentSettings.keepScreenOn) }
    var engineeringMockEnabled by remember { mutableStateOf(currentSettings.engineeringMockEnabled) }
    var remoteInputHost by remember { mutableStateOf(currentSettings.remoteInputHost) }
    var remoteInputPort by remember { mutableStateOf(currentSettings.remoteInputPort.toString()) }
    var remoteInputLocalPort by remember { mutableStateOf(currentSettings.remoteInputLocalPort.toString()) }
    var remoteInputRawForwardEnabled by remember { mutableStateOf(currentSettings.remoteInputRawForwardEnabled) }
    var remoteInputRawForwardPort by remember { mutableStateOf(currentSettings.remoteInputRawForwardPort.toString()) }
    var remoteInputDeadZone by remember { mutableStateOf(currentSettings.remoteInputDeadZone.toString()) }
    var remoteInputTimeoutMs by remember { mutableStateOf(currentSettings.remoteInputTimeoutMs.toString()) }
    var forwardChannel by remember { mutableStateOf(currentSettings.remoteInputForwardChannel.toString()) }
    var forwardInverted by remember { mutableStateOf(currentSettings.remoteInputForwardInverted) }
    var strafeRightChannel by remember { mutableStateOf(currentSettings.remoteInputStrafeRightChannel.toString()) }
    var strafeRightInverted by remember { mutableStateOf(currentSettings.remoteInputStrafeRightInverted) }
    var yawRightChannel by remember { mutableStateOf(currentSettings.remoteInputYawRightChannel.toString()) }
    var yawRightInverted by remember { mutableStateOf(currentSettings.remoteInputYawRightInverted) }
    var headPitchChannel by remember { mutableStateOf(currentSettings.remoteInputHeadPitchChannel.toString()) }
    var headPitchInverted by remember { mutableStateOf(currentSettings.remoteInputHeadPitchInverted) }
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

                    NumberSettingField(
                        value = controllerPort,
                        onValueChange = { controllerPort = it },
                        label = "机器人主控端口",
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = controllerToken,
                        onValueChange = { controllerToken = it },
                        label = { Text("机器人主控部署令牌") },
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = "主控令牌")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text(
                        text = "33445 用于低延迟遥控，33446 用于模式、建图、地图和位置。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 视频流设置
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "视频流设置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedTextField(
                        value = headRtspUrl,
                        onValueChange = { headRtspUrl = it },
                        label = { Text("头部 RTSP 视频流地址") },
                        placeholder = { Text("rtsp://192.168.234.1:8554/front") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "头部视频流地址"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = tailRtspUrl,
                        onValueChange = { tailRtspUrl = it },
                        label = { Text("尾部 RTSP 视频流地址") },
                        placeholder = { Text("rtsp://192.168.234.1:8554/back") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "尾部视频流地址"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // 遥控输入设置
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "遥控输入",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedTextField(
                        value = remoteInputHost,
                        onValueChange = { remoteInputHost = it },
                        label = { Text("UniRC UDP 地址") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = "遥控输入地址"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        NumberSettingField(
                            value = remoteInputPort,
                            onValueChange = { remoteInputPort = it },
                            label = "远端端口",
                            modifier = Modifier.weight(1f)
                        )
                        NumberSettingField(
                            value = remoteInputLocalPort,
                            onValueChange = { remoteInputLocalPort = it },
                            label = "本地端口",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "原始 UDP 转发",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "收到的 UniRC 原始报文转发到 127.0.0.1，供其他 App 自行解析",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = remoteInputRawForwardEnabled,
                            onCheckedChange = { remoteInputRawForwardEnabled = it }
                        )
                        NumberSettingField(
                            value = remoteInputRawForwardPort,
                            onValueChange = { remoteInputRawForwardPort = it },
                            label = "转发端口",
                            modifier = Modifier.width(128.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DecimalSettingField(
                            value = remoteInputDeadZone,
                            onValueChange = { remoteInputDeadZone = it },
                            label = "死区",
                            modifier = Modifier.weight(1f)
                        )
                        NumberSettingField(
                            value = remoteInputTimeoutMs,
                            onValueChange = { remoteInputTimeoutMs = it },
                            label = "超时 ms",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = "通道映射",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AxisMappingRow(
                        label = "前进/后退",
                        channel = forwardChannel,
                        inverted = forwardInverted,
                        onChannelChange = { forwardChannel = it },
                        onInvertedChange = { forwardInverted = it }
                    )
                    AxisMappingRow(
                        label = "左右平移",
                        channel = strafeRightChannel,
                        inverted = strafeRightInverted,
                        onChannelChange = { strafeRightChannel = it },
                        onInvertedChange = { strafeRightInverted = it }
                    )
                    AxisMappingRow(
                        label = "左右转向",
                        channel = yawRightChannel,
                        inverted = yawRightInverted,
                        onChannelChange = { yawRightChannel = it },
                        onInvertedChange = { yawRightInverted = it }
                    )
                    AxisMappingRow(
                        label = "原地俯仰",
                        channel = headPitchChannel,
                        inverted = headPitchInverted,
                        onChannelChange = { headPitchChannel = it },
                        onInvertedChange = { headPitchInverted = it }
                    )
                }
            }

            // 应用行为设置
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "应用行为",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // 屏幕常亮开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "保持屏幕常亮",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "应用前台时防止屏幕自动息屏",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = { keepScreenOn = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "工程 Mock 模式",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "本地模拟连接和状态，不发送真实 ZMQ/UDP",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = engineeringMockEnabled,
                            onCheckedChange = { engineeringMockEnabled = it }
                        )
                    }
                }
            }

            // 保存按钮
            Button(
                onClick = {
                    val port = zmqPort.toIntOrNull() ?: currentSettings.zmqPort
                    val parsedControllerPort = parsePort(
                        controllerPort,
                        currentSettings.controllerPort
                    )
                    val newSettings = currentSettings.copy(
                        zmqIp = zmqIp.trim(),
                        zmqPort = port,
                        controllerPort = parsedControllerPort,
                        controllerToken = controllerToken,
                        headRtspUrl = headRtspUrl.trim(),
                        tailRtspUrl = tailRtspUrl.trim(),
                        remoteInputHost = remoteInputHost.trim(),
                        remoteInputPort = parsePort(remoteInputPort, currentSettings.remoteInputPort),
                        remoteInputLocalPort = parseLocalPort(
                            remoteInputLocalPort,
                            currentSettings.remoteInputLocalPort
                        ),
                        remoteInputRawForwardEnabled = remoteInputRawForwardEnabled,
                        remoteInputRawForwardPort = parsePort(
                            remoteInputRawForwardPort,
                            currentSettings.remoteInputRawForwardPort
                        ),
                        remoteInputDeadZone = parseDeadZone(
                            remoteInputDeadZone,
                            currentSettings.remoteInputDeadZone
                        ),
                        remoteInputTimeoutMs = parseTimeoutMs(
                            remoteInputTimeoutMs,
                            currentSettings.remoteInputTimeoutMs
                        ),
                        remoteInputForwardChannel = parseChannel(
                            forwardChannel,
                            currentSettings.remoteInputForwardChannel
                        ),
                        remoteInputForwardInverted = forwardInverted,
                        remoteInputStrafeRightChannel = parseChannel(
                            strafeRightChannel,
                            currentSettings.remoteInputStrafeRightChannel
                        ),
                        remoteInputStrafeRightInverted = strafeRightInverted,
                        remoteInputYawRightChannel = parseChannel(
                            yawRightChannel,
                            currentSettings.remoteInputYawRightChannel
                        ),
                        remoteInputYawRightInverted = yawRightInverted,
                        remoteInputHeadPitchChannel = parseChannel(
                            headPitchChannel,
                            currentSettings.remoteInputHeadPitchChannel
                        ),
                        remoteInputHeadPitchInverted = headPitchInverted,
                        keepScreenOn = keepScreenOn,
                        engineeringMockEnabled = engineeringMockEnabled
                    )
                    onSettingsChange(newSettings)
                    Timber.i(
                        "设置已保存: IP=%s, DriverPort=%s, ControllerPort=%s, HeadRTSP=%s, TailRTSP=%s, KeepScreenOn=%s",
                        zmqIp,
                        port,
                        parsedControllerPort,
                        headRtspUrl,
                        tailRtspUrl,
                        keepScreenOn
                    )
                    Toast.makeText(
                        context,
                        "设置已保存",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "保存",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存设置")
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
                    Text("描述: 机器狗遥控器")
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
                    Text("• 配置机器人 IP、驱动端口、主控端口和部署令牌，然后保存")
                    Text("• 点击连接按钮连接到机器人，等待连接成功")
                    Text("• 连接成功后手动遥控走 driver；模式、建图、地图和位置走机器人主控")
                    Text("• UniRC UDP 输入负责移动轴和原地姿态轴，动作和模式由主屏按钮触发")
                    Text("• 原始 UDP 转发默认开启，其他 App 可监听 127.0.0.1:19857 自行解析")
                    Text("• 调试通道映射时先保持低速，确认方向后再提高速度")
                    Text("• 主屏显示头尾双路视频，点击拍照按钮可保存当前主画面")
                }
            }
        }
    }
}

@Composable
private fun NumberSettingField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            if (next.all { it.isDigit() } && next.length <= 5) {
                onValueChange(next)
            }
        },
        label = { Text(label) },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

@Composable
private fun DecimalSettingField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            if (next.count { it == '.' } <= 1 && next.all { it.isDigit() || it == '.' }) {
                onValueChange(next)
            }
        },
        label = { Text(label) },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}

@Composable
private fun AxisMappingRow(
    label: String,
    channel: String,
    inverted: Boolean,
    onChannelChange: (String) -> Unit,
    onInvertedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        NumberSettingField(
            value = channel,
            onValueChange = onChannelChange,
            label = "CH",
            modifier = Modifier.width(96.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "反向",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(
                checked = inverted,
                onCheckedChange = onInvertedChange
            )
        }
    }
}

private fun parsePort(value: String, fallback: Int): Int {
    return value.toIntOrNull()?.coerceIn(1, 65535) ?: fallback
}

private fun parseLocalPort(value: String, fallback: Int): Int {
    return value.toIntOrNull()?.coerceIn(0, 65535) ?: fallback
}

private fun parseDeadZone(value: String, fallback: Float): Float {
    return value.toFloatOrNull()?.coerceIn(0f, 0.95f) ?: fallback
}

private fun parseTimeoutMs(value: String, fallback: Long): Long {
    return value.toLongOrNull()?.coerceIn(100L, 5000L) ?: fallback
}

private fun parseChannel(value: String, fallback: Int): Int {
    return value.toIntOrNull()?.coerceIn(1, 16) ?: fallback
}
