package com.helywin.leggedjoystick

import android.os.Bundle
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
import com.helywin.leggedjoystick.ui.main.MainControlScreen
import com.helywin.leggedjoystick.ui.settings.SettingsScreen
import com.helywin.leggedjoystick.ui.theme.LeggedJoystickTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {
    private lateinit var controller: Controller
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化机器人控制器
        controller = RobotControllerImpl()
        
        enableEdgeToEdge()
        setContent {
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
    
    override fun onDestroy() {
        super.onDestroy()
        controller.cleanup()
    }
}

@Composable
fun LeggedJoystickApp(controller: Controller) {
    var showSettings by remember { mutableStateOf(false) }
    
    if (showSettings) {
        SettingsScreen(
            currentSettings = settingsState.settings,
            onSettingsChange = { newSettings ->
                controller.updateSettings(newSettings)
            },
            onBackClick = { showSettings = false }
        )
    } else {
        MainControlScreen(
            controller = controller,
            onSettingsClick = { showSettings = true }
        )
    }
}

// 横屏预览
@Preview(showBackground = true, widthDp = 800, heightDp = 480)
@Composable
fun LeggedJoystickAppPreview() {
    LeggedJoystickTheme {
        // 预览时使用假的Controller实现
        val dummyController = object : Controller {
            override fun connect() {}
            override fun disconnect() {}
            override fun cancelConnection() {}
            override fun setMode(mode: com.helywin.leggedjoystick.proto.Mode) {}
            override fun setControlMode(controlMode: com.helywin.leggedjoystick.proto.ControlMode) {}
            override fun updateLeftJoystick(joystickValue: com.helywin.leggedjoystick.ui.joystick.JoystickValue) {}
            override fun updateRightJoystick(joystickValue: com.helywin.leggedjoystick.ui.joystick.JoystickValue) {}
            override fun onLeftJoystickReleased() {}
            override fun onRightJoystickReleased() {}
            override fun toggleRageMode() {}
            override fun updateSettings(settings: AppSettings) {}
            override fun isConnected() = false
            override fun cleanup() {}
        }
        LeggedJoystickApp(dummyController)
    }
}