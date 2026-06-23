/*********************************************************************************
 * FileName: MainControlScreen.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 主控制界面
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.rememberAsyncImagePainter
import com.helywin.leggedjoystick.R
import com.helywin.leggedjoystick.controller.Controller
import com.helywin.leggedjoystick.controller.RobotAction
import com.helywin.leggedjoystick.controller.settingsState
import com.helywin.leggedjoystick.data.AppSettings
import com.helywin.leggedjoystick.data.ConnectionState
import com.helywin.leggedjoystick.data.ControlOwnershipState
import com.helywin.leggedjoystick.data.HighLowStance
import com.helywin.leggedjoystick.data.SpeedLevel
import com.helywin.leggedjoystick.input.remote.RemoteInputRuntimeState
import com.helywin.leggedjoystick.input.remote.RemoteInputStatus
import com.helywin.leggedjoystick.proto.displayName
import com.helywin.leggedjoystick.ui.components.ConnectionDialog
import legged_driver.AppMode
import legged_driver.SportMode

private enum class RightToolPanel {
    LIGHT,
    HEAD,
    STANCE
}

private const val HEAD_CONTROL_STEP = 0.5f

/**
 * 主控制界面。
 */
@Composable
fun MainControlScreen(
    controller: Controller,
    onSettingsClick: () -> Unit,
    onVideoClick: () -> Unit
) {
    val currentSportMode = settingsState.robotCtrlMode
    val appMode = settingsState.robotMode
    val connectionState = settingsState.connectionState
    val batteryLevel = settingsState.batteryLevel
    val speedLevel = settingsState.settings.speedLevel
    val currentSpeedValue = settingsState.currentSpeedValue
    val frontLightOn = settingsState.frontLightOn
    val backLightOn = settingsState.backLightOn
    val autoModeLightOn = settingsState.autoModeLightOn
    val highLowStance = settingsState.highLowStance
    val controlOwnershipState = settingsState.controlOwnershipState
    val remoteInputState = settingsState.remoteInputState
    val isSportModeChanging = settingsState.isRobotCtrlModeChanging
    val mainTitle = settingsState.settings.mainTitle
    val logoPath = settingsState.settings.logoPath
    val isConnected = connectionState == ConnectionState.CONNECTED
    val hasControl = settingsState.hasControl
    val commandEnabled = isConnected && hasControl
    val inPlaceCommandEnabled = commandEnabled && currentSportMode == SportMode.SPORT_MODE_IN_PLACE

    var modeOverlayVisible by remember { mutableStateOf(false) }
    var speedSelectorVisible by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(true) }
    var batteryOverlayVisible by remember { mutableStateOf(false) }
    var activeRightToolPanel by remember { mutableStateOf<RightToolPanel?>(null) }

    ConnectionDialog(
        connectionState = connectionState,
        onDismiss = {
            if (connectionState != ConnectionState.CONNECTING) {
                controller.disconnect()
            }
        },
        onCancel = controller::cancelConnection
    )

    ControlScreenBackground {
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
                    title = mainTitle.ifEmpty { "机器狗遥控器" },
                    logoPath = logoPath,
                    appMode = appMode,
                    connectionState = connectionState,
                    controlOwnershipState = controlOwnershipState,
                    batteryLevel = batteryLevel,
                    remoteInputState = remoteInputState,
                    onModeClick = controller::setMode,
                    onControlOwnershipClick = {
                        if (hasControl) {
                            controller.releaseControl()
                        } else {
                            controller.takeControl()
                        }
                    },
                    onConnectClick = {
                        when (connectionState) {
                            ConnectionState.CONNECTED -> controller.disconnect()
                            ConnectionState.CONNECTING -> controller.cancelConnection()
                            else -> controller.connect()
                        }
                    },
                    onBatteryClick = { batteryOverlayVisible = !batteryOverlayVisible },
                    onVideoClick = onVideoClick,
                    onSettingsClick = onSettingsClick
                )

                MotionModeEntry(
                    currentMode = currentSportMode,
                    modifier = Modifier.align(Alignment.TopCenter),
                    onClick = { modeOverlayVisible = true }
                )

                MiniVideoWindow(
                    logoPath = logoPath,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = 86.dp)
                )

                RemoteInputPanel(
                    remoteInputState = remoteInputState,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 258.dp, y = 86.dp)
                )

                VerticalSpeedSelector(
                    currentLevel = speedLevel,
                    currentSpeedValue = currentSpeedValue,
                    expanded = speedSelectorVisible,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = 252.dp)
                        .zIndex(1f),
                    onExpandClick = { speedSelectorVisible = !speedSelectorVisible },
                    onLevelSelected = { level ->
                        speedSelectorVisible = false
                        controller.setSpeedLevel(level)
                    }
                )

                RightToolColumn(
                    activePanel = activeRightToolPanel,
                    commandEnabled = commandEnabled,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(y = 54.dp),
                    onPanelToggle = { panel ->
                        activeRightToolPanel = if (activeRightToolPanel == panel) null else panel
                    }
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
                    RightToolPanel.HEAD -> {
                        HeadControlPanel(
                            enabled = inPlaceCommandEnabled,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = (-68).dp, y = 10.dp)
                                .zIndex(8f),
                            onControlHead = controller::controlHead
                        )
                    }
                    RightToolPanel.STANCE -> {
                        StanceControlPanel(
                            currentStance = highLowStance,
                            enabled = inPlaceCommandEnabled,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = (-68).dp, y = 94.dp)
                                .zIndex(8f),
                            onStanceSelected = controller::setHighLowStance
                        )
                    }
                    null -> Unit
                }

                BottomActionGroup(
                    expanded = actionsExpanded,
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
private fun ControlScreenBackground(content: @Composable BoxScope.() -> Unit) {
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
        content()
    }
}

@Composable
private fun TopHud(
    title: String,
    logoPath: String,
    appMode: AppMode,
    connectionState: ConnectionState,
    controlOwnershipState: ControlOwnershipState,
    batteryLevel: Int,
    remoteInputState: RemoteInputRuntimeState,
    onModeClick: (AppMode) -> Unit,
    onControlOwnershipClick: () -> Unit,
    onConnectClick: () -> Unit,
    onBatteryClick: () -> Unit,
    onVideoClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.width(330.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (logoPath.isNotEmpty()) {
                Image(
                    painter = rememberAsyncImagePainter(logoPath),
                    contentDescription = "应用标识",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            RemoteInputIndicator(remoteInputState = remoteInputState)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (connectionState == ConnectionState.CONNECTED) {
                ControlModeToggle(
                    currentMode = appMode,
                    isConnected = controlOwnershipState == ControlOwnershipState.OWNED,
                    onModeClick = onModeClick
                )
            }
            ControlOwnershipButton(
                state = controlOwnershipState,
                isConnected = connectionState == ConnectionState.CONNECTED,
                onClick = onControlOwnershipClick
            )
            ConnectionButton(
                connectionState = connectionState,
                onClick = onConnectClick
            )
            HudIconButton(
                iconResId = R.drawable.genisdog_icon_video,
                contentDescription = "视频",
                onClick = onVideoClick
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
    Surface(
        modifier = modifier
            .height(46.dp)
            .width(148.dp)
            .clickable(onClick = onClick),
        color = Color(0xCC1F2A2B),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp
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
    logoPath: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(240.dp)
            .height(150.dp)
            .border(2.dp, Color.White.copy(alpha = 0.62f), RoundedCornerShape(16.dp)),
        color = Color(0xFF080A0A),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (logoPath.isNotEmpty()) {
                Image(
                    painter = rememberAsyncImagePainter(logoPath),
                    contentDescription = "预览画面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.genisdog_icon_video),
                    contentDescription = "预览画面",
                    modifier = Modifier.size(46.dp)
                )
            }
        }
    }
}

@Composable
private fun RemoteInputPanel(
    remoteInputState: RemoteInputRuntimeState,
    modifier: Modifier = Modifier
) {
    val snapshot = remoteInputState.latestSnapshot
    val intent = snapshot?.movementIntent

    Surface(
        modifier = modifier
            .width(240.dp)
            .height(100.dp),
        color = Color(0xCC151B1B),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Sensors,
                    contentDescription = null,
                    tint = remoteInputState.status.statusColor(),
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = remoteInputState.sourceName.ifEmpty { "外部遥控输入" },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "状态 ${remoteInputState.status.displayName}",
                fontSize = 12.sp,
                color = remoteInputState.status.statusColor()
            )
            Text(
                text = if (intent != null) {
                    "前进 %.2f  平移 %.2f  转向 %.2f".format(
                        intent.forward,
                        intent.strafeRight,
                        intent.yawRight
                    )
                } else {
                    "等待通道帧"
                },
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
        modifier = modifier
            .width(160.dp)
            .height(210.dp)
    ) {
        Box(
            modifier = Modifier
                .width(78.dp)
                .height(46.dp)
                .clickable(onClick = onExpandClick),
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
                .align(Alignment.TopStart)
                .offset(x = 28.dp, y = 62.dp),
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 21.sp,
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
    commandEnabled: Boolean,
    modifier: Modifier = Modifier,
    onPanelToggle: (RightToolPanel) -> Unit
) {
    Column(
        modifier = modifier.width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RightToolButton(
            iconResId = R.drawable.genisdog_icon_light,
            contentDescription = "灯光",
            selected = activePanel == RightToolPanel.LIGHT,
            commandEnabled = commandEnabled,
            onClick = { onPanelToggle(RightToolPanel.LIGHT) }
        )
        RightToolButton(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "头部控制",
            selected = activePanel == RightToolPanel.HEAD,
            commandEnabled = commandEnabled,
            onClick = { onPanelToggle(RightToolPanel.HEAD) }
        )
        RightToolButton(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = "高低站姿",
            selected = activePanel == RightToolPanel.STANCE,
            commandEnabled = commandEnabled,
            onClick = { onPanelToggle(RightToolPanel.STANCE) }
        )
    }
}

@Composable
private fun RightToolButton(
    iconResId: Int? = null,
    imageVector: ImageVector? = null,
    contentDescription: String,
    selected: Boolean,
    commandEnabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = when {
        pressed -> PressedActionBackground
        selected -> Color(0x7A27C7C4)
        else -> PanelBackground
    }

    Surface(
        modifier = Modifier
            .size(52.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .alpha(if (commandEnabled || selected) 1f else 0.54f),
        color = background,
        shape = RoundedCornerShape(15.dp),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (iconResId != null) {
                Image(
                    painter = painterResource(iconResId),
                    contentDescription = contentDescription,
                    modifier = Modifier.size(32.dp),
                    contentScale = ContentScale.Fit
                )
            } else if (imageVector != null) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = contentDescription,
                    tint = if (selected) Color.White else AccentCyan,
                    modifier = Modifier.size(27.dp)
                )
            }
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
                    R.drawable.genisdog_icon_light_front_on
                } else {
                    R.drawable.genisdog_icon_light
                },
                selected = frontLightOn,
                enabled = enabled,
                onClick = onFrontLightClick
            )
            LightToggleRow(
                label = "后灯",
                iconResId = if (backLightOn) {
                    R.drawable.genisdog_icon_light_back_on
                } else {
                    R.drawable.genisdog_icon_light
                },
                selected = backLightOn,
                enabled = enabled,
                onClick = onBackLightClick
            )
            LightToggleRow(
                label = "自动",
                iconResId = if (autoModeLightOn) {
                    R.drawable.genisdog_icon_light_all_on
                } else {
                    R.drawable.genisdog_icon_light_all_off
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
            .alpha(if (enabled) 1f else 0.46f)
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
private fun HeadControlPanel(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onControlHead: (Float, Float) -> Unit
) {
    ToolPanelSurface(modifier = modifier.width(154.dp)) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HeadControlButton(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "抬头",
                enabled = enabled,
                onClick = { onControlHead(0f, HEAD_CONTROL_STEP) }
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeadControlButton(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "左探头",
                    enabled = enabled,
                    onClick = { onControlHead(HEAD_CONTROL_STEP, 0f) }
                )
                HeadControlButton(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "头部停止",
                    enabled = enabled,
                    onClick = { onControlHead(0f, 0f) }
                )
                HeadControlButton(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "右探头",
                    enabled = enabled,
                    onClick = { onControlHead(-HEAD_CONTROL_STEP, 0f) }
                )
            }
            HeadControlButton(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "低头",
                enabled = enabled,
                onClick = { onControlHead(0f, -HEAD_CONTROL_STEP) }
            )
        }
    }
}

@Composable
private fun HeadControlButton(
    imageVector: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Surface(
        modifier = Modifier
            .size(40.dp)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .alpha(if (enabled) 1f else 0.42f),
        color = if (pressed && enabled) PressedActionBackground else Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(11.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(25.dp)
            )
        }
    }
}

@Composable
private fun StanceControlPanel(
    currentStance: HighLowStance,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onStanceSelected: (HighLowStance) -> Unit
) {
    ToolPanelSurface(modifier = modifier.width(160.dp)) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StanceChoiceRow(
                stance = HighLowStance.HIGH,
                icon = Icons.Filled.KeyboardArrowUp,
                selected = currentStance == HighLowStance.HIGH,
                enabled = enabled,
                onClick = { onStanceSelected(HighLowStance.HIGH) }
            )
            StanceChoiceRow(
                stance = HighLowStance.NORMAL,
                icon = Icons.Filled.Refresh,
                selected = currentStance == HighLowStance.NORMAL,
                enabled = enabled,
                onClick = { onStanceSelected(HighLowStance.NORMAL) }
            )
            StanceChoiceRow(
                stance = HighLowStance.LOW,
                icon = Icons.Filled.KeyboardArrowDown,
                selected = currentStance == HighLowStance.LOW,
                enabled = enabled,
                onClick = { onStanceSelected(HighLowStance.LOW) }
            )
        }
    }
}

@Composable
private fun StanceChoiceRow(
    stance: HighLowStance,
    icon: ImageVector,
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
            .alpha(if (enabled) 1f else 0.46f)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stance.displayName,
            tint = if (selected) AccentCyan else Color.White,
            modifier = Modifier.size(23.dp)
        )
        Text(
            text = stance.displayName,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
    commandEnabled: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onActionClick: (RobotAction) -> Unit
) {
    if (!expanded) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        Surface(
            modifier = modifier
                .size(52.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onToggle
                ),
            color = if (pressed) PressedActionBackground else PanelBackground,
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 0.dp,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.genisdog_icon_dog),
                    contentDescription = "展开动作组",
                    modifier = Modifier.size(38.dp),
                    contentScale = ContentScale.Fit
                )
            }
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
        Surface(
            modifier = Modifier
                .size(54.dp)
                .clickable(
                    interactionSource = toggleInteractionSource,
                    indication = null,
                    onClick = onToggle
                ),
            color = if (togglePressed) PressedActionBackground else PanelBackground,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 0.dp,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.genisdog_icon_dog_clicked),
                    contentDescription = "收缩动作组",
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }

        Surface(
            modifier = Modifier
                .height(72.dp)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp)
                ),
            color = PanelBackground,
            shape = RoundedCornerShape(14.dp),
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
            .clickable(onClick = onClick)
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
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Column(
        modifier = Modifier
            .width(66.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (pressed && enabled) PressedActionBackground else Color.Transparent)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .alpha(if (enabled) 1f else 0.46f)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Image(
            painter = painterResource(action.iconResId()),
            contentDescription = action.displayName,
            modifier = Modifier.size(26.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = action.displayName,
            color = Color.White,
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
                .clickable(onClick = onDismiss)
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
                    Surface(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        color = Color.White.copy(alpha = 0.08f),
                        shape = CircleShape,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clickable(onClick = onDismiss),
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
                }

                Spacer(modifier = Modifier.height(26.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
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
                        Spacer(modifier = Modifier.width(18.dp))
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
    Surface(
        modifier = Modifier
            .width(190.dp)
            .height(154.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.48f)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AccentCyan else Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(18.dp)
            ),
        color = if (selected) Color(0xFF203434) else Color(0xFF121818),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
        shadowElevation = if (selected) 8.dp else 2.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
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
            Spacer(modifier = Modifier.height(16.dp))
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
    Surface(
        modifier = Modifier
            .size(46.dp)
            .clickable(enabled = isConnected) { onModeClick(nextMode) }
            .alpha(if (isConnected) 1f else 0.46f),
        color = if (currentMode == AppMode.APP_MODE_MANUAL) {
            Color(0x7A27C7C4)
        } else {
            Color(0x8A17201F)
        },
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.genisdog_icon_robot),
                contentDescription = currentMode.displayName,
                modifier = Modifier.size(28.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun ControlOwnershipButton(
    state: ControlOwnershipState,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    val isPending = state == ControlOwnershipState.TAKING || state == ControlOwnershipState.RELEASING
    val enabled = isConnected && !isPending
    val icon = when (state) {
        ControlOwnershipState.OWNED -> Icons.Filled.Check
        ControlOwnershipState.TAKING,
        ControlOwnershipState.RELEASING -> Icons.Filled.Sync
        ControlOwnershipState.DENIED,
        ControlOwnershipState.LOST -> Icons.Filled.Refresh
        ControlOwnershipState.OCCUPIED -> Icons.Filled.LinkOff
        ControlOwnershipState.UNKNOWN,
        ControlOwnershipState.AVAILABLE -> Icons.Filled.Link
    }
    val text = when (state) {
        ControlOwnershipState.OWNED -> "释放"
        ControlOwnershipState.TAKING -> "接管中"
        ControlOwnershipState.RELEASING -> "释放中"
        ControlOwnershipState.OCCUPIED -> "占用"
        ControlOwnershipState.DENIED -> "重试"
        ControlOwnershipState.LOST -> "重接"
        ControlOwnershipState.UNKNOWN,
        ControlOwnershipState.AVAILABLE -> "接管"
    }

    Surface(
        modifier = Modifier
            .width(74.dp)
            .height(46.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.48f),
        color = if (state == ControlOwnershipState.OWNED) {
            Color(0x7A27C7C4)
        } else {
            PanelBackground
        },
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = state.displayName,
                tint = state.color(),
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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

    Surface(
        modifier = Modifier
            .size(46.dp)
            .clickable(onClick = onClick),
        color = Color(0x8A17201F),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = connectionState.color(),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun HudIconButton(
    iconResId: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(46.dp)
            .clickable(onClick = onClick),
        color = Color(0x8A17201F),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(iconResId),
                contentDescription = contentDescription,
                modifier = Modifier.size(30.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun BatteryIconButton(
    batteryLevel: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(64.dp)
            .height(46.dp)
            .clickable(onClick = onClick),
        color = Color(0x8A17201F),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.genisdog_icon_battery),
                contentDescription = "电量",
                modifier = Modifier.size(22.dp),
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
}

@Composable
private fun RemoteInputIndicator(remoteInputState: RemoteInputRuntimeState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Sensors,
            contentDescription = "遥控输入",
            tint = remoteInputState.status.statusColor(),
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = remoteInputState.status.displayName,
            fontSize = 12.sp,
            color = remoteInputState.status.statusColor(),
            fontWeight = FontWeight.SemiBold
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

private fun SportMode.iconResId(): Int {
    return when (this) {
        SportMode.SPORT_MODE_GENERAL -> R.drawable.genisdog_icon_mode_common
        SportMode.SPORT_MODE_IN_PLACE -> R.drawable.genisdog_icon_mode_in_place
        SportMode.SPORT_MODE_STAIR -> R.drawable.genisdog_icon_mode_stair
        else -> R.drawable.genisdog_icon_mode_common
    }
}

private fun RobotAction.iconResId(): Int {
    return when (this) {
        RobotAction.STAND_UP -> R.drawable.genisdog_icon_stand
        RobotAction.CRAWL -> R.drawable.genisdog_icon_crawl
        RobotAction.LIE_DOWN -> R.drawable.genisdog_icon_lie_down
        RobotAction.GAIT -> R.drawable.genisdog_icon_spinning
        RobotAction.CLIMB -> R.drawable.genisdog_icon_high_platform
        RobotAction.SLIM -> R.drawable.genisdog_icon_slim
        RobotAction.LOCKED -> R.drawable.genisdog_icon_lock
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

private fun ControlOwnershipState.color(): Color {
    return when (this) {
        ControlOwnershipState.OWNED,
        ControlOwnershipState.AVAILABLE -> Color(0xFF1AAE9F)
        ControlOwnershipState.TAKING,
        ControlOwnershipState.RELEASING -> Color(0xFFE2A72E)
        ControlOwnershipState.DENIED,
        ControlOwnershipState.LOST -> Color(0xFFE45D3D)
        ControlOwnershipState.OCCUPIED -> Color(0xFFE2A72E)
        ControlOwnershipState.UNKNOWN -> Color.White.copy(alpha = 0.58f)
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

private val RemoteInputStatus.displayName: String
    get() = when (this) {
        RemoteInputStatus.STOPPED -> "未启动"
        RemoteInputStatus.STARTING -> "启动中"
        RemoteInputStatus.RUNNING -> "接收中"
        RemoteInputStatus.TIMEOUT -> "超时"
        RemoteInputStatus.ERROR -> "异常"
    }

private val AccentCyan = Color(0xFF27C7C4)
private val PanelBackground = Color(0x8A17201F)
private val PressedActionBackground = Color(0x6627C7C4)

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
        onSettingsClick = {},
        onVideoClick = {}
    )
}
