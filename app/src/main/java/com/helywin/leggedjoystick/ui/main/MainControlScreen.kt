/*********************************************************************************
 * FileName: MainControlScreen.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 主控制界面
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.ui.main

import android.os.SystemClock
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.helywin.leggedjoystick.R
import com.helywin.leggedjoystick.controller.Controller
import com.helywin.leggedjoystick.controller.RobotAction
import com.helywin.leggedjoystick.controller.settingsState
import com.helywin.leggedjoystick.data.AppSettings
import com.helywin.leggedjoystick.data.ConnectionState
import com.helywin.leggedjoystick.data.DriverConnectionTelemetry
import com.helywin.leggedjoystick.data.FaultTelemetry
import com.helywin.leggedjoystick.data.HighLowStance
import com.helywin.leggedjoystick.data.MotionTelemetry
import com.helywin.leggedjoystick.data.OdometryTelemetry
import com.helywin.leggedjoystick.data.SpeedLevel
import com.helywin.leggedjoystick.data.Vector3Telemetry
import com.helywin.leggedjoystick.input.remote.RemoteInputRuntimeState
import com.helywin.leggedjoystick.input.remote.RemoteInputStatus
import com.helywin.leggedjoystick.proto.displayName
import com.helywin.leggedjoystick.product.RemoteProductPolicy
import com.helywin.leggedjoystick.ui.components.ConnectionDialog
import com.helywin.leggedjoystick.ui.components.OperatorAlertDialog
import com.helywin.leggedjoystick.ui.mapping.MappingWorkspace
import com.helywin.leggedjoystick.ui.mapping.MapNavigationWorkspace
import com.helywin.leggedjoystick.ui.video.RtspVideoScaleMode
import com.helywin.leggedjoystick.ui.video.RtspVideoSlot
import com.helywin.leggedjoystick.ui.video.RtspVideoSurface
import com.helywin.leggedjoystick.ui.video.captureRtspSurfaceSnapshot
import kotlinx.coroutines.delay
import legged_driver.AppMode
import legged_driver.ConnectionState as DriverConnectionState
import legged_driver.FaultLevel
import legged_driver.HeadDirection
import legged_driver.MotionStatus
import legged_driver.SportMode
import sar.robot_controller.v1.OperationMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class RightToolPanel {
    LIGHT
}

private enum class PrimaryVideoSource {
    HEAD,
    TAIL
}

private data class ActiveWorkflowSwitch(
    val request: WorkflowSwitchRequest,
    val dispatchedCommand: WorkflowSwitchCommand? = null,
    val dispatchedRequestId: Long = 0L,
    val dispatchedAtMs: Long = 0L,
    val failure: String = ""
)

/**
 * 主控制界面。
 */
@Composable
fun MainControlScreen(
    controller: Controller,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val currentSportMode = settingsState.robotCtrlMode
    val motionStatus = settingsState.motionStatus
    val appMode = settingsState.robotMode
    val connectionState = settingsState.connectionState
    val battery1Level = settingsState.battery1Level
    val battery2Level = settingsState.battery2Level
    val speedLevel = settingsState.settings.speedLevel
    val currentSpeedValue = settingsState.currentSpeedValue
    val frontLightOn = settingsState.frontLightOn
    val backLightOn = settingsState.backLightOn
    val autoModeLightOn = settingsState.autoModeLightOn
    val remoteInputState = settingsState.remoteInputState
    val headDirection = settingsState.headDirection
    val lastCommandName = settingsState.lastCommandName
    val lastCommandDetail = settingsState.lastCommandDetail
    val driverConnectionTelemetry = settingsState.driverConnectionTelemetry
    val motionTelemetry = settingsState.motionTelemetry
    val faultTelemetry = settingsState.faultTelemetry
    val odometryTelemetry = settingsState.odometryTelemetry
    val isSportModeChanging = settingsState.isRobotCtrlModeChanging
    val isConnected = connectionState == ConnectionState.CONNECTED
    val hasControl = settingsState.hasControl
    val manualControlReady = isConnected && hasControl
    val commandEnabled = manualControlReady && appMode == AppMode.APP_MODE_MANUAL
    val controllerOperationMode = settingsState.controllerSnapshot?.operation_mode

    var modeOverlayVisible by remember { mutableStateOf(false) }
    var speedSelectorVisible by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(true) }
    var batteryOverlayVisible by remember { mutableStateOf(false) }
    var activeRightToolPanel by remember { mutableStateOf<RightToolPanel?>(null) }
    var mainVideoTexture by remember { mutableStateOf<TextureView?>(null) }
    var secondaryVideoTexture by remember { mutableStateOf<TextureView?>(null) }
    var isTakingSnapshot by remember { mutableStateOf(false) }
    var primaryVideoSource by remember { mutableStateOf(PrimaryVideoSource.HEAD) }
    var mappingWorkspaceVisible by remember { mutableStateOf(false) }
    var navigationWorkspaceVisible by remember { mutableStateOf(false) }
    var taskHubVisible by remember { mutableStateOf(false) }
    var workflowSwitchPrompt by remember { mutableStateOf<TaskWorkspace?>(null) }
    var activeWorkflowSwitch by remember { mutableStateOf<ActiveWorkflowSwitch?>(null) }

    val openWorkspace: (TaskWorkspace) -> Unit = { target ->
        mappingWorkspaceVisible = target == TaskWorkspace.MAPPING
        navigationWorkspaceVisible = target == TaskWorkspace.NAVIGATION
    }
    val requestWorkspace: (TaskWorkspace) -> Unit = { target ->
        val requiresSwitch = when (target) {
            TaskWorkspace.MAPPING -> controllerOperationMode.isLocalizationMode()
            TaskWorkspace.NAVIGATION -> controllerOperationMode.isMappingMode()
        }
        if (requiresSwitch) {
            workflowSwitchPrompt = target
        } else {
            openWorkspace(target)
        }
    }
    val switchAuthority = WorkflowSwitchAuthority(
        sessionGeneration = settingsState.mapNavigationState.sessionGeneration,
        connected = settingsState.controllerConnectionState ==
            com.helywin.leggedjoystick.zmq.RobotControllerConnectionState.CONNECTED,
        mode = controllerOperationMode ?: OperationMode.OPERATION_MODE_UNSPECIFIED,
        allowedActions = settingsState.controllerSnapshot?.allowed_actions.orEmpty()
            .filter { it.allowed }
            .map { it.action }
            .toSet(),
        requestInFlight = settingsState.pendingControllerRequestId != 0L
    )
    val workflowSwitchPlan = activeWorkflowSwitch
        ?.takeIf { it.failure.isEmpty() }
        ?.let {
            WorkflowSwitchPlanner.plan(
                request = it.request,
                authority = switchAuthority,
                dispatched = it.dispatchedCommand
            )
        }

    LaunchedEffect(
        workflowSwitchPlan,
        settingsState.controllerSnapshot?.state_revision,
        settingsState.pendingControllerRequestId
    ) {
        val active = activeWorkflowSwitch ?: return@LaunchedEffect
        when (val plan = workflowSwitchPlan) {
            WorkflowSwitchPlan.Complete -> {
                openWorkspace(active.request.target)
                activeWorkflowSwitch = null
            }
            is WorkflowSwitchPlan.Execute -> {
                val modeBefore = settingsState.controllerSnapshot?.operation_mode
                when (plan.command) {
                    WorkflowSwitchCommand.STOP_RUNTIME -> controller.stopLocalizationRuntime()
                    WorkflowSwitchCommand.FINISH_MAPPING -> controller.finishMapping()
                    WorkflowSwitchCommand.SAVE_MAP -> {
                        controller.saveMap(active.request.mapDisplayName)
                    }
                    WorkflowSwitchCommand.DISCARD_MAP -> controller.discardMap()
                }
                val requestId = settingsState.pendingControllerRequestId
                val modeAfter = settingsState.controllerSnapshot?.operation_mode
                activeWorkflowSwitch = active.copy(
                    dispatchedCommand = plan.command,
                    dispatchedRequestId = requestId,
                    dispatchedAtMs = SystemClock.elapsedRealtime(),
                    failure = if (requestId == 0L && modeAfter == modeBefore) {
                        settingsState.mapNavigationError
                            .ifEmpty { settingsState.mappingError }
                            .ifEmpty { "${plan.command.displayName()}请求未进入主控队列" }
                    } else {
                        ""
                    }
                )
            }
            is WorkflowSwitchPlan.Failed -> {
                activeWorkflowSwitch = active.copy(failure = plan.message)
            }
            is WorkflowSwitchPlan.Wait,
            null -> Unit
        }
    }

    LaunchedEffect(settingsState.lastControllerCommandResult) {
        val result = settingsState.lastControllerCommandResult ?: return@LaunchedEffect
        val active = activeWorkflowSwitch ?: return@LaunchedEffect
        if (active.dispatchedRequestId == result.requestId && !result.accepted) {
            activeWorkflowSwitch = active.copy(failure = result.message)
        }
    }

    LaunchedEffect(
        activeWorkflowSwitch?.dispatchedCommand,
        activeWorkflowSwitch?.dispatchedAtMs
    ) {
        val active = activeWorkflowSwitch ?: return@LaunchedEffect
        val command = active.dispatchedCommand ?: return@LaunchedEffect
        val dispatchedAtMs = active.dispatchedAtMs
        delay(45_000L)
        val current = activeWorkflowSwitch ?: return@LaunchedEffect
        if (current.dispatchedCommand == command &&
            current.dispatchedAtMs == dispatchedAtMs &&
            current.failure.isEmpty()
        ) {
            activeWorkflowSwitch = current.copy(
                failure = "等待${command.displayName()}后的权威状态更新超时，请核对当前状态后重试"
            )
        }
    }
    val primaryVideoUrl = when (primaryVideoSource) {
        PrimaryVideoSource.HEAD -> settingsState.settings.headRtspUrl
        PrimaryVideoSource.TAIL -> settingsState.settings.tailRtspUrl
    }
    val secondaryVideoUrl = when (primaryVideoSource) {
        PrimaryVideoSource.HEAD -> settingsState.settings.tailRtspUrl
        PrimaryVideoSource.TAIL -> settingsState.settings.headRtspUrl
    }
    val onPhotoClick = {
        if (!isTakingSnapshot) {
            isTakingSnapshot = true
            captureRtspSurfaceSnapshot(
                context = context,
                primaryTextureView = mainVideoTexture,
                secondaryTextureView = secondaryVideoTexture
            ) {
                isTakingSnapshot = false
            }
        }
    }

    ConnectionDialog(
        connectionState = connectionState,
        onDismiss = {
            if (connectionState != ConnectionState.CONNECTING) {
                controller.disconnect()
            }
        },
        onCancel = controller::cancelConnection
    )

    ControlScreenBackground(
        rtspUrl = primaryVideoUrl,
        onTextureViewReady = { mainVideoTexture = it }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp)
            ) {
                TopHud(
                    connectionState = connectionState,
                    appMode = appMode,
                    appModeChanging = settingsState.isRobotModeChanging,
                    showAppModeControl = RemoteProductPolicy.appModeControlEnabled,
                    showTaskHub = RemoteProductPolicy.taskWorkspaceEnabled,
                    onConnectClick = {
                        when (connectionState) {
                            ConnectionState.CONNECTED -> controller.disconnect()
                            ConnectionState.CONNECTING -> controller.cancelConnection()
                            else -> controller.connect()
                        }
                    },
                    onBatteryClick = { batteryOverlayVisible = !batteryOverlayVisible },
                    onAppModeClick = {
                        controller.setMode(
                            if (appMode == AppMode.APP_MODE_AUTO) {
                                AppMode.APP_MODE_MANUAL
                            } else {
                                AppMode.APP_MODE_AUTO
                            }
                        )
                    },
                    onTaskHubClick = { taskHubVisible = true },
                    onSettingsClick = onSettingsClick
                )

                MotionModeEntry(
                    currentMode = currentSportMode,
                    modifier = Modifier.align(Alignment.TopCenter),
                    onClick = { modeOverlayVisible = true }
                )

                MiniVideoWindow(
                    rtspUrl = secondaryVideoUrl,
                    onTextureViewReady = { secondaryVideoTexture = it },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = 20.dp),
                    onClick = {
                        primaryVideoSource = when (primaryVideoSource) {
                            PrimaryVideoSource.HEAD -> PrimaryVideoSource.TAIL
                            PrimaryVideoSource.TAIL -> PrimaryVideoSource.HEAD
                        }
                    }
                )

                RemoteInputPanel(
                    remoteInputState = remoteInputState,
                    lastCommandName = lastCommandName,
                    lastCommandDetail = lastCommandDetail,
                    driverConnectionTelemetry = driverConnectionTelemetry,
                    motionTelemetry = motionTelemetry,
                    faultTelemetry = faultTelemetry,
                    odometryTelemetry = odometryTelemetry,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 288.dp, y = 56.dp)
                )

                VerticalSpeedSelector(
                    currentLevel = speedLevel,
                    currentSpeedValue = currentSpeedValue,
                    expanded = speedSelectorVisible,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = 200.dp)
                        .zIndex(1f),
                    onExpandClick = { speedSelectorVisible = !speedSelectorVisible },
                    onLevelSelected = { level ->
                        speedSelectorVisible = false
                        controller.setSpeedLevel(level)
                    }
                )

                RightToolButton(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(y = (-39).dp),
                    iconResId = R.drawable.genisdog_icon_photo,
                    contentDescription = if (isTakingSnapshot) "正在拍照" else "拍照",
                    selected = false,
                    clickEnabled = !isTakingSnapshot,
                    commandEnabled = true,
                    onClick = onPhotoClick
                )

                RightToolColumn(
                    activePanel = activeRightToolPanel,
                    headDirection = headDirection,
                    commandEnabled = commandEnabled,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(y = 54.dp),
                    onPanelToggle = { panel ->
                        activeRightToolPanel = if (activeRightToolPanel == panel) null else panel
                    },
                    onReverseHeadTailClick = controller::reverseHeadTail
                )

                when (activeRightToolPanel) {
                    RightToolPanel.LIGHT -> {
                        LightControlPanel(
                            frontLightOn = frontLightOn,
                            backLightOn = backLightOn,
                            autoModeLightOn = autoModeLightOn,
                            enabled = commandEnabled,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = (-68).dp, y = (-76).dp)
                                .zIndex(8f),
                            onFrontLightClick = { controller.setFrontLight(!frontLightOn) },
                            onBackLightClick = { controller.setBackLight(!backLightOn) },
                            onAutoModeLightClick = { controller.setAutoModeLight(!autoModeLightOn) }
                        )
                    }
                    null -> Unit
                }

                BottomActionGroup(
                    expanded = actionsExpanded,
                    currentMotionStatus = motionStatus,
                    commandEnabled = commandEnabled,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onToggle = { actionsExpanded = !actionsExpanded },
                    onActionClick = controller::performAction
                )

                if (batteryOverlayVisible) {
                    BatteryStatusOverlay(
                        battery1Level = battery1Level,
                        battery2Level = battery2Level,
                        connectionState = connectionState,
                        appMode = appMode,
                        sportMode = currentSportMode,
                        remoteInputState = remoteInputState,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(y = 66.dp)
                    )
                }
            }

            if (modeOverlayVisible) {
                MotionModeOverlay(
                    currentMode = currentSportMode,
                    isConnected = isConnected && hasControl,
                    isChanging = isSportModeChanging,
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(20f),
                    onDismiss = { modeOverlayVisible = false },
                    onModeSelected = { mode ->
                        controller.setControlMode(mode)
                        modeOverlayVisible = false
                    }
                )
            }

            if (RemoteProductPolicy.taskWorkspaceEnabled && mappingWorkspaceVisible) {
                MappingWorkspace(
                    state = settingsState,
                    controller = controller,
                    onClose = { mappingWorkspaceVisible = false },
                    onSwitchToNavigation = {
                        requestWorkspace(TaskWorkspace.NAVIGATION)
                    },
                    modifier = Modifier.matchParentSize().zIndex(30f)
                )
            }
            if (RemoteProductPolicy.taskWorkspaceEnabled && navigationWorkspaceVisible) {
                MapNavigationWorkspace(
                    state = settingsState,
                    controller = controller,
                    onClose = { navigationWorkspaceVisible = false },
                    onSwitchToMapping = {
                        requestWorkspace(TaskWorkspace.MAPPING)
                    },
                    modifier = Modifier.matchParentSize().zIndex(30f)
                )
            }
        }
    }

    if (RemoteProductPolicy.taskWorkspaceEnabled && taskHubVisible) {
        TaskHubDialog(
            onDismiss = { taskHubVisible = false },
            onMappingClick = {
                taskHubVisible = false
                requestWorkspace(TaskWorkspace.MAPPING)
            },
            onNavigationClick = {
                taskHubVisible = false
                requestWorkspace(TaskWorkspace.NAVIGATION)
            }
        )
    }

    workflowSwitchPrompt?.let { target ->
        WorkflowSwitchPromptDialog(
            target = target,
            operationMode = controllerOperationMode,
            onDismiss = { workflowSwitchPrompt = null },
            onConfirm = { exitChoice, mapName ->
                workflowSwitchPrompt = null
                activeWorkflowSwitch = ActiveWorkflowSwitch(
                    request = WorkflowSwitchRequest(
                        target = target,
                        sessionGeneration = settingsState.mapNavigationState.sessionGeneration,
                        mappingExitChoice = exitChoice,
                        mapDisplayName = mapName
                    )
                )
            }
        )
    }

    activeWorkflowSwitch?.let { active ->
        WorkflowSwitchProgressDialog(
            active = active,
            plan = workflowSwitchPlan,
            operationMode = controllerOperationMode,
            onStopFollowingSteps = { activeWorkflowSwitch = null },
            onRetry = {
                activeWorkflowSwitch = ActiveWorkflowSwitch(
                    request = active.request.copy(
                        sessionGeneration = settingsState.mapNavigationState.sessionGeneration
                    )
                )
            }
        )
    }
}

@Composable
private fun ControlScreenBackground(
    rtspUrl: String,
    onTextureViewReady: (TextureView?) -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1B201F),
                        Color(0xFF111716),
                        Color(0xFF080B0D)
                    )
                )
            )
    ) {
        RtspVideoSurface(
            rtspUrl = rtspUrl,
            slot = RtspVideoSlot.Main,
            scaleMode = RtspVideoScaleMode.Fill,
            showStatus = false,
            showReconnectIndicator = true,
            onTextureViewReady = onTextureViewReady,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.10f))
        )
        content()
    }
}

@Composable
private fun TopHud(
    connectionState: ConnectionState,
    appMode: AppMode,
    appModeChanging: Boolean,
    showAppModeControl: Boolean,
    showTaskHub: Boolean,
    onConnectClick: () -> Unit,
    onBatteryClick: () -> Unit,
    onAppModeClick: () -> Unit,
    onTaskHubClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showAppModeControl) {
                AppModeToggleButton(
                    appMode = appMode,
                    enabled = connectionState == ConnectionState.CONNECTED && !appModeChanging,
                    onClick = onAppModeClick
                )
            }
            ConnectionButton(
                connectionState = connectionState,
                onClick = onConnectClick
            )
            BatteryIconButton(
                onClick = onBatteryClick
            )
            if (showTaskHub) {
                TaskHubButton(onClick = onTaskHubClick)
            }
            HudIconButton(
                iconResId = R.drawable.genisdog_icon_setting,
                contentDescription = "设置",
                onClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun AppModeToggleButton(
    appMode: AppMode,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val isAuto = appMode == AppMode.APP_MODE_AUTO
    Row(
        modifier = Modifier
            .width(92.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isAuto) AccentCyan.copy(alpha = 0.28f) else PanelBackground)
            .noIndicationClickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.55f },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isAuto) "自动" else "手动",
            color = if (isAuto) AccentCyan else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TaskHubButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(78.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PanelBackground)
            .noIndicationClickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Map,
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text("任务", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TaskHubDialog(
    onDismiss: () -> Unit,
    onMappingClick: () -> Unit,
    onNavigationClick: () -> Unit
) {
    OperatorAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("任务工作区") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WorkspaceChoice(
                    icon = Icons.Default.Map,
                    title = "实时建图",
                    description = "查看实时地图并完成结束、保存或放弃",
                    onClick = onMappingClick
                )
                WorkspaceChoice(
                    icon = Icons.Default.Place,
                    title = "定位与导航",
                    description = "选择地图、设置位姿、规划并跟踪目标",
                    onClick = onNavigationClick
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun WorkflowSwitchPromptDialog(
    target: TaskWorkspace,
    operationMode: OperationMode?,
    onDismiss: () -> Unit,
    onConfirm: (MappingExitChoice?, String) -> Unit
) {
    val defaultMapName = remember {
        "map-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}"
    }
    var mapName by remember { mutableStateOf(defaultMapName) }
    val validMapName = mapName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*"))
    val savingInProgress = operationMode == OperationMode.OPERATION_MODE_MAPPING_SAVING

    OperatorAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (target == TaskWorkspace.MAPPING) "切换到建图" else "切换到定位导航")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (target == TaskWorkspace.MAPPING) {
                    Text(
                        "主控会先取消活动导航、停车并停止定位运行时；确认回到待机后只打开建图工作区，不会自动开始建图。"
                    )
                } else if (savingInProgress) {
                    Text("地图正在保存。App 将等待主控回到待机，再打开定位导航工作区。")
                } else {
                    Text(
                        "当前建图必须先结束，再明确保存或放弃；每一步都会等待主控权威状态，不会因退出页面丢失地图。"
                    )
                    OutlinedTextField(
                        value = mapName,
                        onValueChange = { mapName = it.trim() },
                        label = { Text("保存后的地图名") },
                        supportingText = { Text("仅支持字母、数字、点、下划线和连字符") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                    )
                }
                Text(
                    "当前主控状态：${operationMode?.name ?: "未知"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            when {
                target == TaskWorkspace.MAPPING -> {
                    Button(onClick = { onConfirm(null, "") }) {
                        Text("停止定位并切换")
                    }
                }
                savingInProgress -> {
                    Button(onClick = { onConfirm(MappingExitChoice.SAVE, mapName) }) {
                        Text("保存完成后切换")
                    }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onConfirm(MappingExitChoice.DISCARD, "") }
                        ) {
                            Text("放弃并切换")
                        }
                        Button(
                            onClick = { onConfirm(MappingExitChoice.SAVE, mapName) },
                            enabled = validMapName
                        ) {
                            Text("保存并切换")
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun WorkflowSwitchProgressDialog(
    active: ActiveWorkflowSwitch,
    plan: WorkflowSwitchPlan?,
    operationMode: OperationMode?,
    onStopFollowingSteps: () -> Unit,
    onRetry: () -> Unit
) {
    val targetLabel = if (active.request.target == TaskWorkspace.MAPPING) "建图" else "定位导航"
    val message = active.failure.ifEmpty {
        when (plan) {
            is WorkflowSwitchPlan.Wait -> plan.message
            is WorkflowSwitchPlan.Execute -> "正在发送${plan.command.displayName()}命令"
            WorkflowSwitchPlan.Complete -> "切换完成"
            is WorkflowSwitchPlan.Failed -> plan.message
            null -> "正在读取主控权威状态"
        }
    }
    OperatorAlertDialog(
        onDismissRequest = onStopFollowingSteps,
        title = { Text(if (active.failure.isEmpty()) "正在切换到$targetLabel" else "任务切换未完成") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (active.failure.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(message)
                    }
                } else {
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "当前主控状态：${operationMode?.name ?: "未知"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                if (active.failure.isEmpty()) {
                    Text(
                        "关闭只会停止 App 发送后续切换步骤，不会撤销主控已经接受的命令。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            if (active.failure.isNotEmpty()) {
                Button(onClick = onRetry) { Text("按当前状态重试") }
            }
        },
        dismissButton = {
            TextButton(onClick = onStopFollowingSteps) {
                Text(if (active.failure.isEmpty()) "停止后续切换" else "关闭")
            }
        }
    )
}

@Composable
private fun WorkspaceChoice(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainerHighest)
            .border(1.dp, colors.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                description,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun MotionModeEntry(
    currentMode: SportMode,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .height(40.dp)
            .width(140.dp)
            .clip(shape)
            .background(Color(0xCC1F2A2B))
            .noIndicationClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(currentMode.iconResId()),
                contentDescription = null,
                modifier = Modifier.size(23.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = currentMode.displayName,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.78f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MiniVideoWindow(
    rtspUrl: String,
    modifier: Modifier = Modifier,
    onTextureViewReady: (TextureView?) -> Unit,
    onClick: () -> Unit
) {

    Surface(
        modifier = modifier
            .width(270.dp)
            .aspectRatio(16f / 9f)
            .noIndicationClickable(onClick = onClick)
            .border(2.dp, Color.White.copy(alpha = 0.62f)),
        color = Color(0xFF080A0A),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp
    ) {
        RtspVideoSurface(
            rtspUrl = rtspUrl,
            slot = RtspVideoSlot.Secondary,
            scaleMode = RtspVideoScaleMode.BestFit,
            showStatus = false,
            showReconnectIndicator = true,
            onTextureViewReady = onTextureViewReady,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun RemoteInputPanel(
    remoteInputState: RemoteInputRuntimeState,
    lastCommandName: String,
    lastCommandDetail: String,
    driverConnectionTelemetry: DriverConnectionTelemetry,
    motionTelemetry: MotionTelemetry,
    faultTelemetry: FaultTelemetry,
    odometryTelemetry: OdometryTelemetry,
    modifier: Modifier = Modifier
) {
    val snapshot = remoteInputState.latestSnapshot
    val intent = snapshot?.movementIntent
    val rawChannels = snapshot?.rawChannels.orEmpty()
    val axes = snapshot?.normalizedAxes.orEmpty()
    val lastCommandText = if (lastCommandDetail.isBlank()) {
        lastCommandName
    } else {
        "$lastCommandName $lastCommandDetail"
    }
}

@Composable
private fun VerticalSpeedSelector(
    currentLevel: SpeedLevel,
    currentSpeedValue: Double,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onExpandClick: () -> Unit,
    onLevelSelected: (SpeedLevel) -> Unit
) {
    Box(
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(30.dp)
                .noIndicationClickable(onClick = onExpandClick),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(currentLevel.speedButtonResId()),
                contentDescription = currentLevel.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            Text(
                text = currentLevel.displayName,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        Text(
            text = "%.1f m/s".format(currentSpeedValue),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 30.dp),
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        if (expanded) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 0.dp, y = 46.dp)
                    .width(108.dp)
                    .zIndex(2f),
                color = Color(0xEE111817),
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 0.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(SpeedLevel.SLOW, SpeedLevel.MEDIUM, SpeedLevel.FAST).forEach { level ->
                        SpeedLevelMenuItem(
                            level = level,
                            selected = currentLevel == level,
                            onClick = { onLevelSelected(level) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RightToolColumn(
    activePanel: RightToolPanel?,
    headDirection: HeadDirection,
    commandEnabled: Boolean,
    modifier: Modifier = Modifier,
    onPanelToggle: (RightToolPanel) -> Unit,
    onReverseHeadTailClick: () -> Unit
) {
    Column(
        modifier = modifier.width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RightToolButton(
            iconResId = R.drawable.genisdog_icon_light_white,
            contentDescription = "灯光",
            selected = activePanel == RightToolPanel.LIGHT,
            clickEnabled = true,
            commandEnabled = commandEnabled,
            onClick = { onPanelToggle(RightToolPanel.LIGHT) }
        )
        RightToolButton(
            iconResId = headDirection.perspectiveIconResId(),
            iconSize = 44.dp,
            contentDescription = if (headDirection == HeadDirection.HEAD_DIRECTION_TAIL) {
                "切回头部方向"
            } else {
                "切换到尾部方向"
            },
            selected = false,
            clickEnabled = commandEnabled,
            commandEnabled = commandEnabled,
            onClick = onReverseHeadTailClick
        )
    }
}

@Composable
private fun RightToolButton(
    modifier: Modifier = Modifier,
    iconResId: Int? = null,
    imageVector: ImageVector? = null,
    iconSize: Dp = 32.dp,
    contentDescription: String,
    selected: Boolean,
    clickEnabled: Boolean,
    commandEnabled: Boolean,
    onClick: () -> Unit,
    content: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(15.dp)
    val background = when {
        pressed -> PressedActionBackground
        selected -> Color(0x7A27C7C4)
        else -> PanelBackground
    }

    Box(
        modifier = modifier
            .size(52.dp)
            .clip(shape)
            .background(background)
            .clickable(
                enabled = clickEnabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (content != null) {
            content()
        } else if (iconResId != null) {
            Image(
                painter = painterResource(iconResId),
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        alpha = if (clickEnabled || commandEnabled) 1f else 0.46f
                        scaleX = if (pressed) 0.94f else 1f
                        scaleY = if (pressed) 0.94f else 1f
                    },
                contentScale = ContentScale.Fit
            )
        } else if (imageVector != null) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = if (selected) Color.White else AccentCyan,
                modifier = Modifier
                    .size(27.dp)
                    .graphicsLayer {
                        alpha = if (clickEnabled || commandEnabled) 1f else 0.46f
                        scaleX = if (pressed) 0.94f else 1f
                        scaleY = if (pressed) 0.94f else 1f
                    }
            )
        }
    }
}

@Composable
private fun LightControlPanel(
    frontLightOn: Boolean,
    backLightOn: Boolean,
    autoModeLightOn: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onFrontLightClick: () -> Unit,
    onBackLightClick: () -> Unit,
    onAutoModeLightClick: () -> Unit
) {
    ToolPanelSurface(
        modifier = modifier.width(176.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LightToggleRow(
                label = "前灯",
                iconResId = if (frontLightOn) {
                    R.drawable.genisdog_icon_light_front_on_white
                } else {
                    R.drawable.genisdog_icon_light_white
                },
                selected = frontLightOn,
                enabled = enabled,
                onClick = onFrontLightClick
            )
            LightToggleRow(
                label = "后灯",
                iconResId = if (backLightOn) {
                    R.drawable.genisdog_icon_light_back_on_white
                } else {
                    R.drawable.genisdog_icon_light_white
                },
                selected = backLightOn,
                enabled = enabled,
                onClick = onBackLightClick
            )
            LightToggleRow(
                label = "自动",
                iconResId = if (autoModeLightOn) {
                    R.drawable.genisdog_icon_light_all_on_white
                } else {
                    R.drawable.genisdog_icon_light_all_off_white
                },
                selected = autoModeLightOn,
                enabled = enabled,
                onClick = onAutoModeLightClick
            )
        }
    }
}

@Composable
private fun LightToggleRow(
    label: String,
    iconResId: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    pressed && enabled -> PressedActionBackground
                    selected -> Color(0x5527C7C4)
                    else -> Color.Transparent
                }
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(
            painter = painterResource(iconResId),
            contentDescription = label,
            modifier = Modifier.size(25.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.weight(1f))
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ToolPanelSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.border(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.12f),
            shape = RoundedCornerShape(14.dp)
        ),
        color = Color(0xE617201F),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp
    ) {
        content()
    }
}

@Composable
private fun BottomActionGroup(
    expanded: Boolean,
    currentMotionStatus: MotionStatus,
    commandEnabled: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onActionClick: (RobotAction) -> Unit
) {
    if (!expanded) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val shape = RoundedCornerShape(14.dp)
        Box(
            modifier = modifier
                .size(52.dp)
                .clip(shape)
                .background(if (pressed) PressedActionBackground else PanelBackground)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onToggle
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.genisdog_icon_dog),
                contentDescription = "展开动作组",
                modifier = Modifier.size(38.dp),
                contentScale = ContentScale.Fit
            )
        }
        return
    }

    Row(
        modifier = modifier.height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val toggleInteractionSource = remember { MutableInteractionSource() }
        val togglePressed by toggleInteractionSource.collectIsPressedAsState()
        val toggleShape = RoundedCornerShape(16.dp)
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(toggleShape)
                .background(if (togglePressed) PressedActionBackground else PanelBackground)
                .clickable(
                    interactionSource = toggleInteractionSource,
                    indication = null,
                    onClick = onToggle
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.genisdog_icon_dog_clicked),
                contentDescription = "收缩动作组",
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Fit
            )
        }

        Surface(
            modifier = Modifier
                .height(72.dp)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(15.dp)
                ),
            color = PanelBackground,
            shape = RoundedCornerShape(15.dp),
            tonalElevation = 0.dp,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                RobotAction.entries.forEach { action ->
                    ActionButton(
                        action = action,
                        selected = currentMotionStatus == action.motionStatus,
                        enabled = commandEnabled,
                        onClick = { onActionClick(action) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedLevelMenuItem(
    level: SpeedLevel,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .noIndicationClickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(30.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(level.speedButtonResId()),
                contentDescription = level.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            Text(
                text = level.displayName,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = level.color(),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ActionButton(
    action: RobotAction,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = when {
        pressed -> PressedActionBackground
        selected -> Color(0x7A27C7C4)
        else -> PanelBackground
    }


    Column(
        modifier = Modifier
            .width(66.dp)
            .height(66.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(background)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(action.iconResId(selected)),
            contentDescription = action.displayName,
            modifier = Modifier.size(26.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = action.displayName,
            color = if (selected) AccentCyan else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MotionModeOverlay(
    currentMode: SportMode,
    isConnected: Boolean,
    isChanging: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onModeSelected: (SportMode) -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .noIndicationClickable(onClick = onDismiss)
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.64f),
            color = Color(0xF21B2222),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 36.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "选择模式",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .noIndicationClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "关闭",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    supportedSportModes().forEach { mode ->
                        MotionModeCard(
                            mode = mode,
                            selected = currentMode == mode,
                            enabled = isConnected && !isChanging,
                            changing = isChanging && currentMode != mode,
                            onClick = { onModeSelected(mode) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MotionModeCard(
    mode: SportMode,
    selected: Boolean,
    enabled: Boolean,
    changing: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .width(190.dp)
            .height(154.dp)
            .clip(shape)
            .background(if (selected) Color(0xFF203434) else Color(0xFF121818))
            .noIndicationClickable(enabled = enabled, onClick = onClick)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AccentCyan else Color.White.copy(alpha = 0.16f),
                shape = shape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (changing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(34.dp),
                        strokeWidth = 3.dp,
                        color = AccentCyan
                    )
                } else {
                    Image(
                        painter = painterResource(mode.iconResId()),
                        contentDescription = null,
                        modifier = Modifier
                            .width(92.dp)
                            .height(58.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = mode.displayName,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BatteryStatusOverlay(
    battery1Level: Int?,
    battery2Level: Int?,
    connectionState: ConnectionState,
    appMode: AppMode,
    sportMode: SportMode,
    remoteInputState: RemoteInputRuntimeState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(260.dp),
        color = Color(0xF21B2222),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusRow("电池 1", formatBatteryLevel(battery1Level), batteryLevelColor(battery1Level))
            StatusRow("电池 2", formatBatteryLevel(battery2Level), batteryLevelColor(battery2Level))
            StatusRow("连接", connectionState.displayName, connectionState.color())
            StatusRow("模式", appMode.displayName, Color.White.copy(alpha = 0.88f))
            StatusRow("运动", sportMode.displayName, Color.White.copy(alpha = 0.88f))
            StatusRow("输入", remoteInputState.status.displayName, remoteInputState.status.statusColor())
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ConnectionButton(
    connectionState: ConnectionState,
    onClick: () -> Unit
) {
    val icon = when (connectionState) {
        ConnectionState.CONNECTED -> Icons.Filled.LinkOff
        ConnectionState.CONNECTING -> Icons.Filled.Sync
        ConnectionState.CONNECTION_FAILED,
        ConnectionState.CONNECTION_TIMEOUT -> Icons.Filled.Refresh
        ConnectionState.DISCONNECTED -> Icons.Filled.Link
    }
    val description = when (connectionState) {
        ConnectionState.CONNECTED -> "断开连接"
        ConnectionState.CONNECTING -> "取消连接"
        ConnectionState.CONNECTION_FAILED,
        ConnectionState.CONNECTION_TIMEOUT -> "重新连接"
        ConnectionState.DISCONNECTED -> "连接"
    }

    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(shape)
            .background(PanelBackground)
            .noIndicationClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = connectionState.color(),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun HudIconButton(
    iconResId: Int,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(shape)
            .background(PanelBackground)
            .noIndicationClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconResId),
            contentDescription = contentDescription,
            modifier = Modifier.size(30.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun BatteryIconButton(
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(shape)
            .background(PanelBackground)
            .noIndicationClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.genisdog_icon_battery),
            contentDescription = "查看双电池电量",
            modifier = Modifier.size(30.dp),
            contentScale = ContentScale.Fit
        )
    }
}

private fun supportedSportModes(): List<SportMode> {
    return listOf(
        SportMode.SPORT_MODE_GENERAL,
        SportMode.SPORT_MODE_IN_PLACE,
        SportMode.SPORT_MODE_STAIR
    )
}

@Composable
private fun Modifier.noIndicationClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}

private fun List<Int>.formatChannelLine(startIndex: Int): String {
    if (size <= startIndex) return "CH${startIndex + 1}-CH${startIndex + 4} 等待数据"

    return drop(startIndex)
        .take(4)
        .mapIndexed { index, value -> "${startIndex + index + 1}:$value" }
        .joinToString(separator = "  ", prefix = "CH ")
}

private fun Vector3Telemetry.formatTriple(): String {
    return "%.2f,%.2f,%.2f".format(x, y, z)
}

private fun DriverConnectionTelemetry.summaryText(): String {
    return when (connectionState) {
        DriverConnectionState.CONNECTION_STATE_CONNECTED -> "已连接"
        DriverConnectionState.CONNECTION_STATE_CONNECTING -> "连接中"
        DriverConnectionState.CONNECTION_STATE_HANDSHAKING -> "握手中"
        DriverConnectionState.CONNECTION_STATE_RECONNECTING -> "重连中"
        DriverConnectionState.CONNECTION_STATE_DISCONNECTING -> "断开中"
        DriverConnectionState.CONNECTION_STATE_DISCONNECTED -> "已断开"
    }
}

private fun FaultTelemetry.summaryText(): String {
    if (faultCount == 0) return "0"

    val level = when (highestLevel) {
        FaultLevel.FAULT_LEVEL_FATAL_ERROR -> "FATAL"
        FaultLevel.FAULT_LEVEL_ERROR -> "ERROR"
        FaultLevel.FAULT_LEVEL_WARN -> "WARN"
        FaultLevel.FAULT_LEVEL_UNKNOWN -> "UNKNOWN"
    }
    val message = highestMessage.ifBlank { highestCode.name.removePrefix("FAULT_CODE_") }

    return "$faultCount/$level $message"
}

private fun SportMode.iconResId(): Int {
    return when (this) {
        SportMode.SPORT_MODE_GENERAL -> R.drawable.genisdog_icon_mode_common
        SportMode.SPORT_MODE_IN_PLACE -> R.drawable.genisdog_icon_mode_in_place
        SportMode.SPORT_MODE_STAIR -> R.drawable.genisdog_icon_mode_stair
        else -> R.drawable.genisdog_icon_mode_common
    }
}

private fun RobotAction.iconResId(selected: Boolean = false): Int {
    return when (this) {
        RobotAction.STAND_UP -> if (selected) R.drawable.genisdog_icon_stand_selected else R.drawable.genisdog_icon_stand
        RobotAction.CRAWL -> if (selected) R.drawable.genisdog_icon_crawl_selected else R.drawable.genisdog_icon_crawl
        RobotAction.LIE_DOWN -> if (selected) R.drawable.genisdog_icon_lie_down_selected else R.drawable.genisdog_icon_lie_down
        RobotAction.CLIMB -> if (selected) {
            R.drawable.genisdog_icon_high_platform_selected
        } else {
            R.drawable.genisdog_icon_high_platform
        }
        RobotAction.SLIM -> if (selected) R.drawable.genisdog_icon_slim_selected else R.drawable.genisdog_icon_slim
        RobotAction.LOCKED -> if (selected) R.drawable.genisdog_icon_lock_selected else R.drawable.genisdog_icon_lock
    }
}

private fun SpeedLevel.speedButtonResId(): Int {
    return when (this) {
        SpeedLevel.SLOW -> R.drawable.genisdog_speed_slow
        SpeedLevel.MEDIUM -> R.drawable.genisdog_speed_medium
        SpeedLevel.FAST -> R.drawable.genisdog_speed_high
    }
}

private fun SpeedLevel.color(): Color {
    return when (this) {
        SpeedLevel.SLOW -> Color(0xFF1AAE9F)
        SpeedLevel.MEDIUM -> Color(0xFFE2A72E)
        SpeedLevel.FAST -> Color(0xFFE45D3D)
    }
}

private fun formatBatteryLevel(batteryLevel: Int?): String {
    return batteryLevel?.let { "$it%" } ?: "--"
}

private fun batteryLevelColor(batteryLevel: Int?): Color {
    return batteryLevel?.let {
        when {
            it > 50 -> Color(0xFF5ED17A)
            it > 20 -> Color(0xFFE2A72E)
            else -> Color(0xFFE45D3D)
        }
    } ?: Color.White.copy(alpha = 0.72f)
}

private fun ConnectionState.color(): Color {
    return when (this) {
        ConnectionState.CONNECTED -> Color(0xFF1AAE9F)
        ConnectionState.CONNECTING -> Color(0xFFE2A72E)
        ConnectionState.CONNECTION_FAILED,
        ConnectionState.CONNECTION_TIMEOUT -> Color(0xFFE45D3D)
        ConnectionState.DISCONNECTED -> Color(0xFF52635F)
    }
}

private fun RemoteInputStatus.statusColor(): Color {
    return when (this) {
        RemoteInputStatus.RUNNING -> Color(0xFF5ED17A)
        RemoteInputStatus.STARTING -> Color(0xFFE2A72E)
        RemoteInputStatus.TIMEOUT,
        RemoteInputStatus.ERROR -> Color(0xFFE45D3D)
        RemoteInputStatus.STOPPED -> Color.White.copy(alpha = 0.52f)
    }
}

private fun HeadDirection.perspectiveIconResId(): Int {
    return when (this) {
        HeadDirection.HEAD_DIRECTION_TAIL -> R.drawable.genisdog_icon_perspective_tail
        else -> R.drawable.genisdog_icon_perspective_head
    }
}

private val RemoteInputStatus.displayName: String
    get() = when (this) {
        RemoteInputStatus.STOPPED -> "未启动"
        RemoteInputStatus.STARTING -> "启动中"
        RemoteInputStatus.RUNNING -> "接收中"
        RemoteInputStatus.TIMEOUT -> "超时"
        RemoteInputStatus.ERROR -> "异常"
    }

private val AccentCyan = Color(0xFF27C7C4)
private val PanelBackground = Color(0xF217201F)
private val PressedActionBackground = Color(0xF11D3A38)

@Preview(showBackground = true, widthDp = 960, heightDp = 540)
@Composable
fun MainControlScreenPreview() {
    val dummyController = remember {
        object : Controller {
            override fun connect() {}
            override fun disconnect() {}
            override fun cancelConnection() {}
            override fun takeControl() {}
            override fun releaseControl() {}
            override fun setMode(mode: AppMode) {}
            override fun startMapping(draftName: String) {}
            override fun finishMapping() {}
            override fun saveMap(displayName: String) {}
            override fun discardMap() {}
            override fun requestLatestMappingMap() {}
            override fun refreshSavedMaps() {}
            override fun selectSavedMap(mapId: String, revision: Long) {}
            override fun requestSavedMapPreview(mapId: String, revision: Long) {}
            override fun switchSavedMap(mapId: String, revision: Long) {}
            override fun stopLocalizationRuntime() {}
            override fun editInitialPose(pose: com.helywin.leggedjoystick.mapping.MappingPose?) {}
            override fun submitInitialPose() {}
            override fun editNavigationTarget(pose: com.helywin.leggedjoystick.mapping.MappingPose?) {}
            override fun requestNavigationPreview() {}
            override fun startNavigation() {}
            override fun cancelNavigation() {}
            override fun setControlMode(controlMode: SportMode) {}
            override fun setSpeedLevel(level: SpeedLevel) {}
            override fun performAction(action: RobotAction) {}
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
        }
    }

    MainControlScreen(
        controller = dummyController,
        onSettingsClick = {}
    )
}
