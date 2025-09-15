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
import com.helywin.leggedjoystick.controller.RobotController
import com.helywin.leggedjoystick.ui.main.MainControlScreen
import com.helywin.leggedjoystick.ui.settings.SettingsScreen
import com.helywin.leggedjoystick.ui.theme.LeggedJoystickTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {
    private val robotController = RobotController()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
            currentSettings = robotController.settingsState.settings,
            onSettingsChange = { newSettings ->
                robotController.updateSettings(newSettings)
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
        LeggedJoystickApp(RobotController())
    }
}