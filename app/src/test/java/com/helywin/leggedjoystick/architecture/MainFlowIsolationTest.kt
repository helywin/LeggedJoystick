package com.helywin.leggedjoystick.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class MainFlowIsolationTest {
    @Test
    fun mainFlowDoesNotReferenceLegacyInputComponents() {
        val projectRoot = locateProjectRoot()
        val mainFlowFiles = listOf(
            "app/src/main/java/com/helywin/leggedjoystick/MainActivity.kt",
            "app/src/main/java/com/helywin/leggedjoystick/controller/Controller.kt",
            "app/src/main/java/com/helywin/leggedjoystick/ui/main/MainControlScreen.kt",
            "app/src/main/java/com/helywin/leggedjoystick/ui/settings/SettingsScreen.kt"
        )
        val forbiddenTokens = listOf(
            "GamepadInputHandler",
            "GamepadDebugInfo",
            "LinearVirtualJoystick",
            "SquareVirtualJoystick",
            "JoystickValue",
            "com.helywin.leggedjoystick.input.Gamepad",
            "com.helywin.leggedjoystick.ui.joystick"
        )

        val violations = mainFlowFiles.flatMap { relativePath ->
            val content = Files.readString(projectRoot.resolve(relativePath))
            forbiddenTokens
                .filter { token -> content.contains(token) }
                .map { token -> "$relativePath -> $token" }
        }

        assertTrue(
            "旧游戏手柄和触屏虚拟摇杆组件只能保留源码，不能接入第一版主流程: $violations",
            violations.isEmpty()
        )
    }

    private fun locateProjectRoot(): Path {
        return generateSequence(Paths.get("").toAbsolutePath()) { current ->
            current.parent
        }.first { candidate ->
            Files.exists(candidate.resolve("settings.gradle.kts")) &&
                Files.exists(candidate.resolve("app/build.gradle.kts"))
        }
    }
}
