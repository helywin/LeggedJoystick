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
            source = controller,
            functionName = "pauseMovementOutput",
            requiredTokens = listOf("currentMovementIntent = MovementIntent.ZERO", "stopVelocityLoop()", "stopHeadControl()")
        )
        assertBlockContains(
            source = controller,
            functionName = "disconnect",
            requiredTokens = listOf("stopVelocityLoop()", "stopHeadControl()", "stopRemoteInput()")
        )
        assertBlockContains(
            source = controller,
            functionName = "cleanup",
            requiredTokens = listOf("disconnect()", "stopRemoteInput()")
        )
        assertBlockContains(
            source = controller,
            functionName = "resumeMovementOutput",
            requiredTokens = listOf("if (settingsState.isConnected)", "startRemoteInput()", "startVelocityLoop()")
        )
    }

    @Test
    fun activityDoesNotOwnControllerLifecycle() {
        val projectRoot = locateProjectRoot()
        val mainActivity = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/MainActivity.kt"))
        val controller = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/controller/Controller.kt"))

        assertTrue(
            "控制器必须是进程级 object",
            controller.contains("object RobotControllerImpl : Controller") &&
                controller.contains("object ControllerRuntime") &&
                controller.contains("get() = ControllerRuntime.settingsState")
        )
        assertTrue(
            "Activity 必须复用进程级控制器，不能重新构造控制器实例",
            mainActivity.contains("private val controller: Controller = RobotControllerImpl") &&
                mainActivity.contains("RobotControllerImpl.initialize(applicationContext)") &&
                !mainActivity.contains("RobotControllerImpl(this)")
        )
        assertBlockDoesNotContain(
            source = mainActivity,
            functionName = "onDestroy",
            forbiddenTokens = listOf("controller.cleanup()", "RemoteControlForegroundService.stop(this)")
        )
    }

    @Test
    fun connectedStateAutoMarksControlOwned() {
        val projectRoot = locateProjectRoot()
        val controller = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/controller/Controller.kt"))

        assertBlockContains(
            source = controller,
            functionName = "handleConnectionState",
            requiredTokens = listOf(
                "markControlOwned(\"driver 已自动接管\")",
                "startRemoteInput()"
            )
        )
        assertBlockContains(
            source = controller,
            functionName = "markControlOwned",
            requiredTokens = listOf("sendInitialCommandsAfterTake()")
        )
    }

    @Test
    fun mainScreenDoesNotExposeManualTakeover() {
        val projectRoot = locateProjectRoot()
        val mainScreen = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/ui/main/MainControlScreen.kt"))
        val settingsScreen = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/ui/settings/SettingsScreen.kt"))

        assertTrue(
            "driver 自动接管后，主控 UI 不得再提供手动接管或释放入口",
            !mainScreen.contains("ControlOwnershipButton") &&
                !mainScreen.contains("controller.takeControl()") &&
                !mainScreen.contains("controller.releaseControl()") &&
                !settingsScreen.contains("接管控制权后")
        )
    }

    @Test
    fun rtspVideoReattachesAfterResume() {
        val projectRoot = locateProjectRoot()
        val video = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/ui/video/RtspVideoSurface.kt"))
        val mainScreen = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/ui/main/MainControlScreen.kt"))

        assertTrue(
            "RTSP 视频恢复前台时必须复用进程级 IJKPlayer 运行时并重新 attach TextureView，不能回到 VLC SurfaceView 输出路径",
                video.contains("Lifecycle.Event.ON_RESUME ->") &&
                video.contains("resumeGeneration++") &&
                video.contains("object RtspVideoRuntime") &&
                video.contains("IjkMediaPlayer") &&
                video.contains("TextureView") &&
                video.contains("setSurface") &&
                video.contains("MEDIA_INFO_VIDEO_RENDERING_START") &&
                video.contains("registerDefaultNetworkCallback") &&
                video.contains("ACTION_POWER_DISCONNECTED") &&
                video.contains("ACTION_USB_STATE") &&
                video.contains("networkGeneration") &&
                video.contains("retryGeneration") &&
                video.contains("RTSP_START_TIMEOUT_MS") &&
                video.contains("RTSP_FRAME_STALL_TIMEOUT_MS") &&
                video.contains("scheduleVideoRetry") &&
                video.contains("onSurfaceTextureUpdated") &&
                video.contains("VideoReconnectIndicator") &&
                video.contains("captureRtspSurfaceSnapshot") &&
                video.contains("textureView.getBitmap") &&
                video.contains("RtspVideoRuntime.player(context, slot)") &&
                video.contains("player.attach(textureView, scaleMode)") &&
                video.contains("player.playUrl(rtspUrl, forceReload = networkChanged || retryRequested)") &&
                !video.contains("org.videolan") &&
                !video.contains("VLCVideoLayout") &&
                !video.contains("SurfaceView") &&
                !video.contains("key(retryTrigger)") &&
                !video.contains("-vv") &&
                mainScreen.contains("slot = RtspVideoSlot.Main") &&
                mainScreen.contains("slot = RtspVideoSlot.Secondary") &&
                mainScreen.split("showReconnectIndicator = true").size >= 3
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
            requiredTokens = listOf("stopVelocityLoop()", "stopRemoteInput()", "ControlOwnershipState.UNKNOWN")
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

    private fun assertBlockDoesNotContain(source: String, functionName: String, forbiddenTokens: List<String>) {
        val functionMarker = if (source.contains("override fun $functionName")) {
            "override fun $functionName"
        } else {
            "fun $functionName"
        }
        val block = source.substringAfter(functionMarker)
            .substringBefore("\n    }\n")
        val found = forbiddenTokens.filter { token -> block.contains(token) }
        assertTrue(
            "$functionName 不应包含 Activity 生命周期绑定清理: $found",
            found.isEmpty()
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
