package com.helywin.leggedjoystick

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.helywin.leggedjoystick.input.remote.RemoteInputStatus
import com.helywin.leggedjoystick.service.RemoteControlForegroundService
import com.helywin.leggedjoystick.data.HighLowStance
import com.helywin.leggedjoystick.data.SpeedLevel
import com.helywin.leggedjoystick.ui.main.MainControlScreen
import com.helywin.leggedjoystick.ui.settings.SettingsScreen
import com.helywin.leggedjoystick.ui.theme.LeggedJoystickTheme
import legged_driver.AppMode
import legged_driver.SportMode
import timber.log.Timber

class MainActivity : ComponentActivity() {
    private lateinit var controller: Controller
    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationPermissionRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化WakeLock
        initializeWakeLock()

        // 初始化机器人控制器，传入Context
        controller = RobotControllerImpl(this)

        setContent {
            // 监听连接状态变化
            LaunchedEffect(settingsState.connectionState) {
                updateWakeLockState(settingsState.connectionState)
            }

            // 连接后启动前台服务，提高后台长连接稳定性。
            LaunchedEffect(
                settingsState.connectionState,
                settingsState.controlOwnershipState,
                settingsState.remoteInputState.status
            ) {
                updateForegroundServiceState(settingsState.connectionState)
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
                    LeggedJoystickApp(controller)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 应用恢复到前台时，根据设置更新屏幕常亮状态
        updateScreenOnFlag(settingsState.settings.keepScreenOn)
        controller.resumeMovementOutput()
    }

    override fun onPause() {
        super.onPause()
        controller.pauseMovementOutput()
        // 应用进入后台时，清除屏幕常亮标志
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        RemoteControlForegroundService.stop(this)
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
                requestNotificationPermissionIfNeeded()
                acquireWakeLock()
                Timber.i("[MainActivity] 已连接，启用屏幕保持唤醒")
            }
            else -> {
                releaseWakeLock()
                Timber.i("[MainActivity] 未连接，释放屏幕保持唤醒")
            }
        }
    }

    private fun updateForegroundServiceState(connectionState: ConnectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            requestNotificationPermissionIfNeeded()
            RemoteControlForegroundService.start(
                context = this,
                connectionText = buildForegroundConnectionText(),
                inputText = buildForegroundInputText()
            )
            return
        }

        RemoteControlForegroundService.stop(this)
    }

    private fun buildForegroundConnectionText(): String {
        return "连接 ${settingsState.connectionState.displayName}，控制权 ${settingsState.controlOwnershipState.displayName}"
    }

    private fun buildForegroundInputText(): String {
        val inputState = settingsState.remoteInputState
        val statusText = when (inputState.status) {
            RemoteInputStatus.STOPPED -> "已停止"
            RemoteInputStatus.STARTING -> "启动中"
            RemoteInputStatus.RUNNING -> "接收中"
            RemoteInputStatus.TIMEOUT -> "超时"
            RemoteInputStatus.ERROR -> "异常"
        }
        return "输入 ${inputState.sourceName.ifEmpty { "未连接" }} $statusText"
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        if (notificationPermissionRequested) return

        notificationPermissionRequested = true
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
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

    private companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 2001
    }
}

@Composable
fun LeggedJoystickApp(controller: Controller) {
    var showSettings by remember { mutableStateOf(false) }

    when {
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
                onSettingsClick = { showSettings = true }
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
            override fun takeControl() {}
            override fun releaseControl() {}
            override fun setMode(mode: AppMode) {}
            override fun setControlMode(controlMode: SportMode) {}
            override fun setSpeedLevel(level: SpeedLevel) {}
            override fun performAction(action: com.helywin.leggedjoystick.controller.RobotAction) {}
            override fun setFrontLight(on: Boolean) {}
            override fun setBackLight(on: Boolean) {}
            override fun setAutoModeLight(on: Boolean) {}
            override fun reverseHeadTail() {}
            override fun controlHead(leftRight: Float, upDown: Float) {}
            override fun setHighLowStance(stance: HighLowStance) {}
            override fun updateSettings(settings: AppSettings) {}
            override fun pauseMovementOutput() {}
            override fun resumeMovementOutput() {}
            override fun loadSettings() {}
            override fun saveSettings(settings: AppSettings) {}
            override fun isConnected() = false
            override fun cleanup() {}
        })
    }
}
