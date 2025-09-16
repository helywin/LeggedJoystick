/*********************************************************************************
 * FileName: MainControlScreen.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 主控制界面，根据UI设计图实现
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helywin.leggedjoystick.controller.Controller
import com.helywin.leggedjoystick.controller.RobotControllerImpl
import com.helywin.leggedjoystick.controller.settingsState
import com.helywin.leggedjoystick.data.ConnectionState
import com.helywin.leggedjoystick.proto.ControlMode
import com.helywin.leggedjoystick.proto.Mode
import com.helywin.leggedjoystick.ui.components.ConnectionDialog
import com.helywin.leggedjoystick.ui.joystick.*

/**
 * 主控制界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainControlScreen(
    controller: Controller,
    onSettingsClick: () -> Unit
) {
    val currentMode = settingsState.robotCtrlMode
    val controlMode = settingsState.robotMode
    val connectionState = settingsState.connectionState
    val batteryLevel = settingsState.batteryLevel
    val isRageModeEnabled = settingsState.settings.isRageModeEnabled
    val isRobotModeChanging = settingsState.isRobotCtrlModeChanging
    val isRobotCtrlModeChanging = settingsState.isRobotCtrlModeChanging

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部状态栏
        TopStatusBar(
            batteryLevel = batteryLevel,
            connectionState = connectionState,
            mode = controlMode,
            isRageModeEnabled = isRageModeEnabled,
            onConnectClick = {
                if (connectionState == ConnectionState.CONNECTED) {
                    controller.disconnect()
                } else if (connectionState == ConnectionState.CONNECTING) {
                    controller.cancelConnection()
                } else {
                    controller.connect()
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
                maxVelocity = if (isRageModeEnabled) 2f else 1f,
                enhancedCallback = object : EnhancedJoystickCallback {
                    override fun onValueChanged(value: JoystickValue) {
                        controller.updateLeftJoystick(value)
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
                maxVelocity = if (isRageModeEnabled) 2f else 1f,
                enhancedCallback = object : EnhancedJoystickCallback {
                    override fun onValueChanged(value: JoystickValue) {
                        controller.updateRightJoystick(value)
                    }

                    override fun onReleased() {
                        controller.onRightJoystickReleased()
                    }
                }
            )
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
    onConnectClick: () -> Unit,
    onModeClick: (Mode) -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧电量显示
        BatteryIndicator(batteryLevel = batteryLevel)

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
    Box(
        modifier = Modifier
            .width(80.dp)
            .height(30.dp)
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        // 电量背景
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(batteryLevel / 100f)
                .background(
                    when {
                        batteryLevel > 50 -> Color.Green
                        batteryLevel > 20 -> Color.Yellow
                        else -> Color.Red
                    }
                )
                .align(Alignment.CenterStart)
        )
        
        // 百分比文本（白色）
        Text(
            text = "$batteryLevel%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
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
        ControlMode.entries.forEach { mode ->
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
                onClick = { if (isEnabled) onClick() }
            )
            .padding(4.dp)
            .alpha(if (isEnabled) 1f else 0.6f),
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
            Text(
                text = "狂暴",
                fontSize = 12.sp,
                color = if (isEnabled) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "模式",
                fontSize = 12.sp,
                color = if (isEnabled) Color.White else MaterialTheme.colorScheme.onSurface
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
            override fun toggleRageMode() {}
            override fun updateSettings(settings: com.helywin.leggedjoystick.data.AppSettings) {}
            override fun isConnected() = false
            override fun cleanup() {}
        }
    }
    
    MainControlScreen(
        controller = dummyController,
        onSettingsClick = {}
    )
}