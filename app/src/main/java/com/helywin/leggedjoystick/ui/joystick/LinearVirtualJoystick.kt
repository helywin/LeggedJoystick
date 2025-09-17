package com.helywin.leggedjoystick.ui.joystick

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import timber.log.Timber

/**
 * 线性虚拟摇杆组件
 * 支持只在水平方向拖动，具有最大速度限制、自动回零和20Hz回调功能
 * 
 * @param modifier 修饰符
 * @param width 摇杆宽度
 * @param height 摇杆高度
 * @param maxVelocity 最大速度，范围 [0, 1]
 * @param knobSize 摇杆把手大小
 * @param backgroundColor 背景颜色
 * @param knobColor 摇杆把手颜色
 * @param borderColor 边框颜色
 * @param trackColor 轨道颜色
 * @param onValueChange 值变化回调，当摇杆离开零位时以20Hz频率调用
 * @param enhancedCallback 增强回调，包含释放事件
 */
@Composable
fun LinearVirtualJoystick(
    modifier: Modifier = Modifier,
    width: Dp = 300.dp,
    height: Dp = 80.dp,
    maxVelocity: Float = 1f,
    knobSize: Dp = 40.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surface.copy(0.4f),
    knobColor: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    onValueChange: JoystickCallback? = null,
    enhancedCallback: EnhancedJoystickCallback? = null
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var currentValue by remember { mutableStateOf(JoystickValue.ZERO) }
    var isDragging by remember { mutableStateOf(false) }
    
    val density = LocalDensity.current
    val knobSizePx = with(density) { knobSize.toPx() }
    val halfKnobSize = knobSizePx / 2f
    
    // 20Hz回调协程
    LaunchedEffect(currentValue, isDragging, onValueChange, enhancedCallback) {
        if ((onValueChange != null || enhancedCallback != null) && (!currentValue.isCenter || isDragging)) {
            while (isActive && (!currentValue.isCenter || isDragging)) {
                onValueChange?.onValueChanged(currentValue)
                enhancedCallback?.onValueChanged(currentValue)
                delay(50) // 20Hz = 1000ms / 20 = 50ms
            }
        }
    }
    
    Box(
        modifier = modifier
            .size(width, height)
            .background(backgroundColor)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        
                        // 按下时立即触发
                        isDragging = true
                        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                        val maxRange = (canvasSize.width / 2f) - halfKnobSize
                        
                        // 计算初始位置 (不应用maxVelocity缩放，保持[-1,1]范围)
                        val x = ((down.position.x - center.x) / maxRange).coerceIn(-1f, 1f)
                        currentValue = JoystickValue(x, 0f)
                        
                        // 立即触发按下回调
                        enhancedCallback?.onPressed()
                        Timber.d("LinearJoystick pressed: $currentValue")
                        
                        // 处理拖动
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            
                            if (change.pressed) {
                                // 更新拖动位置 (不应用maxVelocity缩放，保持[-1,1]范围)
                                val newX = ((change.position.x - center.x) / maxRange).coerceIn(-1f, 1f)
                                currentValue = JoystickValue(newX, 0f)
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                        
                        // 释放时触发
                        isDragging = false
                        currentValue = JoystickValue.ZERO
                        enhancedCallback?.onReleased()
                        Timber.d("LinearJoystick released: $currentValue")
                    }
                }
        ) {
            drawLinearJoystick(
                size = canvasSize,
                currentValue = currentValue,
                knobSize = knobSizePx,
                knobColor = knobColor,
                borderColor = borderColor,
                trackColor = trackColor
            )
        }
    }
}

/**
 * 绘制线性摇杆
 */
private fun DrawScope.drawLinearJoystick(
    size: IntSize,
    currentValue: JoystickValue,
    knobSize: Float,
    knobColor: Color,
    borderColor: Color,
    trackColor: Color
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val halfKnobSize = knobSize / 2f
    val maxRange = (size.width / 2f) - halfKnobSize
    
    // 绘制外边框
    drawRect(
        color = borderColor,
        topLeft = Offset.Zero,
        size = Size(size.width.toFloat(), size.height.toFloat()),
        style = Stroke(width = 2.dp.toPx())
    )

    // 绘制内部轨道
    val trackHeight = size.height * 0.15f
    val trackTop = center.y - trackHeight / 2f
    drawRoundRect(
        color = trackColor,
        topLeft = Offset(halfKnobSize, trackTop),
        size = Size(size.width - knobSize, trackHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f)
    )
    
    // 绘制中心标记
    val centerMarkSize = 4.dp.toPx()
    drawRect(
        color = borderColor,
        topLeft = Offset(
            center.x - centerMarkSize / 2f,
            center.y - size.height / 4f
        ),
        size = Size(centerMarkSize, size.height / 2f)
    )
    
    // 计算摇杆把手位置（只在X轴移动，Y轴保持中心）
    val knobX = center.x + (currentValue.x * maxRange)
    val knobPosition = Offset(knobX, center.y)
    
    // 绘制摇杆把手（方形）
    drawRect(
        color = knobColor,
        topLeft = Offset(
            knobPosition.x - halfKnobSize,
            knobPosition.y - halfKnobSize
        ),
        size = Size(knobSize, knobSize)
    )
    
    // 绘制摇杆把手边框
    drawRect(
        color = borderColor,
        topLeft = Offset(
            knobPosition.x - halfKnobSize,
            knobPosition.y - halfKnobSize
        ),
        size = Size(knobSize, knobSize),
        style = Stroke(width = 1.dp.toPx())
    )
}

@Preview(showBackground = true)
@Composable
fun LinearVirtualJoystickPreview() {
    LinearVirtualJoystick(
        modifier = Modifier.padding(16.dp),
        onValueChange = object : JoystickCallback {
            override fun onValueChanged(value: JoystickValue) {
                Timber.d("LinearJoystick Value: $value")
            }
        },
        enhancedCallback = object : EnhancedJoystickCallback {
            override fun onValueChanged(value: JoystickValue) {
                // 额外处理
            }

            override fun onReleased() {
                Timber.d("LinearJoystick Released")
            }
        }
    )
}