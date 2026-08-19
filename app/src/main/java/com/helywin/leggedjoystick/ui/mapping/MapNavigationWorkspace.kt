package com.helywin.leggedjoystick.ui.mapping

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.helywin.leggedjoystick.controller.Controller
import com.helywin.leggedjoystick.controller.ControllerState
import com.helywin.leggedjoystick.mapping.MapControlOwner
import com.helywin.leggedjoystick.mapping.MapLocalizationStatus
import com.helywin.leggedjoystick.mapping.MapCellClassifier
import com.helywin.leggedjoystick.mapping.MapNavigationCoordinates
import com.helywin.leggedjoystick.mapping.MapViewport
import com.helywin.leggedjoystick.mapping.MappingPose
import com.helywin.leggedjoystick.mapping.SavedMapCoordinates
import com.helywin.leggedjoystick.mapping.SavedMapDescriptor
import com.helywin.leggedjoystick.mapping.ScreenPoint
import com.helywin.leggedjoystick.zmq.RobotControllerConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import legged_driver.AppMode
import sar.robot_controller.v1.ActionCode
import sar.robot_controller.v1.HealthState
import sar.robot_controller.v1.OperationMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private enum class PoseEditMode {
    NONE,
    INITIAL_POSE,
    NAVIGATION_GOAL
}

@Composable
fun MapNavigationWorkspace(
    state: ControllerState,
    controller: Controller,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var fullscreen by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(PoseEditMode.NONE) }
    var interactionMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        controller.refreshSavedMaps()
    }
    LaunchedEffect(state.mapNavigationState.pathPreview) {
        state.mapNavigationState.pathPreview?.let { preview ->
            interactionMessage = "规划预览已更新：%.2f m，%d 个点".format(
                preview.lengthM,
                preview.points.size
            )
        }
    }
    LaunchedEffect(
        state.mapNavigationState.sessionGeneration,
        state.mapNavigationState.initialPoseDraft,
        state.mapNavigationState.targetDraft
    ) {
        if (state.mapNavigationState.initialPoseDraft == null &&
            state.mapNavigationState.targetDraft == null
        ) {
            interactionMessage = ""
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color(0xFF0B1112)) {
        if (fullscreen) {
            SavedMapCanvas(
                state = state,
                controller = controller,
                editMode = editMode,
                expanded = true,
                interactionMessage = interactionMessage,
                onInteractionMessage = { interactionMessage = it },
                onEditFinished = { editMode = PoseEditMode.NONE },
                onToggleFullscreen = {
                    fullscreen = false
                    if (editMode != PoseEditMode.NONE) {
                        editMode = PoseEditMode.NONE
                        interactionMessage = "已取消本次位姿编辑"
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(Modifier.fillMaxSize().padding(10.dp)) {
                NavigationHeader(state, controller, onClose)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SavedMapList(
                        state = state,
                        controller = controller,
                        modifier = Modifier.width(150.dp).fillMaxHeight()
                    )
                    SavedMapCanvas(
                        state = state,
                        controller = controller,
                        editMode = editMode,
                        expanded = false,
                        interactionMessage = interactionMessage,
                        onInteractionMessage = { interactionMessage = it },
                        onEditFinished = { editMode = PoseEditMode.NONE },
                        onToggleFullscreen = { fullscreen = true },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    NavigationStatus(
                        state = state,
                        modifier = Modifier.width(160.dp).fillMaxHeight()
                    )
                }
                Spacer(Modifier.height(6.dp))
                NavigationActions(
                    state = state,
                    controller = controller,
                    editMode = editMode,
                    onEditMode = {
                        editMode = it
                        if (it != PoseEditMode.NONE) {
                            fullscreen = true
                            interactionMessage = "在全屏地图内按下确定位置，拖动设置角度"
                        } else {
                            interactionMessage = ""
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun NavigationHeader(
    state: ControllerState,
    controller: Controller,
    onClose: () -> Unit
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
        Text("地图定位与导航", color = Color.White, fontSize = 19.sp)
        Spacer(Modifier.width(12.dp))
        StatusPill(
            state.controllerConnectionState.displayName,
            state.controllerConnectionState == RobotControllerConnectionState.CONNECTED
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = controller::refreshSavedMaps) {
            Icon(Icons.Default.Refresh, contentDescription = "刷新地图列表", tint = Color.White)
        }
    }
}

@Composable
private fun SavedMapList(
    state: ControllerState,
    controller: Controller,
    modifier: Modifier
) {
    val navigation = state.mapNavigationState
    Column(
        modifier = modifier
            .background(Color(0xFF151B1C), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF314343), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text("已保存地图", color = Color.White, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        if (navigation.maps.isEmpty()) {
            Text("暂无合法地图，或列表尚未返回", color = Color(0xFF9FB2B0), fontSize = 13.sp)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(navigation.maps, key = { "${it.identity.mapId}:${it.identity.revision}" }) { map ->
                    MapListItem(
                        map = map,
                        selected = navigation.selectedMap == map.identity,
                        current = navigation.currentMap == map.identity,
                        onClick = {
                            controller.selectSavedMap(map.identity.mapId, map.identity.revision)
                        }
                    )
                }
            }
        }
        val selected = navigation.selectedMap
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = navigation.canLoadSelectedMap(
                state.isActionAllowed(ActionCode.ACTION_CODE_SWITCH_MAP)
            ),
            onClick = {
                selected?.let { controller.switchSavedMap(it.mapId, it.revision) }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("加载选中地图")
        }
        OutlinedButton(
            enabled = state.isActionAllowed(ActionCode.ACTION_CODE_STOP_RUNTIME),
            onClick = controller::stopLocalizationRuntime,
            modifier = Modifier.fillMaxWidth(),
            colors = navigationOutlinedButtonColors()
        ) {
            Text("停止定位运行时")
        }
    }
}

@Composable
private fun MapListItem(
    map: SavedMapDescriptor,
    selected: Boolean,
    current: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) Color(0xFF244846) else Color(0xFF20292A),
                RoundedCornerShape(9.dp)
            )
            .border(
                1.dp,
                if (current) Color(0xFF58D8CC) else Color.Transparent,
                RoundedCornerShape(9.dp)
            )
            .clickable(onClick = onClick)
            .padding(9.dp)
    ) {
        Text(
            if (current) "${map.displayName} · 当前" else map.displayName,
            color = Color.White,
            fontSize = 14.sp
        )
        Text(
            "rev ${map.identity.revision} · ${map.coordinates.widthCells}×${map.coordinates.heightCells}",
            color = Color(0xFFAFC4C2),
            fontSize = 11.sp
        )
        if (map.createdUtcNs > 0L) {
            Text(
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                    .format(Date(map.createdUtcNs / 1_000_000L)),
                color = Color(0xFF829795),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun SavedMapCanvas(
    state: ControllerState,
    controller: Controller,
    editMode: PoseEditMode,
    expanded: Boolean,
    interactionMessage: String,
    onInteractionMessage: (String) -> Unit,
    onEditFinished: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier
) {
    val navigation = state.mapNavigationState
    val descriptor = navigation.maps.firstOrNull { it.identity == navigation.selectedMap }
    val preview = state.savedMapPreview?.takeIf { it.key.map == descriptor?.identity }
    val bitmap by produceState<Bitmap?>(null, preview) {
        value = withContext(Dispatchers.Default) {
            preview?.bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
    }
    var dragStart by remember { mutableStateOf<ScreenPoint?>(null) }
    var dragEnd by remember { mutableStateOf<ScreenPoint?>(null) }

    Box(
        modifier = modifier
            .background(Color(0xFF151B1C), RoundedCornerShape(if (expanded) 0.dp else 12.dp))
            .border(
                1.dp,
                Color(0xFF314343),
                RoundedCornerShape(if (expanded) 0.dp else 12.dp)
            )
            .pointerInput(bitmap, descriptor, editMode, expanded) {
                if (bitmap == null || descriptor == null || editMode == PoseEditMode.NONE ||
                    !expanded
                ) {
                    return@pointerInput
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val point = ScreenPoint(down.position.x.toDouble(), down.position.y.toDouble())
                    val viewport = MapViewport(size.width.toDouble(), size.height.toDouble())
                    val pixel = MapNavigationCoordinates.screenToImagePixel(
                        descriptor.coordinates,
                        viewport,
                        point
                    )
                    val allowUnknown = editMode == PoseEditMode.INITIAL_POSE
                    if (pixel == null || bitmap!!.isUnavailableForPose(
                            pixel.first,
                            pixel.second,
                            allowUnknown
                        )
                    ) {
                        dragStart = null
                        dragEnd = null
                        onInteractionMessage(
                            if (allowUnknown) {
                                "不能在地图外或占用区域选择初始位姿"
                            } else {
                                "不能在地图外、未知区域或占用区域选择导航目标"
                            }
                        )
                    } else {
                        dragStart = point
                        dragEnd = point
                        val position = MapNavigationCoordinates.screenToWorld(
                            descriptor.coordinates,
                            viewport,
                            point
                        )
                        onInteractionMessage(
                            position?.let {
                                "位置 x=%.2f m，y=%.2f m；拖动设置角度".format(it.x, it.y)
                            } ?: "拖动确定朝向，松开完成"
                        )
                    }

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (dragStart != null) {
                            val next = ScreenPoint(
                                change.position.x.toDouble(),
                                change.position.y.toDouble()
                            )
                            dragEnd = next
                            val position = MapNavigationCoordinates.screenToWorld(
                                descriptor.coordinates,
                                viewport,
                                dragStart!!
                            )
                            val yaw = MapNavigationCoordinates.dragToWorldYaw(
                                descriptor.coordinates,
                                dragStart!!,
                                next
                            )
                            if (position != null && yaw != null) {
                                onInteractionMessage(
                                    "x=%.2f m · y=%.2f m · yaw=%.1f°".format(
                                        position.x,
                                        position.y,
                                        Math.toDegrees(yaw)
                                    )
                                )
                            }
                            if (change.pressed) {
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    val start = dragStart
                    val end = dragEnd
                    val position = start?.let {
                        MapNavigationCoordinates.screenToWorld(descriptor.coordinates, viewport, it)
                    }
                    val yaw = if (start != null && end != null) {
                        MapNavigationCoordinates.dragToWorldYaw(descriptor.coordinates, start, end)
                    } else {
                        null
                    }
                    if (start == null) {
                        // 起点被地图边界或栅格状态拒绝时，保留拒绝原因。
                    } else if (position == null || yaw == null) {
                        onInteractionMessage("拖动距离过短，请重新选择位置和朝向")
                    } else {
                        val pose = position.copy(yaw = yaw)
                        val message = "x=%.2f m · y=%.2f m · yaw=%.1f°；右上角还原后确认".format(
                            pose.x,
                            pose.y,
                            Math.toDegrees(pose.yaw)
                        )
                        if (editMode == PoseEditMode.INITIAL_POSE) {
                            controller.editInitialPose(pose)
                            onInteractionMessage("初始位姿：$message")
                        } else {
                            controller.editNavigationTarget(pose)
                            onInteractionMessage("导航目标：$message")
                        }
                        onEditFinished()
                    }
                    dragStart = null
                    dragEnd = null
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitmap
        if (descriptor == null) {
            Text("请选择一张地图", color = Color(0xFFAFC4C2))
        } else if (currentBitmap == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("正在获取并校验地图预览…", color = Color(0xFFAFC4C2))
                OutlinedButton(
                    onClick = {
                        controller.requestSavedMapPreview(
                            descriptor.identity.mapId,
                            descriptor.identity.revision
                        )
                    },
                    colors = navigationOutlinedButtonColors()
                ) {
                    Text("重试")
                }
            }
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val scale = min(size.width / currentBitmap.width, size.height / currentBitmap.height)
                val renderedWidth = currentBitmap.width * scale
                val renderedHeight = currentBitmap.height * scale
                val left = (size.width - renderedWidth) / 2f
                val top = (size.height - renderedHeight) / 2f
                drawImage(
                    currentBitmap.asImageBitmap(),
                    dstOffset = IntOffset(left.toInt(), top.toInt()),
                    dstSize = IntSize(renderedWidth.toInt(), renderedHeight.toInt())
                )
                drawRect(
                    Color(0xFF607978),
                    topLeft = Offset(left, top),
                    size = Size(renderedWidth, renderedHeight),
                    style = Stroke(1f)
                )
                val viewport = MapViewport(size.width.toDouble(), size.height.toDouble())
                navigation.pathPreview?.points?.zipWithNext()?.forEach { (start, end) ->
                    drawLine(
                        color = Color(0xFF4CE8D3),
                        start = start.toOffset(descriptor.coordinates, viewport),
                        end = end.toOffset(descriptor.coordinates, viewport),
                        strokeWidth = 4f
                    )
                }
                navigation.initialPoseDraft?.let {
                    drawPose(it, descriptor.coordinates, viewport, Color(0xFFFFC857))
                }
                navigation.targetDraft?.let {
                    drawPose(it, descriptor.coordinates, viewport, Color(0xFFFF6B6B))
                }
                navigation.robotPose?.let {
                    drawPose(it, descriptor.coordinates, viewport, Color(0xFF00E5FF))
                }
                val start = dragStart
                val end = dragEnd
                val dragPosition = start?.let {
                    MapNavigationCoordinates.screenToWorld(descriptor.coordinates, viewport, it)
                }
                val dragYaw = if (start != null && end != null) {
                    MapNavigationCoordinates.dragToWorldYaw(descriptor.coordinates, start, end)
                } else {
                    null
                }
                if (dragPosition != null && dragYaw != null) {
                    drawPose(
                        dragPosition.copy(yaw = dragYaw),
                        descriptor.coordinates,
                        viewport,
                        if (editMode == PoseEditMode.INITIAL_POSE) {
                            Color(0xFFFFC857)
                        } else {
                            Color(0xFFFF6B6B)
                        }
                    )
                }
            }
        }

        IconButton(
            onClick = onToggleFullscreen,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
        ) {
            Icon(
                if (expanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = if (expanded) "还原地图" else "全屏地图",
                tint = Color.White
            )
        }
        val message = state.mapNavigationError.ifEmpty { interactionMessage }
        if (message.isNotEmpty()) {
            Text(
                message,
                color = if (state.mapNavigationError.isEmpty()) Color.White else Color(0xFFFFB29E),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun NavigationStatus(state: ControllerState, modifier: Modifier) {
    val navigation = state.mapNavigationState
    val snapshot = state.controllerSnapshot
    Column(
        modifier = modifier
            .background(Color(0xFF151B1C), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("权威状态", color = Color.White, fontSize = 15.sp)
        InfoRow("运行模式", snapshot?.operation_mode?.displayName() ?: "未知")
        InfoRow("定位", navigation.localizationStatus.displayName())
        navigation.robotPose?.let {
            InfoRow("位置", "%.2f / %.2f / %.2f".format(it.x, it.y, it.yaw))
        }
        InfoRow("控制权", navigation.controlOwner.displayName())
        InfoRow("健康", snapshot?.health_state?.displayName() ?: "未知")
        if (navigation.activeTaskId != null) {
            InfoRow("任务", navigation.activeTaskId)
        } else if (navigation.pathPreview != null) {
            InfoRow(
                "规划",
                "%.2f m · %d 点".format(
                    navigation.pathPreview.lengthM,
                    navigation.pathPreview.points.size
                )
            )
        } else {
            InfoRow("任务", "无")
        }
        val blockers = snapshot?.allowed_actions
            ?.flatMap { action -> action.blocking_reasons }
            ?.distinct()
            .orEmpty()
        if (blockers.isNotEmpty()) {
            Text("阻断原因", color = Color(0xFFFFB29E), fontSize = 13.sp)
            blockers.take(5).forEach { Text("· $it", color = Color(0xFFD7A89D), fontSize = 11.sp) }
        }
    }
}

@Composable
private fun NavigationActions(
    state: ControllerState,
    controller: Controller,
    editMode: PoseEditMode,
    onEditMode: (PoseEditMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = {
                onEditMode(
                    if (editMode == PoseEditMode.INITIAL_POSE) PoseEditMode.NONE
                    else PoseEditMode.INITIAL_POSE
                )
            },
            enabled = state.isActionAllowed(ActionCode.ACTION_CODE_SET_INITIAL_POSE),
            colors = navigationOutlinedButtonColors()
        ) { Text("选择初始位姿") }
        Button(
            onClick = controller::submitInitialPose,
            enabled = state.mapNavigationState.initialPoseDraft != null &&
                state.isActionAllowed(ActionCode.ACTION_CODE_SET_INITIAL_POSE)
        ) { Text("发送初始位姿") }
        OutlinedButton(
            onClick = {
                onEditMode(
                    if (editMode == PoseEditMode.NAVIGATION_GOAL) PoseEditMode.NONE
                    else PoseEditMode.NAVIGATION_GOAL
                )
            },
            enabled = state.isActionAllowed(ActionCode.ACTION_CODE_PREVIEW_GOAL),
            colors = navigationOutlinedButtonColors()
        ) { Text("选择导航目标") }
        Button(
            onClick = controller::requestNavigationPreview,
            enabled = state.mapNavigationState.targetDraft != null &&
                state.isActionAllowed(ActionCode.ACTION_CODE_PREVIEW_GOAL)
        ) { Text("规划预览") }
        Button(
            onClick = controller::startNavigation,
            enabled = state.mapNavigationState.pathPreview != null &&
                state.isActionAllowed(ActionCode.ACTION_CODE_START_NAVIGATION)
        ) { Text("开始导航") }
        OutlinedButton(
            onClick = controller::cancelNavigation,
            enabled = state.isActionAllowed(ActionCode.ACTION_CODE_CANCEL_NAVIGATION),
            colors = navigationOutlinedButtonColors()
        ) { Text("取消导航") }
        Spacer(Modifier.weight(1f))
        Button(onClick = { controller.setMode(AppMode.APP_MODE_MANUAL) }) {
            Text("人工接管")
        }
    }
}

private fun MappingPose.toOffset(
    coordinates: SavedMapCoordinates,
    viewport: MapViewport
): Offset {
    val point = MapNavigationCoordinates.worldToScreen(coordinates, viewport, this)
    return Offset(point.x.toFloat(), point.y.toFloat())
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPose(
    pose: MappingPose,
    coordinates: SavedMapCoordinates,
    viewport: MapViewport,
    color: Color
) {
    val center = pose.toOffset(coordinates, viewport)
    val radius = 8.dp.toPx()
    val tip = Offset(
        center.x + cos(-pose.yaw + coordinates.origin.yaw).toFloat() * radius * 2.3f,
        center.y + sin(-pose.yaw + coordinates.origin.yaw).toFloat() * radius * 2.3f
    )
    drawCircle(color, radius, center)
    drawLine(Color.White, center, tip, strokeWidth = 3.dp.toPx())
}

private fun Bitmap.isUnavailableForPose(x: Int, y: Int, allowUnknown: Boolean): Boolean {
    if (x !in 0 until width || y !in 0 until height) return true
    val pixel = getPixel(x, y)
    val red = android.graphics.Color.red(pixel)
    val green = android.graphics.Color.green(pixel)
    val blue = android.graphics.Color.blue(pixel)
    return MapCellClassifier.isUnavailable(red, green, blue, allowUnknown)
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
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF8DA3A1), fontSize = 10.sp)
        Text(
            text = value,
            color = Color(0xFFD8E5E3),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun navigationOutlinedButtonColors() = ButtonDefaults.outlinedButtonColors(
    contentColor = Color(0xFFB9F3EA),
    disabledContentColor = Color(0xFF4F5B5A)
)

private fun OperationMode.displayName(): String {
    return when (this) {
        OperationMode.OPERATION_MODE_BOOTING -> "启动中"
        OperationMode.OPERATION_MODE_RECONCILING -> "状态核对"
        OperationMode.OPERATION_MODE_STANDBY -> "待机"
        OperationMode.OPERATION_MODE_MAPPING_PREPARING -> "建图准备"
        OperationMode.OPERATION_MODE_MAPPING_RUNNING -> "建图中"
        OperationMode.OPERATION_MODE_MAPPING_REVIEW -> "建图复核"
        OperationMode.OPERATION_MODE_MAPPING_SAVING -> "保存地图"
        OperationMode.OPERATION_MODE_LOCALIZATION_LOADING -> "加载地图"
        OperationMode.OPERATION_MODE_LOCALIZATION_WAITING_INITIAL_POSE -> "等待初始位姿"
        OperationMode.OPERATION_MODE_LOCALIZATION_TRACKING -> "定位跟踪"
        OperationMode.OPERATION_MODE_LOCALIZATION_LOST -> "定位丢失"
        OperationMode.OPERATION_MODE_MAINTENANCE -> "维护"
        OperationMode.OPERATION_MODE_FAULT -> "故障"
        else -> "未知"
    }
}

private fun MapLocalizationStatus.displayName(): String {
    return when (this) {
        MapLocalizationStatus.INITIALIZING -> "初始化中"
        MapLocalizationStatus.TRACKING -> "跟踪正常"
        MapLocalizationStatus.LOST -> "已丢失"
        MapLocalizationStatus.UNKNOWN -> "未知"
    }
}

private fun MapControlOwner.displayName(): String {
    return when (this) {
        MapControlOwner.REMOTE_MANUAL -> "人工控制"
        MapControlOwner.NAVIGATION_AUTO -> "自动导航"
        MapControlOwner.DISABLED -> "未启用"
    }
}

private fun HealthState.displayName(): String {
    return when (this) {
        HealthState.HEALTH_STATE_READY -> "正常"
        HealthState.HEALTH_STATE_HOLD -> "暂停"
        HealthState.HEALTH_STATE_LATCHED_FAULT -> "锁存故障"
        else -> "未知"
    }
}
