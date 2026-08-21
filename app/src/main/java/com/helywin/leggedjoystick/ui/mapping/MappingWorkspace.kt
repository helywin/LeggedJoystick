package com.helywin.leggedjoystick.ui.mapping

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helywin.leggedjoystick.controller.Controller
import com.helywin.leggedjoystick.controller.ControllerState
import com.helywin.leggedjoystick.ui.components.OperatorAlertDialog
import com.helywin.leggedjoystick.mapping.MappingCoordinates
import com.helywin.leggedjoystick.mapping.MappingClock
import com.helywin.leggedjoystick.mapping.MappingFreshness
import com.helywin.leggedjoystick.mapping.MappingGridFrame
import com.helywin.leggedjoystick.mapping.MappingPose
import com.helywin.leggedjoystick.zmq.RobotControllerConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sar.robot_controller.v1.ActionCode
import sar.robot_controller.v1.ControlOwner
import sar.robot_controller.v1.HealthState
import sar.robot_controller.v1.OperationMode
import sar.robot_controller.v1.PoseSource
import sar.robot_controller.v1.TimeSyncState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun MappingWorkspace(
    state: ControllerState,
    controller: Controller,
    onClose: () -> Unit,
    onSwitchToNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot = state.controllerSnapshot
    val frame = state.mappingFrame
    var nameDialogAction by remember { mutableStateOf<NameDialogAction?>(null) }
    var viewportMode by remember { mutableStateOf(MappingViewportMode.WORKSPACE) }
    val nowMs by produceState(initialValue = MappingClock.elapsedRealtimeMs()) {
        while (true) {
            value = MappingClock.elapsedRealtimeMs()
            delay(1_000L)
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color(0xFF0B1112)) {
        val pose = snapshot?.robot_pose?.let { MappingPose(it.x, it.y, it.yaw) }
        val poseUsable = snapshot?.robot_pose_frame_id == "map" &&
            snapshot.robot_pose_source == PoseSource.POSE_SOURCE_MAPPING_ODOMETRY
        if (viewportMode == MappingViewportMode.FULLSCREEN) {
            MappingCanvas(
                frame = frame,
                pose = pose,
                poseUsable = poseUsable,
                expanded = true,
                onToggleExpanded = { viewportMode = viewportMode.toggled() },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                MappingHeader(state, onClose, onSwitchToNavigation)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MappingCanvas(
                        frame = frame,
                        pose = pose,
                        poseUsable = poseUsable,
                        expanded = false,
                        onToggleExpanded = { viewportMode = viewportMode.toggled() },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    MappingStatusPanel(
                        state = state,
                        nowMs = nowMs,
                        modifier = Modifier.width(210.dp).fillMaxHeight()
                    )
                }
                Spacer(Modifier.height(6.dp))
                MappingActions(
                    state = state,
                    onStart = { nameDialogAction = NameDialogAction.START },
                    onFinish = controller::finishMapping,
                    onSave = { nameDialogAction = NameDialogAction.SAVE },
                    onDiscard = controller::discardMap,
                    onReload = controller::requestLatestMappingMap
                )
            }
        }
    }

    nameDialogAction?.let { action ->
        MapNameDialog(
            action = action,
            onDismiss = { nameDialogAction = null },
            onConfirm = { name ->
                if (action == NameDialogAction.START) {
                    controller.startMapping(name)
                } else {
                    controller.saveMap(name)
                }
                nameDialogAction = null
            }
        )
    }
}

@Composable
private fun MappingHeader(
    state: ControllerState,
    onClose: () -> Unit,
    onSwitchToNavigation: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回遥控主屏",
                tint = Color.White
            )
        }
        Text("实时建图", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(14.dp))
        StatusPill(
            text = state.controllerConnectionState.displayName,
            good = state.controllerConnectionState == RobotControllerConnectionState.CONNECTED
        )
        Spacer(Modifier.width(8.dp))
        StatusPill(
            text = state.connectionState.displayName,
            good = state.isConnected
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = state.controllerSnapshot?.operation_mode?.displayName().orEmpty(),
            color = Color(0xFFB7D7D5),
            fontSize = 14.sp
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onSwitchToNavigation) {
            Text("切换到导航")
        }
    }
}

@Composable
private fun StatusPill(text: String, good: Boolean) {
    Text(
        text = text,
        color = if (good) Color(0xFF79E2D4) else Color(0xFFFFB29E),
        fontSize = 12.sp,
        modifier = Modifier
            .background(
                if (good) Color(0x3327C7C4) else Color(0x33E45D3D),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun MappingCanvas(
    frame: MappingGridFrame?,
    pose: MappingPose?,
    poseUsable: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier
) {
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = frame
    ) {
        value = withContext(Dispatchers.Default) { frame?.toImageBitmap() }
    }
    Box(
        modifier = modifier
            .background(
                Color(0xFF151B1C),
                RoundedCornerShape(if (expanded) 0.dp else 12.dp)
            )
            .border(
                1.dp,
                Color(0xFF314343),
                RoundedCornerShape(if (expanded) 0.dp else 12.dp)
            )
            .clickable(
                onClickLabel = if (expanded) "还原建图工作区" else "全屏查看实时地图",
                onClick = onToggleExpanded
            ),
        contentAlignment = Alignment.Center
    ) {
        val currentImage = image
        if (frame == null || currentImage == null) {
            Text("等待 /map 实时地图…", color = Color.White.copy(alpha = 0.62f))
        } else {
            Canvas(Modifier.fillMaxSize().padding(if (expanded) 4.dp else 12.dp)) {
                val scale = min(size.width / currentImage.width, size.height / currentImage.height)
                val renderedWidth = currentImage.width * scale
                val renderedHeight = currentImage.height * scale
                val left = (size.width - renderedWidth) / 2f
                val top = (size.height - renderedHeight) / 2f
                drawImage(
                    image = currentImage,
                    dstOffset = IntOffset(left.toInt(), top.toInt()),
                    dstSize = IntSize(renderedWidth.toInt(), renderedHeight.toInt())
                )
                drawRect(
                    color = Color(0xFF607978),
                    topLeft = Offset(left, top),
                    size = Size(renderedWidth, renderedHeight),
                    style = Stroke(1f)
                )
                if (poseUsable && pose != null &&
                    pose.x.isFinite() && pose.y.isFinite() && pose.yaw.isFinite()
                ) {
                    drawRobot(frame, pose, left, top, renderedWidth, renderedHeight)
                }
            }
        }
        Text(
            text = if (expanded) "再次点击地图还原" else "点击地图全屏",
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (expanded) 10.dp else 8.dp)
                .background(Color.Black.copy(alpha = 0.56f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

private fun DrawScope.drawRobot(
    frame: MappingGridFrame,
    pose: MappingPose,
    left: Float,
    top: Float,
    renderedWidth: Float,
    renderedHeight: Float
) {
    val normalized = MappingCoordinates.gridToNormalizedScreen(frame.metadata, pose)
    if (normalized.x !in 0.0..1.0 || normalized.y !in 0.0..1.0) return
    val center = Offset(
        left + normalized.x.toFloat() * renderedWidth,
        top + normalized.y.toFloat() * renderedHeight
    )
    val radius = 8.dp.toPx()
    val yaw = MappingCoordinates.screenYaw(frame.metadata, pose.yaw)
    val tip = Offset(
        center.x + cos(yaw).toFloat() * radius * 2.3f,
        center.y + sin(yaw).toFloat() * radius * 2.3f
    )
    drawCircle(Color(0xFF00E5FF), radius + 3.dp.toPx(), center)
    drawCircle(Color(0xFF073D45), radius, center)
    drawLine(Color.White, center, tip, strokeWidth = 3.dp.toPx())
}

private fun MappingGridFrame.toImageBitmap() = Bitmap.createBitmap(
    IntArray(metadata.widthCells * metadata.heightCells) { screenIndex ->
        val screenRow = screenIndex / metadata.widthCells
        val column = screenIndex % metadata.widthCells
        val rosRow = metadata.heightCells - 1 - screenRow
        when (cells[rosRow * metadata.widthCells + column].toInt()) {
            -1 -> 0xFF384143.toInt()
            in 0..19 -> 0xFFF1F4F3.toInt()
            in 20..64 -> 0xFF9EA8A7.toInt()
            else -> 0xFF202728.toInt()
        }
    },
    metadata.widthCells,
    metadata.heightCells,
    Bitmap.Config.ARGB_8888
).asImageBitmap()

@Composable
private fun MappingStatusPanel(state: ControllerState, nowMs: Long, modifier: Modifier) {
    val snapshot = state.controllerSnapshot
    val frame = state.mappingFrame
    val poseUsable = snapshot?.robot_pose_frame_id == "map" &&
        snapshot.robot_pose_source == PoseSource.POSE_SOURCE_MAPPING_ODOMETRY
    val pose = snapshot?.robot_pose?.takeIf { poseUsable }
    val mapAge = frame?.let {
        MappingFreshness.ageMs(nowMs, MappingClock.elapsedRealtimeMs(), it.receivedAtMs)
    }
    val poseAge = snapshot?.robot_pose_source_time_ns
        ?.takeIf { poseUsable }
        ?.takeIf { it > 0L }
        ?.let { (System.currentTimeMillis() * 1_000_000L - it) / 1_000_000L }
    var detailsExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .background(Color(0xFF151B1C), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("建图状态", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (detailsExpanded) "收起" else "详情",
                color = Color(0xFF79E2D4),
                fontSize = 10.sp,
                modifier = Modifier
                    .clickable { detailsExpanded = !detailsExpanded }
                    .padding(horizontal = 5.dp, vertical = 3.dp)
            )
        }
        InfoRow(
            "运行 / 健康",
            "${snapshot?.operation_mode?.displayName() ?: "未知"} / " +
                (snapshot?.health_state?.displayName() ?: "未知")
        )
        InfoRow("控制权", snapshot?.control_owner?.displayName() ?: "未知")
        InfoRow(
            "位置 x / y",
            pose?.let { "%.2f / %.2f m".format(it.x, it.y) } ?: "--"
        )
        InfoRow(
            "朝向",
            pose?.let {
                "%.1f°".format(Math.toDegrees(it.yaw))
            } ?: "--",
            warning = poseAge != null && poseAge > 1_000L
        )
        InfoRow(
            "地图 / 位姿年龄",
            "${mapAge?.formatAge() ?: "--"} / ${poseAge?.formatAge() ?: "--"}",
            warning = mapAge != null && mapAge > 3_000L || poseAge != null && poseAge > 1_000L
        )
        val stream = snapshot?.mapping_stream
        if (detailsExpanded) {
            InfoRow("对时", state.controllerTimeSync?.state?.displayName() ?: "未知")
            InfoRow(
                "地图",
                frame?.let {
                    "#${it.metadata.frameSequence} · ${it.metadata.widthCells}×${it.metadata.heightCells}"
                } ?: "--"
            )
            InfoRow(
                "任务 / 地图流",
                "${state.controllerTask?.state?.name ?: "无"} / " +
                    "拒${stream?.rejected_frames ?: 0} 丢${stream?.dropped_frames ?: 0}"
            )
        }
        val warning = state.mappingError.ifEmpty {
            stream?.last_error.orEmpty().ifEmpty { state.controllerCommandMessage }
        }
        if (warning.isNotEmpty()) {
            Text(
                text = warning,
                color = Color(0xFFFFB29E),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, warning: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
        Text(
            value,
            color = if (warning) Color(0xFFFFB29E) else Color.White.copy(alpha = 0.88f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MappingActions(
    state: ControllerState,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onReload: () -> Unit
) {
    val busy = state.pendingControllerRequestId != 0L
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton("开始建图", state.isActionAllowed(ActionCode.ACTION_CODE_START_MAPPING) && !busy, onStart)
        ActionButton("结束建图", state.isActionAllowed(ActionCode.ACTION_CODE_FINISH_MAPPING) && !busy, onFinish)
        ActionButton("保存地图", state.isActionAllowed(ActionCode.ACTION_CODE_SAVE_MAP) && !busy, onSave)
        ActionButton(
            "放弃草稿",
            state.isActionAllowed(ActionCode.ACTION_CODE_DISCARD_MAP) && !busy,
            onDiscard,
            destructive = true
        )
        OutlinedButton(
            onClick = onReload,
            enabled = state.controllerConnectionState == RobotControllerConnectionState.CONNECTED
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text("刷新地图")
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (destructive) Color(0xFF9F4738) else Color(0xFF197F7C)
        )
    ) { Text(label) }
}

private enum class NameDialogAction { START, SAVE }

@Composable
private fun MapNameDialog(
    action: NameDialogAction,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val prefix = if (action == NameDialogAction.START) "draft" else "map"
    var name by remember {
        mutableStateOf("${prefix}-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}")
    }
    val valid = name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*"))
    OperatorAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (action == NameDialogAction.START) "开始建图" else "保存地图") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.trim() },
                label = { Text(if (action == NameDialogAction.START) "草稿名" else "地图名") },
                supportingText = { Text("仅支持字母、数字、点、下划线和连字符") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, enabled = valid) {
                Text("确认")
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun Long.formatAge(): String = if (this < 0L) "时钟异常" else "%.1f s".format(this / 1000.0)

private fun OperationMode.displayName(): String = when (this) {
    OperationMode.OPERATION_MODE_BOOTING -> "启动中"
    OperationMode.OPERATION_MODE_RECONCILING -> "启动核对"
    OperationMode.OPERATION_MODE_STANDBY -> "待机"
    OperationMode.OPERATION_MODE_MAPPING_PREPARING -> "建图准备"
    OperationMode.OPERATION_MODE_MAPPING_RUNNING -> "建图中"
    OperationMode.OPERATION_MODE_MAPPING_REVIEW -> "建图复核"
    OperationMode.OPERATION_MODE_MAPPING_SAVING -> "地图保存中"
    OperationMode.OPERATION_MODE_LOCALIZATION_LOADING -> "定位加载"
    OperationMode.OPERATION_MODE_LOCALIZATION_WAITING_INITIAL_POSE -> "等待初始位姿"
    OperationMode.OPERATION_MODE_LOCALIZATION_TRACKING -> "定位跟踪"
    OperationMode.OPERATION_MODE_LOCALIZATION_LOST -> "定位丢失"
    OperationMode.OPERATION_MODE_MAINTENANCE -> "工程维护"
    OperationMode.OPERATION_MODE_FAULT -> "故障"
    OperationMode.OPERATION_MODE_UNSPECIFIED -> "未知"
}

private fun ControlOwner.displayName(): String = when (this) {
    ControlOwner.CONTROL_OWNER_REMOTE_MANUAL -> "人工控制"
    ControlOwner.CONTROL_OWNER_NAVIGATION_AUTO -> "自动导航"
    ControlOwner.CONTROL_OWNER_DISABLED -> "已禁用"
    ControlOwner.CONTROL_OWNER_UNSPECIFIED -> "未知"
}

private fun HealthState.displayName(): String = when (this) {
    HealthState.HEALTH_STATE_READY -> "正常"
    HealthState.HEALTH_STATE_HOLD -> "等待"
    HealthState.HEALTH_STATE_LATCHED_FAULT -> "锁存故障"
    HealthState.HEALTH_STATE_UNSPECIFIED -> "未知"
}

private fun TimeSyncState.displayName(): String = when (this) {
    TimeSyncState.TIME_SYNC_STATE_APPLIED -> "已对时"
    TimeSyncState.TIME_SYNC_STATE_DEFERRED -> "已推迟"
    TimeSyncState.TIME_SYNC_STATE_FAILED -> "失败"
    TimeSyncState.TIME_SYNC_STATE_CHALLENGE_SENT -> "对时中"
    TimeSyncState.TIME_SYNC_STATE_REQUIRED -> "待对时"
    TimeSyncState.TIME_SYNC_STATE_UNSPECIFIED -> "未知"
}
