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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helywin.leggedjoystick.controller.RobotController
import com.helywin.leggedjoystick.data.RobotMode
import com.helywin.leggedjoystick.ui.joystick.*
import timber.log.Timber

/**
 * 主控制界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainControlScreen(
    robotController: RobotController,
    onSettingsClick: () -> Unit
) {
    val settingsState = robotController.settingsState
    val currentMode = settingsState.robotMode
    val isConnected = settingsState.isConnected
    val batteryLevel = settingsState.batteryLevel
    val isRageModeEnabled = settingsState.settings.isRageModeEnabled
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部状态栏
        TopStatusBar(
            batteryLevel = batteryLevel,
            isConnected = isConnected,
            isRageModeEnabled = isRageModeEnabled,
            onConnectClick = {
                if (isConnected) {
                    robotController.disconnect()
                } else {
                    robotController.connect(settingsState.settings)
                }
            },
            onSettingsClick = onSettingsClick
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 模式选择按钮组
        ModeSelectionRow(
            currentMode = currentMode,
            onModeSelected = { mode ->
                robotController.setRobotMode(mode)
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 主控制区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧摇杆区域
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                SquareVirtualJoystick(
                    size = 200.dp,
                    maxVelocity = if (isRageModeEnabled) 2f else 1f,
                    enhancedCallback = object : EnhancedJoystickCallback {
                        override fun onValueChanged(value: JoystickValue) {
                            robotController.moveRobot(value)
                        }
                        
                        override fun onReleased() {
                            robotController.onJoystickReleased()
                        }
                    }
                )
            }
            
            // 中间狂暴模式按钮
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(0.3f)
            ) {
                RageModeButton(
                    isEnabled = isRageModeEnabled,
                    onClick = { robotController.toggleRageMode() }
                )
            }
            
            // 右侧线性摇杆（可选，根据需要调整）
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                LinearVirtualJoystick(
                    width = 150.dp,
                    height = 60.dp,
                    maxVelocity = if (isRageModeEnabled) 2f else 1f,
                    enhancedCallback = object : EnhancedJoystickCallback {
                        override fun onValueChanged(value: JoystickValue) {
                            // 可以用于转向控制
                            // robotController.moveRobot(JoystickValue(0f, value.x))
                        }
                        
                        override fun onReleased() {
                            // robotController.onJoystickReleased()
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
    isConnected: Boolean,
    isRageModeEnabled: Boolean,
    onConnectClick: () -> Unit,
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
            // 连接状态按钮
            Button(
                onClick = onConnectClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isConnected) "断开" else "连接")
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$batteryLevel%",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        
        // 简单的电量条
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(20.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
        ) {
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
            )
        }
    }
}

/**
 * 模式选择行
 */
@Composable
private fun ModeSelectionRow(
    currentMode: RobotMode,
    onModeSelected: (RobotMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        RobotMode.values().forEach { mode ->
            ModeButton(
                mode = mode,
                isSelected = currentMode == mode,
                onClick = { onModeSelected(mode) }
            )
        }
    }
}

/**
 * 模式按钮
 */
@Composable
private fun ModeButton(
    mode: RobotMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .selectable(
                selected = isSelected,
                onClick = onClick
            )
            .padding(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        )
    ) {
        Text(
            text = mode.displayName,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (isSelected) 
                MaterialTheme.colorScheme.onPrimary 
            else 
                MaterialTheme.colorScheme.onSurface
        )
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