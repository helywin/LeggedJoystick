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
import com.helywin.leggedjoystick.ui.main.MainControlScreen
import com.helywin.leggedjoystick.ui.settings.SettingsScreen
import com.helywin.leggedjoystick.ui.theme.LeggedJoystickTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {
    private lateinit var robotController: RobotController
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化机器人控制器，传入Context
        robotController = RobotController(this)
        
        enableEdgeToEdge()
        setContent {
            LeggedJoystickTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LeggedJoystickApp(robotController)
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        robotController.cleanup()
    }
}

@Composable
fun LeggedJoystickApp(robotController: RobotController) {
    var showSettings by remember { mutableStateOf(false) }
    
    if (showSettings) {
        SettingsScreen(
            currentSettings = robotController.getCurrentSettings(),
            onSettingsChange = { newSettings ->
                robotController.saveAppSettings(newSettings)
            },
            onBackClick = { showSettings = false }
        )
    } else {
        MainControlScreen(
            robotController = robotController,
            onSettingsClick = { showSettings = true }
        )
    }
}

// 横屏
@Preview(showBackground = true, widthDp = 800, heightDp = 480)
@Composable
fun LeggedJoystickAppPreview() {
    LeggedJoystickTheme {
        // 在预览中使用假的RobotController，避免需要Context
        // LeggedJoystickApp(RobotController())
    }
}