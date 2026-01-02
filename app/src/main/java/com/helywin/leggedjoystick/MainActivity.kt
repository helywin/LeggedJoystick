package com.helywin.leggedjoystick

import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.helywin.leggedjoystick.controller.Controller
import com.helywin.leggedjoystick.controller.RobotControllerImpl
import com.helywin.leggedjoystick.controller.settingsState
import com.helywin.leggedjoystick.data.AppSettings
import com.helywin.leggedjoystick.data.ConnectionState
import com.helywin.leggedjoystick.input.GamepadInputHandler
import com.helywin.leggedjoystick.ui.main.MainControlScreen
import com.helywin.leggedjoystick.ui.settings.SettingsScreen
import com.helywin.leggedjoystick.ui.theme.LeggedJoystickTheme
import com.helywin.leggedjoystick.ui.video.VideoStreamScreen
import timber.log.Timber

class MainActivity : ComponentActivity() {
    private lateinit var controller: Controller
    private lateinit var gamepadInputHandler: GamepadInputHandler
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化WakeLock
        initializeWakeLock()

        // 初始化机器人控制器，传入Context
        controller = RobotControllerImpl(this)

        // 初始化游戏手柄输入处理器
        gamepadInputHandler = GamepadInputHandler()

        // 设置游戏手柄输入回调
        setupGamepadCallbacks()

        // 检测可用的游戏手柄设备
        gamepadInputHandler.detectGamepadDevices()

        enableEdgeToEdge()
        setContent {
            // 监听连接状态变化
            LaunchedEffect(settingsState.connectionState) {
                updateWakeLockState(settingsState.connectionState)
            }

            // 监听屏幕常亮设置变化
            LaunchedEffect(settingsState.settings.keepScreenOn) {
                updateScreenOnFlag(settingsState.settings.keepScreenOn)
            }

            LeggedJoystickTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LeggedJoystickApp(controller, gamepadInputHandler)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 应用恢复到前台时，根据设置更新屏幕常亮状态
        updateScreenOnFlag(settingsState.settings.keepScreenOn)
    }

    override fun onPause() {
        super.onPause()
        // 应用进入后台时，清除屏幕常亮标志
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        gamepadInputHandler.reset()
        controller.cleanup()
    }

    /**
     * 初始化WakeLock
     */
    private fun initializeWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "LeggedJoystick::ScreenWakeLock"
        )
    }

    /**
     * 根据连接状态更新WakeLock状态
     */
    private fun updateWakeLockState(connectionState: ConnectionState) {
        when (connectionState) {
            ConnectionState.CONNECTED -> {
                acquireWakeLock()
                Timber.i("[MainActivity] 已连接，启用屏幕保持唤醒")
            }
            else -> {
                releaseWakeLock()
                Timber.i("[MainActivity] 未连接，释放屏幕保持唤醒")
            }
        }
    }

    /**
     * 获取WakeLock，保持屏幕唤醒
     */
    private fun acquireWakeLock() {
        try {
            wakeLock?.let { lock ->
                if (!lock.isHeld) {
                    lock.acquire()
                    Timber.d("[MainActivity] WakeLock已获取")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[MainActivity] 获取WakeLock失败")
        }
    }

    /**
     * 释放WakeLock
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                    Timber.d("[MainActivity] WakeLock已释放")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[MainActivity] 释放WakeLock失败")
        }
    }

    /**
     * 根据设置更新屏幕常亮标志
     */
    private fun updateScreenOnFlag(keepScreenOn: Boolean) {
        try {
            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                Timber.d("[MainActivity] 已启用屏幕常亮")
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                Timber.d("[MainActivity] 已禁用屏幕常亮")
            }
        } catch (e: Exception) {
            Timber.e(e, "[MainActivity] 更新屏幕常亮标志失败")
        }
    }

    /**
     * 处理运动事件（游戏手柄摇杆输入）
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return gamepadInputHandler.handleMotionEvent(event) || super.onGenericMotionEvent(event)
    }

    /**
     * 处理按键事件（游戏手柄按钮输入）
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return gamepadInputHandler.handleKeyEvent(event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return gamepadInputHandler.handleKeyEvent(event) || super.onKeyUp(keyCode, event)
    }

    /**
     * 设置游戏手柄输入回调
     */
    private fun setupGamepadCallbacks() {
        // 左摇杆回调
        gamepadInputHandler.setLeftJoystickCallback { joystickValue ->
            controller.updateLeftJoystick(joystickValue)
            Timber.v("[MainActivity] 物理左摇杆更新: x=${joystickValue.x}, y=${joystickValue.y}")
        }

        // 右摇杆回调
        gamepadInputHandler.setRightJoystickCallback { joystickValue ->
            controller.updateRightJoystick(joystickValue)
            Timber.v("[MainActivity] 物理右摇杆更新: x=${joystickValue.x}, y=${joystickValue.y}")
        }

        // 按键事件回调
        gamepadInputHandler.setKeyEventCallback { keyCode, isPressed ->
            handleGamepadButtonEvent(keyCode, isPressed)
        }
    }

    /**
     * 处理游戏手柄按钮事件
     */
    private fun handleGamepadButtonEvent(keyCode: Int, isPressed: Boolean) {
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                // 左摇杆按下
                if (isPressed) {
                    controller.onLeftJoystickPressed()
                } else {
                    controller.onLeftJoystickReleased()
                }
            }
            KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                // 右摇杆按下
                if (isPressed) {
                    controller.onRightJoystickPressed()
                } else {
                    controller.onRightJoystickReleased()
                }
            }
            KeyEvent.KEYCODE_BUTTON_A -> {
                // A按钮 - 可以设置为连接/断开连接
                if (isPressed) {
                    if (controller.isConnected()) {
                        controller.disconnect()
                    } else {
                        controller.connect()
                    }
                }
            }
            KeyEvent.KEYCODE_BUTTON_B -> {
                // B按钮 - 可以设置为切换狂暴模式
                if (isPressed) {
                    controller.toggleRageMode()
                }
            }
            // 可以根据需要添加更多按钮映射
        }

        val action = if (isPressed) "按下" else "释放"
        Timber.d("[MainActivity] 游戏手柄按钮事件: ${getButtonName(keyCode)} $action")
    }

    /**
     * 获取按钮名称（用于日志）
     */
    private fun getButtonName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> "A"
            KeyEvent.KEYCODE_BUTTON_B -> "B"
            KeyEvent.KEYCODE_BUTTON_X -> "X"
            KeyEvent.KEYCODE_BUTTON_Y -> "Y"
            KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
            KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
            KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
            KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
            KeyEvent.KEYCODE_BUTTON_THUMBL -> "左摇杆按下"
            KeyEvent.KEYCODE_BUTTON_THUMBR -> "右摇杆按下"
            else -> "未知($keyCode)"
        }
    }
}

@Composable
fun LeggedJoystickApp(controller: Controller, gamepadInputHandler: GamepadInputHandler) {
    var showSettings by remember { mutableStateOf(false) }
    var showVideoStream by remember { mutableStateOf(false) }

    when {
        showVideoStream -> {
            VideoStreamScreen(
                rtspUrl = settingsState.settings.rtspUrl,
                onBackClick = { showVideoStream = false }
            )
        }
        showSettings -> {
            SettingsScreen(
                currentSettings = settingsState.settings,
                onSettingsChange = { newSettings ->
                    controller.updateSettings(newSettings)
                },
                onBackClick = { showSettings = false }
            )
        }
        else -> {
            MainControlScreen(
                controller = controller,
                gamepadInputState = gamepadInputHandler.inputState,
                onSettingsClick = { showSettings = true },
                onVideoClick = { showVideoStream = true }
            )
        }
    }
}

// 横屏预览
@Preview(showBackground = true, widthDp = 800, heightDp = 480)
@Composable
fun LeggedJoystickAppPreview() {
    LeggedJoystickTheme {
        LeggedJoystickApp(object : Controller {
            override fun connect() {}
            override fun disconnect() {}
            override fun cancelConnection() {}
            override fun setMode(mode: legged_driver.Mode) {}
            override fun setControlMode(controlMode: legged_driver.ControlMode) {}
            override fun updateLeftJoystick(joystickValue: com.helywin.leggedjoystick.ui.joystick.JoystickValue) {}
            override fun updateRightJoystick(joystickValue: com.helywin.leggedjoystick.ui.joystick.JoystickValue) {}
            override fun onLeftJoystickReleased() {}
            override fun onRightJoystickReleased() {}
            override fun onLeftJoystickPressed() {}
            override fun onRightJoystickPressed() {}
            override fun toggleRageMode() {}
            override fun updateSettings(settings: AppSettings) {}
            override fun loadSettings() {}
            override fun saveSettings(settings: AppSettings) {}
            override fun isConnected() = false
            override fun cleanup() {}
        }, GamepadInputHandler())
    }
}
