/*********************************************************************************
 * FileName: GamepadInputHandler.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-17
 * Description: 游戏手柄输入处理器，专门处理RETROID等掌机的物理摇杆输入
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.*
import com.helywin.leggedjoystick.ui.joystick.JoystickValue
import timber.log.Timber
import kotlin.math.abs

/**
 * 游戏手柄输入状态
 */
@Stable
class GamepadInputState {
    // 左摇杆状态
    var leftJoystick by mutableStateOf(JoystickValue.ZERO)
        private set
    
    // 右摇杆状态
    var rightJoystick by mutableStateOf(JoystickValue.ZERO)
        private set
    
    // 按键状态
    var buttonStates by mutableStateOf(mutableMapOf<Int, Boolean>())

    // 是否检测到游戏手柄
    var isGamepadConnected by mutableStateOf(false)
        private set
    
    // 当前连接的设备信息
    var connectedDevice by mutableStateOf<InputDevice?>(null)
        private set
    
    fun updateLeftJoystick(value: JoystickValue) {
        leftJoystick = value
    }
    
    fun updateRightJoystick(value: JoystickValue) {
        rightJoystick = value
    }
    
    fun updateButtonState(keyCode: Int, pressed: Boolean) {
        val newStates = buttonStates.toMutableMap()
        newStates[keyCode] = pressed
        buttonStates = newStates
    }
    
    fun updateGamepadConnection(connected: Boolean, device: InputDevice? = null) {
        isGamepadConnected = connected
        connectedDevice = device
    }
}

/**
 * 游戏手柄输入处理器
 */
class GamepadInputHandler {
    companion object {
        // 摇杆死区，小于此值的输入将被忽略
        private const val DEADZONE_THRESHOLD = 0.1f
        
        // 支持的游戏手柄轴
        private const val AXIS_LEFT_X = MotionEvent.AXIS_X
        private const val AXIS_LEFT_Y = MotionEvent.AXIS_Y
        private const val AXIS_RIGHT_X = MotionEvent.AXIS_Z
        private const val AXIS_RIGHT_Y = MotionEvent.AXIS_RZ
        
        // 备用右摇杆轴（某些设备可能使用不同的轴）
        private const val AXIS_RIGHT_X_ALT = MotionEvent.AXIS_RX
        private const val AXIS_RIGHT_Y_ALT = MotionEvent.AXIS_RY
    }
    
    // 输入状态
    val inputState = GamepadInputState()
    
    // 摇杆变化回调
    private var leftJoystickCallback: ((JoystickValue) -> Unit)? = null
    private var rightJoystickCallback: ((JoystickValue) -> Unit)? = null
    
    // 按键事件回调
    private var keyEventCallback: ((Int, Boolean) -> Unit)? = null
    
    /**
     * 设置左摇杆变化回调
     */
    fun setLeftJoystickCallback(callback: (JoystickValue) -> Unit) {
        leftJoystickCallback = callback
    }
    
    /**
     * 设置右摇杆变化回调
     */
    fun setRightJoystickCallback(callback: (JoystickValue) -> Unit) {
        rightJoystickCallback = callback
    }
    
    /**
     * 设置按键事件回调
     */
    fun setKeyEventCallback(callback: (Int, Boolean) -> Unit) {
        keyEventCallback = callback
    }
    
    /**
     * 处理运动事件（摇杆移动）
     * @param event 运动事件
     * @return 是否处理了该事件
     */
    fun handleMotionEvent(event: MotionEvent): Boolean {
        // 检查是否为游戏手柄输入
        if (!isGamepadEvent(event)) {
            return false
        }
        
        try {
            // 更新设备连接状态
            val device = event.device
            if (device != null && !inputState.isGamepadConnected) {
                inputState.updateGamepadConnection(true, device)
                Timber.i("[GamepadInput] 检测到游戏手柄: ${device.name}")
            }
            
            // 获取左摇杆值
            val leftX = getCenteredAxis(event, AXIS_LEFT_X)
            val leftY = getCenteredAxis(event, AXIS_LEFT_Y)
            val leftJoystick = JoystickValue(leftX, leftY)
            
            // 获取右摇杆值（尝试主要轴和备用轴）
            var rightX = getCenteredAxis(event, AXIS_RIGHT_X)
            var rightY = getCenteredAxis(event, AXIS_RIGHT_Y)
            
            // 如果主要轴没有值，尝试备用轴
            if (abs(rightX) < DEADZONE_THRESHOLD && abs(rightY) < DEADZONE_THRESHOLD) {
                rightX = getCenteredAxis(event, AXIS_RIGHT_X_ALT)
                rightY = getCenteredAxis(event, AXIS_RIGHT_Y_ALT)
            }
            
            val rightJoystick = JoystickValue(rightX, rightY)
            
            // 应用死区处理
            val processedLeftJoystick = applyDeadzone(leftJoystick)
            val processedRightJoystick = applyDeadzone(rightJoystick)
            
            // 更新状态
            inputState.updateLeftJoystick(processedLeftJoystick)
            inputState.updateRightJoystick(processedRightJoystick)
            
            // 触发回调
            leftJoystickCallback?.invoke(processedLeftJoystick)
            rightJoystickCallback?.invoke(processedRightJoystick)
            
            Timber.v("[GamepadInput] 摇杆更新 - 左: (${processedLeftJoystick.x}, ${processedLeftJoystick.y}), 右: (${processedRightJoystick.x}, ${processedRightJoystick.y})")
            
            return true
            
        } catch (e: Exception) {
            Timber.e(e, "[GamepadInput] 处理运动事件异常")
            return false
        }
    }
    
    /**
     * 处理按键事件
     * @param event 按键事件
     * @return 是否处理了该事件
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        // 检查是否为游戏手柄输入
        if (!isGamepadEvent(event)) {
            return false
        }
        
        try {
            val keyCode = event.keyCode
            val isPressed = event.action == KeyEvent.ACTION_DOWN
            val isReleased = event.action == KeyEvent.ACTION_UP
            
            if (isPressed || isReleased) {
                // 更新按键状态
                inputState.updateButtonState(keyCode, isPressed)
                
                // 触发回调
                keyEventCallback?.invoke(keyCode, isPressed)
                
                val action = if (isPressed) "按下" else "释放"
                Timber.d("[GamepadInput] 按键事件: ${getKeyName(keyCode)} $action")
                
                return true
            }
            
        } catch (e: Exception) {
            Timber.e(e, "[GamepadInput] 处理按键事件异常")
        }
        
        return false
    }
    
    /**
     * 检查事件是否来自游戏手柄
     */
    private fun isGamepadEvent(event: MotionEvent): Boolean {
        val source = event.source
        return source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }
    
    /**
     * 检查按键事件是否来自游戏手柄
     */
    private fun isGamepadEvent(event: KeyEvent): Boolean {
        val source = event.source
        return source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }
    
    /**
     * 获取居中的轴值（处理偏移）
     */
    private fun getCenteredAxis(event: MotionEvent, axis: Int): Float {
        val device = event.device ?: return 0f
        
        val range = device.getMotionRange(axis, event.source) ?: return 0f
        val flat = range.flat
        val value = event.getAxisValue(axis)
        
        // 应用死区
        return if (abs(value) > flat) {
            // 归一化到 [-1, 1] 范围
            val normalizedValue = (value - range.min) / (range.max - range.min) * 2f - 1f
            normalizedValue.coerceIn(-1f, 1f)
        } else {
            0f
        }
    }
    
    /**
     * 应用死区处理
     */
    private fun applyDeadzone(joystickValue: JoystickValue): JoystickValue {
        val magnitude = joystickValue.magnitude
        
        return if (magnitude < DEADZONE_THRESHOLD) {
            JoystickValue.ZERO
        } else {
            // 重新缩放以补偿死区
            val scaleFactor = (magnitude - DEADZONE_THRESHOLD) / (1f - DEADZONE_THRESHOLD)
            val normalizedX = if (abs(joystickValue.x) > DEADZONE_THRESHOLD) 
                joystickValue.x * scaleFactor / magnitude else 0f
            val normalizedY = if (abs(joystickValue.y) > DEADZONE_THRESHOLD) 
                joystickValue.y * scaleFactor / magnitude else 0f
            
            JoystickValue(normalizedX, normalizedY)
        }
    }
    
    /**
     * 获取按键名称（用于调试）
     */
    private fun getKeyName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> "A"
            KeyEvent.KEYCODE_BUTTON_B -> "B"
            KeyEvent.KEYCODE_BUTTON_X -> "X"
            KeyEvent.KEYCODE_BUTTON_Y -> "Y"
            KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
            KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
            KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
            KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
            KeyEvent.KEYCODE_BUTTON_SELECT -> "SELECT"
            KeyEvent.KEYCODE_BUTTON_START -> "START"
            KeyEvent.KEYCODE_BUTTON_THUMBL -> "左摇杆按下"
            KeyEvent.KEYCODE_BUTTON_THUMBR -> "右摇杆按下"
            KeyEvent.KEYCODE_DPAD_UP -> "方向键上"
            KeyEvent.KEYCODE_DPAD_DOWN -> "方向键下"
            KeyEvent.KEYCODE_DPAD_LEFT -> "方向键左"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "方向键右"
            else -> "未知($keyCode)"
        }
    }
    
    /**
     * 检测可用的游戏手柄设备
     */
    fun detectGamepadDevices(): List<InputDevice> {
        val gamepadDevices = mutableListOf<InputDevice>()
        
        try {
            val deviceIds = InputDevice.getDeviceIds()
            
            for (deviceId in deviceIds) {
                val device = InputDevice.getDevice(deviceId)
                if (device != null && isGamepadDevice(device)) {
                    gamepadDevices.add(device)
                    Timber.i("[GamepadInput] 发现游戏手柄设备: ${device.name}, ID: $deviceId")
                }
            }
            
            // 更新连接状态
            if (gamepadDevices.isNotEmpty() && !inputState.isGamepadConnected) {
                inputState.updateGamepadConnection(true, gamepadDevices.first())
            }
            
        } catch (e: Exception) {
            Timber.e(e, "[GamepadInput] 检测游戏手柄设备异常")
        }
        
        return gamepadDevices
    }
    
    /**
     * 检查设备是否为游戏手柄
     */
    private fun isGamepadDevice(device: InputDevice): Boolean {
        val sources = device.sources
        return (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }
    
    /**
     * 重置输入状态
     */
    fun reset() {
        inputState.updateLeftJoystick(JoystickValue.ZERO)
        inputState.updateRightJoystick(JoystickValue.ZERO)
        inputState.buttonStates = mutableMapOf()
        inputState.updateGamepadConnection(false)
        Timber.d("[GamepadInput] 输入状态已重置")
    }
}