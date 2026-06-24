package com.helywin.leggedjoystick.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class SafetyContractTest {
    @Test
    fun lifecycleAndConnectionFailuresStopMovementOutput() {
        val projectRoot = locateProjectRoot()
        val mainActivity = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/MainActivity.kt"))
        val controller = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/controller/Controller.kt"))

        assertBlockContains(
            source = mainActivity,
            functionName = "onPause",
            requiredTokens = listOf("controller.pauseMovementOutput()")
        )
        assertBlockContains(
            source = mainActivity,
            functionName = "onDestroy",
            requiredTokens = listOf("controller.cleanup()")
        )
        assertBlockContains(
            source = controller,
            functionName = "pauseMovementOutput",
            requiredTokens = listOf("currentMovementIntent = MovementIntent.ZERO", "stopVelocityLoop()", "stopHeadControl()")
        )
        assertBlockContains(
            source = controller,
            functionName = "disconnect",
            requiredTokens = listOf("stopVelocityLoop()", "stopHeadControl()")
        )
        assertBlockContains(
            source = controller,
            functionName = "cleanup",
            requiredTokens = listOf("disconnect()", "stopRemoteInput()")
        )
    }

    @Test
    fun inputTimeoutAndControlLossClearMovementOutput() {
        val projectRoot = locateProjectRoot()
        val controller = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/controller/Controller.kt"))

        assertTrue(
            "输入超时或错误时必须清零当前运动意图",
            controller.contains("if (status == RemoteInputStatus.TIMEOUT || status == RemoteInputStatus.ERROR)") &&
                controller.contains("currentMovementIntent = MovementIntent.ZERO")
        )
        assertTrue(
            "输入状态为 TIMEOUT/ERROR/STOPPED 时必须把调试快照清零",
            controller.contains("RemoteInputStatus.TIMEOUT,") &&
                controller.contains("RemoteInputStatus.ERROR,") &&
                controller.contains("RemoteInputStatus.STOPPED -> RemoteInputSnapshot") &&
                controller.contains("movementIntent = MovementIntent.ZERO")
        )
        assertTrue(
            "收到控制权丢失消息时必须停止移动和头部输出",
            controller.contains("MessageType.MESSAGE_TYPE_CONTROL_LOST ->") &&
                controller.substringAfter("MessageType.MESSAGE_TYPE_CONTROL_LOST ->")
                    .substringBefore("MessageType.MESSAGE_TYPE_CONTROL_AVAILABLE ->")
                    .let { block ->
                        block.contains("stopVelocityLoop()") &&
                            block.contains("stopHeadControl()") &&
                            block.contains("ControlOwnershipState.LOST")
                    }
        )
        assertBlockContains(
            source = controller,
            functionName = "handleConnectionState",
            requiredTokens = listOf("stopVelocityLoop()", "ControlOwnershipState.UNKNOWN")
        )
    }

    @Test
    fun stopVelocityLoopSendsZeroWhenMovementWasActive() {
        val projectRoot = locateProjectRoot()
        val controller = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/controller/Controller.kt"))

        assertBlockContains(
            source = controller,
            functionName = "stopVelocityLoop",
            requiredTokens = listOf(
                "if (lastCommandSent)",
                "zmqClient.sendOperatorMoveCommand(0f, 0f, 0f)",
                "settingsState.updateLastCommand(\"移动\", \"停止\")",
                "currentMovementIntent = MovementIntent.ZERO",
                "lastCommandSent = false"
            )
        )
    }

    private fun assertBlockContains(source: String, functionName: String, requiredTokens: List<String>) {
        val functionMarker = if (source.contains("override fun $functionName")) {
            "override fun $functionName"
        } else {
            "fun $functionName"
        }
        val block = source.substringAfter(functionMarker)
            .substringBefore("\n    }\n")
        val missing = requiredTokens.filterNot { token -> block.contains(token) }
        assertTrue(
            "$functionName 缺少安全保护调用: $missing",
            missing.isEmpty()
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
