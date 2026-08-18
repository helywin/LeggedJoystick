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
        assertBlockContains(
            source = controller,
            functionName = "sendInitialCommandsAfterTake",
            requiredTokens = listOf(
                "settingsState.setSpeedLevel(SpeedLevel.SLOW)",
                "zmqClient.setSpeedLevel(SpeedLevel.SLOW.protocolSpeedLevel)"
            )
        )
    }

    @Test
    fun appLaunchAutoConnectsOnce() {
        val projectRoot = locateProjectRoot()
        val mainActivity = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/MainActivity.kt"))

        assertTrue(
            "App 首次打开必须自动触发一次连接",
            mainActivity.contains("LaunchedEffect(Unit)") &&
                mainActivity.contains("requestInitialAutoConnect()") &&
                mainActivity.contains("initialAutoConnectRequested = true") &&
                mainActivity.contains("controller.connect()")
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
    fun mainScreenPlacesAuthoritativeModeAndManualTakeoverBeforeConnectionButton() {
        val projectRoot = locateProjectRoot()
        val mainScreen = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/ui/main/MainControlScreen.kt"))
        val topHud = mainScreen.substringAfter("private fun TopHud")
            .substringBefore("private fun MotionModeEntry")

        val modeToggleIndex = topHud.indexOf("ControlModeToggle(")
        val connectionButtonIndex = topHud.indexOf("ConnectionButton(")
        assertTrue(
            "主控页必须把权威模式和人工接管入口放在连接按钮左侧",
            modeToggleIndex >= 0 && connectionButtonIndex > modeToggleIndex
        )
        assertTrue(
            "人工接管必须同时受连接状态、AUTO 权威状态和模式请求状态保护",
            mainScreen.contains("isEnabled && currentMode == AppMode.APP_MODE_AUTO && !isChanging") &&
                mainScreen.contains("modeEnabled = takeoverEnabled") &&
                mainScreen.contains("commandEnabled = takeoverEnabled && appMode == AppMode.APP_MODE_MANUAL")
        )
        val modeToggle = mainScreen.substringAfter("private fun ControlModeToggle")
            .substringBefore("private fun ConnectionButton")
        assertTrue(
            "模式按钮必须直接显示权威状态，且只能发送 MANUAL 人工接管",
            modeToggle.contains("onModeClick(AppMode.APP_MODE_MANUAL)") &&
                modeToggle.contains("自动·接管") &&
                modeToggle.contains("人工模式") &&
                !modeToggle.contains("genisdog_icon_robot")
        )
        val controller = Files.readString(
            projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/controller/Controller.kt")
        )
        assertTrue(
            "App 不得绕过导航任务直接向 driver 发送 AUTO",
            !controller.contains("zmqClient.setMode(AppMode.APP_MODE_AUTO)")
        )
    }

    @Test
    fun batteryButtonOnlyShowsIconAndOverlayShowsBothBatteries() {
        val projectRoot = locateProjectRoot()
        val mainScreen = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/ui/main/MainControlScreen.kt"))
        val batteryButton = mainScreen.substringAfter("private fun BatteryIconButton")
            .substringBefore("private fun supportedSportModes")
        val batteryOverlay = mainScreen.substringAfter("private fun BatteryStatusOverlay")
            .substringBefore("private fun StatusRow")

        assertTrue(
            "顶部电量按钮不得显示单路电量数字",
            !batteryButton.contains("batteryLevel") &&
                !batteryButton.contains("Text(")
        )
        assertTrue(
            "顶部电量按钮尺寸必须与设置按钮一致",
            batteryButton.contains(".size(46.dp)") &&
                batteryButton.contains("modifier = Modifier.size(30.dp)")
        )
        assertTrue(
            "电量浮层必须分别显示电池1和电池2",
            batteryOverlay.contains("电池 1") &&
                batteryOverlay.contains("battery1Level") &&
                batteryOverlay.contains("电池 2") &&
                batteryOverlay.contains("battery2Level")
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

    @Test
    fun inPlaceRightStickControlsHeadPitchWithStopProtection() {
        val projectRoot = locateProjectRoot()
        val controller = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/controller/Controller.kt"))

        val velocityLoop = controller.substringAfter("private fun startVelocityLoop")
            .substringBefore("private fun stopVelocityLoop")
        assertTrue(
            "原地模式必须从移动循环切换到连续头部姿态控制",
            velocityLoop.contains("settingsState.robotCtrlMode == SportMode.SPORT_MODE_IN_PLACE") &&
                velocityLoop.contains("sendInPlaceHeadControl(intent, headIntent)")
        )

        val inPlaceHeadControl = controller.substringAfter("private fun sendInPlaceHeadControl")
            .substringBefore("private fun stopVelocityLoop")
        assertTrue(
            "右杆左右和上下必须映射到 CONTROL_HEAD，并保留左右符号转换",
            inPlaceHeadControl.contains("leftRight = -intent.yawRight") &&
                inPlaceHeadControl.contains("upDown = headIntent.pitchUp") &&
                inPlaceHeadControl.contains("zmqClient.controlHead(leftRight, upDown)") &&
                inPlaceHeadControl.contains("headControlActive = true")
        )

        val stopHeadControl = controller.substringAfter("private fun stopHeadControl")
            .substringBefore("private fun handleMockHeadControl")
        assertTrue(
            "停止姿态输出时必须发送 CONTROL_HEAD(0, 0)",
            stopHeadControl.contains("zmqClient.controlHead(0f, 0f)") &&
                stopHeadControl.contains("headControlActive = false")
        )
    }

    @Test
    fun remoteLeftButtonsSetSpeedLevelsThroughController() {
        val projectRoot = locateProjectRoot()
        val models = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/input/remote/RemoteInputModels.kt"))
        val protocol = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/input/remote/unirc/UniRcProtocol.kt"))
        val controller = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/controller/Controller.kt"))

        assertTrue(
            "L1/L2/L3 必须按真机观测到的 CH5 离散值映射为低速/中速/高速",
            models.contains("speedSelector: RemoteInputSpeedSelectorMapping = RemoteInputSpeedSelectorMapping(channel = 5)") &&
                models.contains("lowRaw: Int = 1000") &&
                models.contains("mediumRaw: Int = 1200") &&
                models.contains("highRaw: Int = 1400")
        )
        assertTrue(
            "UniRC 输入层必须输出速度档请求，但不得在输入层发送协议命令",
            protocol.contains("RemoteSpeedLevelRequest.LOW") &&
                protocol.contains("RemoteSpeedLevelRequest.MEDIUM") &&
                protocol.contains("RemoteSpeedLevelRequest.HIGH") &&
                !protocol.contains("COMMAND_CODE_SET_SPEED_LEVEL")
        )
        assertTrue(
            "控制层必须把遥控器速度键转换为现有 setSpeedLevel 调用",
            controller.contains("handleRemoteSpeedLevelRequest(speedLevelRequest)") &&
                controller.contains("RemoteSpeedLevelRequest.LOW -> SpeedLevel.SLOW") &&
                controller.contains("RemoteSpeedLevelRequest.MEDIUM -> SpeedLevel.MEDIUM") &&
                controller.contains("RemoteSpeedLevelRequest.HIGH -> SpeedLevel.FAST") &&
                controller.contains("setSpeedLevel(speedLevel)")
        )
        assertTrue(
            "CH5 首帧只能作为速度基线，不能覆盖启动默认低速",
            controller.contains("remoteSpeedLevelSnapshotSeen") &&
                controller.contains("lastRemoteSpeedLevelRequest = speedLevelRequest") &&
                controller.contains("外部遥控速度基线")
        )
    }

    @Test
    fun unircRawUdpForwardingUsesLocalUdpNotAndroidBroadcast() {
        val projectRoot = locateProjectRoot()
        val inputSource = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/input/remote/unirc/UniRcUdpInputSource.kt"))
        val controller = Files.readString(projectRoot.resolve("app/src/main/java/com/helywin/leggedjoystick/controller/Controller.kt"))

        assertTrue(
            "UniRC 原始报文必须在解析前按 UDP datagram 原样转发",
            inputSource.contains("data = ByteArray(receiveBuffer.remaining())") &&
                inputSource.contains("rawForwarder.forward(data)") &&
                inputSource.indexOf("rawForwarder.forward(data)") < inputSource.indexOf("frameAssembler.append(data)") &&
                inputSource.contains("DatagramChannel.open()") &&
                !inputSource.contains("sendBroadcast")
        )
        assertTrue(
            "控制器必须把设置里的原始转发端口传给 UniRC 输入源",
            controller.contains("UniRcRawUdpForwardConfig") &&
                controller.contains("enabled = settings.remoteInputRawForwardEnabled") &&
                controller.contains("targetPort = settings.remoteInputRawForwardPort")
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
