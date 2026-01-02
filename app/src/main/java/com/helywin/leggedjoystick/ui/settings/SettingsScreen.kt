/*********************************************************************************
 * FileName: SettingsScreen.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 设置页面UI
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
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
    var rtspUrl by remember { mutableStateOf(currentSettings.rtspUrl) }
    var mainTitle by remember { mutableStateOf(currentSettings.mainTitle) }
    var logoPath by remember { mutableStateOf(currentSettings.logoPath) }
    var keepScreenOn by remember { mutableStateOf(currentSettings.keepScreenOn) }
    val context = LocalContext.current

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // 获取持久化的 URI 权限
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Timber.w(e, "无法获取持久化 URI 权限")
            }
            logoPath = it.toString()
            Timber.i("Logo 图片已选择: $logoPath")
        }
    }

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

                    // RTSP地址输入
                    OutlinedTextField(
                        value = rtspUrl,
                        onValueChange = { rtspUrl = it },
                        label = { Text("RTSP 视频流地址") },
                        placeholder = { Text("rtsp://192.168.133.1:8554/test") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "视频流地址"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
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
                }
            }

            // 界面定制设置
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "界面定制",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // 主屏幕标题输入
                    OutlinedTextField(
                        value = mainTitle,
                        onValueChange = { mainTitle = it },
                        label = { Text("主屏幕标题") },
                        placeholder = { Text("天马智行机器狗遥控器") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Title,
                                contentDescription = "标题"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Logo图片选择
                    Text(
                        text = "Logo 图片",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Logo预览
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (logoPath.isNotEmpty()) {
                                Image(
                                    painter = rememberAsyncImagePainter(logoPath),
                                    contentDescription = "Logo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "选择图片",
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("选择图片")
                            }

                            if (logoPath.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { logoPath = "" },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("清除图片")
                                }
                            }
                        }
                    }

                    if (logoPath.isEmpty()) {
                        Text(
                            text = "未设置 Logo 时将不显示 Logo 图标",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 保存按钮
            Button(
                onClick = {
                    val port = zmqPort.toIntOrNull() ?: currentSettings.zmqPort
                    val newSettings = currentSettings.copy(
                        zmqIp = zmqIp.trim(),
                        zmqPort = port,
                        rtspUrl = rtspUrl.trim(),
                        mainTitle = mainTitle.trim(),
                        logoPath = logoPath,
                        keepScreenOn = keepScreenOn
                    )
                    onSettingsChange(newSettings)
                    Timber.i("设置已保存: IP=$zmqIp, Port=$port, RTSP=$rtspUrl, Title=$mainTitle, Logo=$logoPath, KeepScreenOn=$keepScreenOn")
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
                    Text("• 配置服务器的IP地址和端口，然后保存")
                    Text("• 点击连接按钮连接到机器人，等待连接成功")
                    Text("• 手动模式下可以遥控机器狗，自动模式下机器人自主导航")
                    Text("• 选择不同模式控制机器人状态，站立模式才能移动")
                    Text("• 使用物理或者虚拟摇杆控制机器人移动，左边是线速度控制，右边是角速度控制")
                    Text("• 狂暴模式开启时前后速度提升到2m/s，关闭时为1m/s")
                    Text("• 点击视频按钮可全屏查看 RTSP 视频流")
                }
            }
        }
    }
}
