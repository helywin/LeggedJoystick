/*********************************************************************************
 * FileName: GamepadDebugInfo.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-17
 * Description: 游戏手柄调试信息显示组件
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.VideogameAssetOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helywin.leggedjoystick.input.GamepadInputState
import com.helywin.leggedjoystick.ui.joystick.JoystickValue
import kotlin.math.abs

/**
 * 游戏手柄调试信息组件
 * @param gamepadState 游戏手柄输入状态
 * @param modifier 修饰符
 */
@Composable
fun GamepadDebugInfo(
    gamepadState: GamepadInputState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(8.dp)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标题和连接状态
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (gamepadState.isGamepadConnected) {
                        Icons.Default.VideogameAsset
                    } else {
                        Icons.Default.VideogameAssetOff
                    },
                    contentDescription = if (gamepadState.isGamepadConnected) "游戏手柄已连接" else "游戏手柄未连接",
                    tint = if (gamepadState.isGamepadConnected) {
                        Color(0xFF4CAF50)
                    } else {
                        Color(0xFFFF5722)
                    }
                )
                
                Text(
                    text = "游戏手柄状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Surface(
                    color = if (gamepadState.isGamepadConnected) {
                        Color(0xFF4CAF50)
                    } else {
                        Color(0xFFFF5722)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = if (gamepadState.isGamepadConnected) "已连接" else "未连接",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            // 设备信息
            if (gamepadState.isGamepadConnected && gamepadState.connectedDevice != null) {
                Text(
                    text = "设备: ${gamepadState.connectedDevice?.name ?: "未知设备"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 摇杆状态显示
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 左摇杆
                JoystickStatusCard(
                    title = "左摇杆",
                    joystickValue = gamepadState.leftJoystick,
                    modifier = Modifier.weight(1f)
                )
                
                // 右摇杆
                JoystickStatusCard(
                    title = "右摇杆",
                    joystickValue = gamepadState.rightJoystick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 摇杆状态卡片
 * @param title 标题
 * @param joystickValue 摇杆值
 * @param modifier 修饰符
 */
@Composable
private fun JoystickStatusCard(
    title: String,
    joystickValue: JoystickValue,
    modifier: Modifier = Modifier
) {
    val isActive = !joystickValue.isCenter
    val borderColor = if (isActive) Color(0xFF2196F3) else Color(0xFFE0E0E0)
    val backgroundColor = if (isActive) Color(0xFFF3F8FF) else Color(0xFFFAFAFA)
    
    Surface(
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) Color(0xFF2196F3) else MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "X: ${String.format("%.3f", joystickValue.x)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            
            Text(
                text = "Y: ${String.format("%.3f", joystickValue.y)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            
            Text(
                text = "幅度: ${String.format("%.3f", joystickValue.magnitude)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 简化的游戏手柄状态指示器
 * @param isConnected 是否连接
 * @param deviceName 设备名称
 * @param modifier 修饰符
 */
@Composable
fun GamepadStatusIndicator(
    modifier: Modifier = Modifier,
    isConnected: Boolean,
    deviceName: String? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (isConnected) Icons.Default.VideogameAsset else Icons.Default.VideogameAssetOff,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF5722)
        )
        
        Text(
            text = deviceName ?:
                   if (isConnected) "手柄已连接" else "手柄未连接",
            style = MaterialTheme.typography.labelSmall,
            color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF5722)
        )
    }
}