/*********************************************************************************
 * FileName: MainControlScreen.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 主控制界面
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.ui.main

import android.view.SurfaceView
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.helywin.leggedjoystick.ui.components.ConnectionDialog
import com.helywin.leggedjoystick.ui.video.RtspVideoScaleMode
import com.helywin.leggedjoystick.ui.video.RtspVideoSlot
import com.helywin.leggedjoystick.ui.video.RtspVideoSurface
import com.helywin.leggedjoystick.ui.video.captureRtspSurfaceSnapshot
import legged_driver.AppMode
import legged_driver.ConnectionState as DriverConnectionState
import legged_driver.FaultLevel
import legged_driver.HeadDirection
import legged_driver.MotionStatus
import legged_driver.SportMode

private enum class RightToolPanel {
    LIGHT
}

private enum class PrimaryVideoSource {
    HEAD,
    TAIL
}

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
    val batteryLevel = settingsState.batteryLevel
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
    val commandEnabled = isConnected && hasControl

    var modeOverlayVisible by remember { mutableStateOf(false) }
    var speedSelectorVisible by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(true) }
    var batteryOverlayVisible by remember { mutableStateOf(false) }
    var activeRightToolPanel by remember { mutableStateOf<RightToolPanel?>(null) }
    var mainVideoSurface by remember { mutableStateOf<SurfaceView?>(null) }
    var secondaryVideoSurface by remember { mutableStateOf<SurfaceView?>(null) }
    var isTakingSnapshot by remember { mutableStateOf(false) }
    var primaryVideoSource by remember { mutableStateOf(PrimaryVideoSource.HEAD) }
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
                primarySurfaceView = mainVideoSurface,
                secondarySurfaceView = secondaryVideoSurface
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
        onSurfaceViewReady = { mainVideoSurface = it }
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
                    appMode = appMode,
                    connectionState = connectionState,
                    batteryLevel = batteryLevel,
                    onModeClick = controller::setMode,
                    onConnectClick = {
                        when (connectionState) {
                            ConnectionState.CONNECTED -> controller.disconnect()
                            ConnectionState.CONNECTING -> controller.cancelConnection()
                            else -> controller.connect()
                        }
                    },
                    onBatteryClick = { batteryOverlayVisible = !batteryOverlayVisible },
                    onSettingsClick = onSettingsClick
                )

                MotionModeEntry(
                    currentMode = currentSportMode,
                    modifier = Modifier.align(Alignment.TopCenter),
                    onClick = { modeOverlayVisible = true }
                )

                MiniVideoWindow(
                    rtspUrl = secondaryVideoUrl,
                    onSurfaceViewReady = { secondaryVideoSurface = it },
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
                        batteryLevel = batteryLevel,
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
        }
    }
}

@Composable
private fun ControlScreenBackground(
    rtspUrl: String,
    onSurfaceViewReady: (SurfaceView?) -> Unit,
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
            onSurfaceViewReady = onSurfaceViewReady,
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
    appMode: AppMode,
    connectionState: ConnectionState,
    batteryLevel: Int,
    onModeClick: (AppMode) -> Unit,
    onConnectClick: () -> Unit,
    onBatteryClick: () -> Unit,
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
            if (connectionState == ConnectionState.CONNECTED) {
                ControlModeToggle(
                    currentMode = appMode,
                    isConnected = true,
                    onModeClick = onModeClick
                )
            }
            ConnectionButton(
                connectionState = connectionState,
                onClick = onConnectClick
            )
            BatteryIconButton(
                batteryLevel = batteryLevel,
                onClick = onBatteryClick
            )
            HudIconButton(
                iconResId = R.drawable.genisdog_icon_setting,
                contentDescription = "设置",
                onClick = onSettingsClick
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
    onSurfaceViewReady: (SurfaceView?) -> Unit,
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
            useTextureView = false,
            showStatus = false,
            onSurfaceViewReady = onSurfaceViewReady,
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
            text = "%.1f".format(currentSpeedValue),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 30.dp),
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 18.sp,
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
                modifier = Modifier.padding(horizontal = 8.dp),
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
            .background(background)
            .clip(RoundedCornerShape(15.dp))
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
    batteryLevel: Int,
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
            StatusRow("电量", "$batteryLevel%", batteryLevelColor(batteryLevel))
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
private fun ControlModeToggle(
    currentMode: AppMode,
    isConnected: Boolean,
    onModeClick: (AppMode) -> Unit
) {
    val nextMode = if (currentMode == AppMode.APP_MODE_MANUAL) {
        AppMode.APP_MODE_AUTO
    } else {
        AppMode.APP_MODE_MANUAL
    }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(shape)
            .background(
                if (currentMode == AppMode.APP_MODE_MANUAL) {
                    Color(0x7A27C7C4)
                } else {
                    PanelBackground
                }
            )
            .noIndicationClickable(enabled = isConnected) { onModeClick(nextMode) },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.genisdog_icon_robot),
            contentDescription = currentMode.displayName,
            modifier = Modifier.size(28.dp),
            contentScale = ContentScale.Fit
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
    batteryLevel: Int,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .width(64.dp)
            .height(46.dp)
            .clip(shape)
            .background(PanelBackground)
            .noIndicationClickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.genisdog_icon_battery),
            contentDescription = "电量",
            modifier = Modifier.size(24.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = "$batteryLevel",
            color = batteryLevelColor(batteryLevel),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
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

private fun batteryLevelColor(batteryLevel: Int): Color {
    return when {
        batteryLevel > 50 -> Color(0xFF5ED17A)
        batteryLevel > 20 -> Color(0xFFE2A72E)
        else -> Color(0xFFE45D3D)
    }
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
