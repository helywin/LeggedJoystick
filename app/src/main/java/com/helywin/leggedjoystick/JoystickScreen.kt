package com.helywin.leggedjoystick

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.helywin.leggedjoystick.ui.joystick.*
import com.helywin.leggedjoystick.ui.theme.LeggedJoystickTheme
import timber.log.Timber

@Composable
fun JoystickScreen() {
    var squareJoystickValue by remember { mutableStateOf(JoystickValue.ZERO) }
    var linearJoystickValue by remember { mutableStateOf(JoystickValue.ZERO) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "虚拟摇杆演示",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        // 方形摇杆部分
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "方形摇杆",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                
                SquareVirtualJoystick(
                    size = 200.dp,
                    maxVelocity = 1f,
                    onValueChange = { value ->
                        squareJoystickValue = value
                        Timber.d("Square joystick: x=%.3f, y=%.3f, magnitude=%.3f", 
                            value.x, value.y, value.magnitude)
                    }
                )
                
                // 显示当前值
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "当前值:",
                        fontWeight = FontWeight.Medium
                    )
                    Text("X: ${String.format("%.3f", squareJoystickValue.x)}")
                    Text("Y: ${String.format("%.3f", squareJoystickValue.y)}")
                    Text("距离: ${String.format("%.3f", squareJoystickValue.magnitude)}")
                }
            }
        }
        
        // 线性摇杆部分
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "线性摇杆",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                
                LinearVirtualJoystick(
                    width = 300.dp,
                    height = 80.dp,
                    maxVelocity = 1f,
                    onValueChange = { value ->
                        linearJoystickValue = value
                        Timber.d("Linear joystick: x=%.3f", value.x)
                    }
                )
                
                // 显示当前值
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "当前值:",
                        fontWeight = FontWeight.Medium
                    )
                    Text("X: ${String.format("%.3f", linearJoystickValue.x)}")
                    Text("Y: ${String.format("%.3f", linearJoystickValue.y)}")
                }
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
                Text("• 方形摇杆：可在正方形区域内任意拖动")
                Text("• 线性摇杆：只能水平方向拖动")
                Text("• 松开后自动回到中心位置")
                Text("• 拖动时以20Hz频率调用回调函数")
                Text("• 输出值范围：[-1, 1]")
            }
        }
    }
}