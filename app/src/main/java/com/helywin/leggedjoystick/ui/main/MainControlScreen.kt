/*********************************************************************************
 * FileName: MainControlScreen.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 主控制界面，根据UI设计图实现
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.ui.main

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.helywin.leggedjoystick.controller.Controller
import com.helywin.leggedjoystick.proto.displayName
import com.helywin.leggedjoystick.controller.RobotControllerImpl
import com.helywin.leggedjoystick.controller.settingsState
import com.helywin.leggedjoystick.data.ConnectionState
import com.helywin.leggedjoystick.input.GamepadInputHandler
import com.helywin.leggedjoystick.input.GamepadInputState
import legged_driver.ControlMode
import legged_driver.Mode
import com.helywin.leggedjoystick.ui.components.ConnectionDialog
import com.helywin.leggedjoystick.ui.components.GamepadStatusIndicator
import com.helywin.leggedjoystick.ui.joystick.*
import kotlin.random.Random

/**
 * 渐变点数据类
 */
data class GradientPoint(
    val color: Color,
    val center: Offset,
    val radius: Float,
    val alpha: Float
)

/**
 * 主控制界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainControlScreen(
    controller: Controller,
    gamepadInputState: GamepadInputState? = null,
    onSettingsClick: () -> Unit,
    onVideoClick: () -> Unit
) {
    val currentMode = settingsState.robotCtrlMode
    val controlMode = settingsState.robotMode
    val connectionState = settingsState.connectionState
    val batteryLevel = settingsState.batteryLevel
    val isRageModeEnabled = settingsState.settings.isRageModeEnabled
    val isRobotModeChanging = settingsState.isRobotCtrlModeChanging
    val isRobotCtrlModeChanging = settingsState.isRobotCtrlModeChanging
    val mainTitle = settingsState.settings.mainTitle
    val logoPath = settingsState.settings.logoPath

    // 连接状态对话框
    ConnectionDialog(
        connectionState = connectionState,
        onDismiss = {
            // 重置连接状态为断开
            if (connectionState != ConnectionState.CONNECTING) {
                controller.disconnect()
            }
        },
        onCancel = {
            controller.cancelConnection()
        }
    )

    MultiPointGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Logo 图片（仅在设置了路径时显示）
                if (logoPath.isNotEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(logoPath),
                        contentDescription = "Logo",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = mainTitle.ifEmpty { "天马智行机器狗遥控器" },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            // 顶部状态栏
            TopStatusBar(
                batteryLevel = batteryLevel,
                connectionState = connectionState,
                mode = controlMode,
                isRageModeEnabled = isRageModeEnabled,
                gamepadInputState = gamepadInputState,
                onVideoClick = onVideoClick,
                onConnectClick = {
                    when (connectionState) {
                        ConnectionState.CONNECTED -> {
                            controller.disconnect()
                        }

                        ConnectionState.CONNECTING -> {
                            controller.cancelConnection()
                        }

                        else -> {
                            controller.connect()
                        }
                    }
                },
                onModeClick = { mode ->
                    controller.setMode(mode)
                },
                onSettingsClick = onSettingsClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 模式选择按钮组
            ModeSelectionRow(
                currentCtrlMode = currentMode,
                isRobotModeChanging = isRobotModeChanging,
                isConnected = connectionState == ConnectionState.CONNECTED,
                onCtrlModeSelected = { mode ->
                    controller.setControlMode(mode)
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            // 主控制区域
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧摇杆区域 - 用于移动控制 (vx, vy)
                SquareVirtualJoystick(
                    size = 200.dp,
                    enhancedCallback = object : EnhancedJoystickCallback {
                        override fun onValueChanged(value: JoystickValue) {
                            controller.updateLeftJoystick(value)
                        }

                        override fun onPressed() {
                            controller.onLeftJoystickPressed()
                        }

                        override fun onReleased() {
                            controller.onLeftJoystickReleased()
                        }
                    }
                )

                // 中间狂暴模式按钮
                RageModeButton(
                    isEnabled = isRageModeEnabled,
                    onClick = {
                        controller.toggleRageMode()
                    }
                )

                // 右侧线性摇杆 - 用于转向控制 (yawRate)
                LinearVirtualJoystick(
                    width = 200.dp,
                    height = 60.dp,
                    enhancedCallback = object : EnhancedJoystickCallback {
                        override fun onValueChanged(value: JoystickValue) {
                            controller.updateRightJoystick(value)
                        }

                        override fun onPressed() {
                            controller.onRightJoystickPressed()
                        }

                        override fun onReleased() {
                            controller.onRightJoystickReleased()
                        }
                    }
                )
            }
        }
    }
}

/**
 * 顶部状态栏
 */
@Composable
private fun TopStatusBar(
    batteryLevel: Int,
    connectionState: ConnectionState,
    mode: Mode,
    isRageModeEnabled: Boolean,
    gamepadInputState: GamepadInputState?,
    onVideoClick: () -> Unit,
    onConnectClick: () -> Unit,
    onModeClick: (Mode) -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧状态显示
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 电量显示
            BatteryIndicator(batteryLevel = batteryLevel)

            // 游戏手柄状态显示
            if (gamepadInputState != null) {
                GamepadStatusIndicator(
                    isConnected = gamepadInputState.isGamepadConnected,
                    deviceName = gamepadInputState.connectedDevice?.name
                )
            }
        }

        // 右侧控制按钮
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 控制模式切换按钮
            ControlModeToggle(
                currentMode = mode,
                isConnected = connectionState == ConnectionState.CONNECTED,
                onModeClick = onModeClick
            )

            // 连接状态按钮
            Button(
                onClick = onConnectClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (connectionState) {
                        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.error
                        ConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                ),
                enabled = connectionState != ConnectionState.CONNECTING
            ) {
                Icon(
                    imageVector = when (connectionState) {
                        ConnectionState.CONNECTED -> Icons.Default.LinkOff
                        ConnectionState.CONNECTING -> Icons.Default.Sync
                        ConnectionState.CONNECTION_FAILED, ConnectionState.CONNECTION_TIMEOUT -> Icons.Default.Refresh
                        else -> Icons.Default.Link
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (connectionState) {
                        ConnectionState.CONNECTED -> "断开"
                        ConnectionState.CONNECTING -> "连接中..."
                        ConnectionState.CONNECTION_FAILED -> "重试"
                        ConnectionState.CONNECTION_TIMEOUT -> "重试"
                        else -> "连接"
                    }
                )
            }

            // 视频流按钮
            IconButton(onClick = onVideoClick) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "视频流")
            }

            // 设置按钮
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
        }
    }
}

/**
 * 电量指示器
 */
@Composable
private fun BatteryIndicator(batteryLevel: Int) {
    // 电池图标与百分比显示
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = when {
                batteryLevel > 90 -> Icons.Default.BatteryFull
                batteryLevel > 60 -> Icons.Default.Battery6Bar
                batteryLevel > 50 -> Icons.Default.Battery5Bar
                batteryLevel > 30 -> Icons.Default.Battery4Bar
                batteryLevel > 20 -> Icons.Default.Battery2Bar
                batteryLevel > 10 -> Icons.Default.Battery1Bar
                else -> Icons.Default.Battery0Bar
            },
            contentDescription = "电池电量",
            tint = when {
                batteryLevel > 50 -> Color(0xFF4CAF50)
                batteryLevel > 20 -> Color(0xFFFF9800)
                else -> Color(0xFFF44336)
            },
            modifier = Modifier.size(20.dp)
        )

        Text(
            text = "$batteryLevel%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = when {
                batteryLevel > 50 -> Color(0xFF4CAF50)
                batteryLevel > 20 -> Color(0xFFFF9800)
                else -> Color(0xFFF44336)
            }
        )
    }
}

/**
 * 控制模式切换按钮
 */
@Composable
private fun ControlModeToggle(
    currentMode: Mode,
    isConnected: Boolean,
    onModeClick: (Mode) -> Unit
) {
    Button(
        onClick = {
            val newMode = if (currentMode == Mode.MODE_MANUAL) {
                Mode.MODE_AUTO
            } else {
                Mode.MODE_MANUAL
            }
            onModeClick(newMode)
        },
        enabled = isConnected,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (currentMode == Mode.MODE_AUTO) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    ) {
        Icon(
            imageVector = if (currentMode == Mode.MODE_AUTO) Icons.Default.AutoMode else Icons.Default.ControlCamera,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = currentMode.displayName,
            fontSize = 12.sp
        )
    }
}

/**
 * 模式选择行·
 */
@Composable
private fun ModeSelectionRow(
    currentCtrlMode: ControlMode,
    isRobotModeChanging: Boolean,
    isConnected: Boolean,
    onCtrlModeSelected: (ControlMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ControlMode.entries
            .filter { it != ControlMode.CONTROL_MODE_UNSPECIFIED } // 过滤掉未指定模式
            .forEach { mode ->
                ModeButton(
                    mode = mode,
                    isSelected = currentCtrlMode == mode,
                    isEnabled = isConnected && !isRobotModeChanging,
                    isChanging = isRobotModeChanging && currentCtrlMode != mode,
                    onClick = { onCtrlModeSelected(mode) }
                )
            }
    }
}

/**
 * 模式按钮
 */
@Composable
private fun ModeButton(
    mode: ControlMode,
    isSelected: Boolean,
    isEnabled: Boolean,
    isChanging: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .selectable(
                selected = isSelected,
                onClick = { if (isEnabled) onClick() },
                enabled = isEnabled
            )
            .alpha(if (isEnabled) 1f else 0.6f)
            .padding(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primary
                isChanging -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isChanging) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Icon(
                    imageVector = when (mode) {
                        ControlMode.CONTROL_MODE_STAND_UP -> Icons.Default.KeyboardArrowUp
                        ControlMode.CONTROL_MODE_LIE_DOWN -> Icons.Default.KeyboardArrowDown
                        ControlMode.CONTROL_MODE_PASSIVE -> Icons.Default.PauseCircleOutline
                        else -> Icons.Default.HelpOutline
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        isChanging -> MaterialTheme.colorScheme.onTertiary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            Text(
                text = mode.displayName,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isChanging -> MaterialTheme.colorScheme.onTertiary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

/**
 * 狂暴模式按钮
 */
@Composable
private fun RageModeButton(
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .selectable(
                selected = isEnabled,
                onClick = onClick
            )
            .size(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                Color.Red.copy(alpha = 0.8f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isEnabled) 8.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isEnabled) Icons.Default.Whatshot else Icons.Default.LocalFireDepartment,
                contentDescription = "狂暴模式",
                modifier = Modifier.size(24.dp),
                tint = if (isEnabled) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = (if (isEnabled) "狂暴" else "普通") + "模式",
                fontSize = 10.sp,
                color = if (isEnabled) Color.White else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isEnabled) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 480)
@Composable
fun MainControlScreenPreview() {
    // 预览时使用假的Controller实现
    val dummyController = remember {
        object : Controller {
            override fun connect() {}
            override fun disconnect() {}
            override fun cancelConnection() {}
            override fun setMode(mode: Mode) {}
            override fun setControlMode(controlMode: ControlMode) {}
            override fun updateLeftJoystick(joystickValue: JoystickValue) {}
            override fun updateRightJoystick(joystickValue: JoystickValue) {}
            override fun onLeftJoystickReleased() {}
            override fun onRightJoystickReleased() {}
            override fun onLeftJoystickPressed() {}
            override fun onRightJoystickPressed() {}
            override fun toggleRageMode() {}
            override fun updateSettings(settings: com.helywin.leggedjoystick.data.AppSettings) {}
            override fun loadSettings() {}
            override fun saveSettings(settings: com.helywin.leggedjoystick.data.AppSettings) {}
            override fun isConnected() = false
            override fun cleanup() {}
        }
    }

    MainControlScreen(
        controller = dummyController,
        onSettingsClick = {},
        onVideoClick = {}
    )
}

/**
 * 创建多点渐变背景
 */
private fun createMultiPointGradientBrush(): Brush {
    return Brush.radialGradient(
        colors = listOf(
            Color(0xFF1E88E5).copy(alpha = 0.08f), // 蓝色点，透明度8%
            Color(0xFF43A047).copy(alpha = 0.06f), // 绿色点，透明度6%
            Color(0xFFE53935).copy(alpha = 0.04f), // 红色点，透明度4%
            Color(0xFFFB8C00).copy(alpha = 0.05f), // 橙色点，透明度5%
            Color(0xFF8E24AA).copy(alpha = 0.07f), // 紫色点，透明度7%
            Color.Transparent                       // 透明
        ),
        center = Offset(0.3f, 0.2f),
        radius = 800f
    )
}

/**
 * 生成随机渐变点
 */
private fun generateRandomGradientPoints(): List<GradientPoint> {
    val baseColors = listOf(
        Color(0xFF2196F3), // 蓝色
        Color(0xFF4CAF50), // 绿色
        Color(0xFF9C27B0), // 紫色
        Color(0xFFFF9800), // 橙色
        Color(0xFFE91E63), // 粉色
        Color(0xFF00BCD4), // 青色
        Color(0xFF607D8B), // 蓝灰色
        Color(0xFFFF5722), // 深橙色
        Color(0xFF795548), // 棕色
        Color(0xFF3F51B5)  // 靛蓝色
    )

    return (0..Random.nextInt(3, 7)).map { // 随机生成3-6个渐变点
        val color = baseColors.random()
        val centerX = Random.nextFloat() // 0.0 - 1.0
        val centerY = Random.nextFloat() // 0.0 - 1.0
        val radius = Random.nextFloat() * 400f + 300f // 300-700像素
        val alpha = Random.nextFloat() * 0.1f + 0.1f // 0.02-0.10透明度

        GradientPoint(
            color = color,
            center = Offset(centerX, centerY),
            radius = radius,
            alpha = alpha
        )
    }
}

/**
 * 创建多层叠加渐变背景（多个点效果）
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun MultiPointGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // 使用remember确保渐变点在重组时不会改变，只在初次创建时随机生成
    val gradientPoints = remember { generateRandomGradientPoints() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenWidth = with(density) { maxWidth.toPx() }
        val screenHeight = with(density) { maxHeight.toPx() }

        // 为每个渐变点创建一层背景
        gradientPoints.forEach { point ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                point.color.copy(alpha = point.alpha),
                                Color.Transparent
                            ),
                            center = Offset(
                                point.center.x * screenWidth,
                                point.center.y * screenHeight
                            ),
                            radius = point.radius
                        )
                    )
            )
        }

        content()
    }
}
