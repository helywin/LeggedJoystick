package com.helywin.leggedjoystick.ui.joystick

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 摇杆数值数据类
 * @param x X轴坐标，范围 [-1, 1]
 * @param y Y轴坐标，范围 [-1, 1]
 */
data class JoystickValue(
    val x: Float = 0f,
    val y: Float = 0f
) {
    /**
     * 获取摇杆的距离（从中心点到当前位置的距离）
     */
    val magnitude: Float
        get() = sqrt(x * x + y * y).coerceAtMost(1f)
    
    /**
     * 判断摇杆是否在中心位置
     */
    val isCenter: Boolean
        get() = abs(x) < 0.01f && abs(y) < 0.01f
    
    companion object {
        val ZERO = JoystickValue(0f, 0f)
    }
}

/**
 * 摇杆值变化回调接口
 */
fun interface JoystickCallback {
    /**
     * 当摇杆值发生变化时调用
     * @param value 当前摇杆值
     */
    fun onValueChanged(value: JoystickValue)
}

/**
 * 增强的摇杆回调接口，包含按下和释放事件
 */
interface EnhancedJoystickCallback {
    /**
     * 当摇杆值发生变化时调用
     * @param value 当前摇杆值
     */
    fun onValueChanged(value: JoystickValue)
    
    /**
     * 当摇杆被按下时调用
     */
    fun onPressed() {}
    
    /**
     * 当摇杆释放时调用
     */
    fun onReleased() {}
}

/**
 * 将Offset转换为JoystickValue
 * @param center 中心点坐标
 * @param maxRadius 最大半径
 * @return 归一化的摇杆值
 */
fun Offset.toJoystickValue(center: Offset, maxRadius: Float): JoystickValue {
    val deltaX = (x - center.x) / maxRadius
    val deltaY = (y - center.y) / maxRadius
    return JoystickValue(deltaX.coerceIn(-1f, 1f), deltaY.coerceIn(-1f, 1f))
}

/**
 * 将JoystickValue转换为Offset
 * @param center 中心点坐标
 * @param maxRadius 最大半径
 * @return 实际坐标偏移
 */
fun JoystickValue.toOffset(center: Offset, maxRadius: Float): Offset {
    return Offset(
        center.x + x * maxRadius,
        center.y + y * maxRadius
    )
}