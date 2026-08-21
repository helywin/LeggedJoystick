package com.helywin.leggedjoystick.ui.mapping

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontWeight
import com.helywin.leggedjoystick.controller.Controller
import com.helywin.leggedjoystick.controller.ControllerState
import com.helywin.leggedjoystick.mapping.MapControlOwner
import com.helywin.leggedjoystick.mapping.MapCellClassifier
import com.helywin.leggedjoystick.mapping.MapNavigationCoordinates
import com.helywin.leggedjoystick.mapping.MapViewport
import com.helywin.leggedjoystick.mapping.MapViewTransform
import com.helywin.leggedjoystick.mapping.MappingPose
import com.helywin.leggedjoystick.mapping.SavedMapCoordinates
import com.helywin.leggedjoystick.mapping.SavedMapDescriptor
import com.helywin.leggedjoystick.mapping.ScreenPoint
import com.helywin.leggedjoystick.ui.components.OperatorAlertDialog
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

private enum class NavigationConfirmation {
    LOAD_MAP,
    SUBMIT_INITIAL_POSE,
    STOP_RUNTIME,
    START_NAVIGATION
}

@Composable
fun MapNavigationWorkspace(
    state: ControllerState,
    controller: Controller,
    onClose: () -> Unit,
    onSwitchToMapping: () -> Unit,
    modifier: Modifier = Modifier
) {
    var fullscreen by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(PoseEditMode.NONE) }
    var editDraft by remember { mutableStateOf<MappingPose?>(null) }
    var mapViewTransform by remember { mutableStateOf(MapViewTransform.IDENTITY) }
    var interactionMessage by remember { mutableStateOf("") }
    var mapDialogVisible by remember { mutableStateOf(false) }
    var statusDialogVisible by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<NavigationConfirmation?>(null) }

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
    LaunchedEffect(state.mapNavigationState.selectedMap) {
        mapViewTransform = MapViewTransform.IDENTITY
        editDraft = null
        editMode = PoseEditMode.NONE
        fullscreen = false
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color(0xFF0B1112)) {
        if (fullscreen) {
            SavedMapCanvas(
                state = state,
                controller = controller,
                editMode = editMode,
                editDraft = editDraft,
                viewTransform = mapViewTransform,
                expanded = true,
                interactionMessage = interactionMessage,
                onInteractionMessage = { interactionMessage = it },
                onEditDraftChange = { editDraft = it },
                onViewTransformChange = { mapViewTransform = it },
                onResetView = { mapViewTransform = MapViewTransform.IDENTITY },
                onCommitEdit = {
                    val pose = editDraft ?: return@SavedMapCanvas
                    if (editMode == PoseEditMode.INITIAL_POSE) {
                        controller.editInitialPose(pose)
                        interactionMessage = "初始位姿已暂存，确认后发送"
                    } else {
                        controller.editNavigationTarget(pose)
                        interactionMessage = "导航目标已暂存，请先规划路线"
                    }
                    editDraft = null
                    editMode = PoseEditMode.NONE
                    fullscreen = false
                },
                onCancelEdit = {
                    editDraft = null
                    editMode = PoseEditMode.NONE
                    fullscreen = false
                    interactionMessage = "已取消本次位姿编辑"
                },
                onOpenMaps = { mapDialogVisible = true },
                onToggleFullscreen = {
                    fullscreen = false
                    if (editMode != PoseEditMode.NONE) {
                        editMode = PoseEditMode.NONE
                        editDraft = null
                        interactionMessage = "已取消本次位姿编辑"
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(Modifier.fillMaxSize().padding(10.dp)) {
                NavigationHeader(
                    state = state,
                    onClose = onClose,
                    onSwitchToMapping = onSwitchToMapping,
                    onOpenMaps = { mapDialogVisible = true },
                    onOpenStatus = { statusDialogVisible = true }
                )
                Spacer(Modifier.height(6.dp))
                SavedMapCanvas(
                    state = state,
                    controller = controller,
                    editMode = PoseEditMode.NONE,
                    editDraft = null,
                    viewTransform = mapViewTransform,
                    expanded = false,
                    interactionMessage = interactionMessage,
                    onInteractionMessage = { interactionMessage = it },
                    onEditDraftChange = {},
                    onViewTransformChange = { mapViewTransform = it },
                    onResetView = { mapViewTransform = MapViewTransform.IDENTITY },
                    onCommitEdit = {},
                    onCancelEdit = {},
                    onOpenMaps = { mapDialogVisible = true },
                    onToggleFullscreen = { fullscreen = true },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                NavigationActions(
                    state = state,
                    controller = controller,
                    onOpenMaps = { mapDialogVisible = true },
                    onEditMode = {
                        editMode = it
                        editDraft = null
                        if (it != PoseEditMode.NONE) {
                            fullscreen = true
                            interactionMessage =
                                "双指可缩放地图；单指按下确定位置，拖动设置角度"
                        } else {
                            interactionMessage = ""
                        }
                    },
                    onConfirm = { confirmation = it }
                )
            }
        }
    }

    if (mapDialogVisible) {
        MapManagementDialog(
            state = state,
            controller = controller,
            onDismiss = { mapDialogVisible = false },
            onLoadMap = {
                mapDialogVisible = false
                confirmation = NavigationConfirmation.LOAD_MAP
            },
            onStopRuntime = {
                mapDialogVisible = false
                confirmation = NavigationConfirmation.STOP_RUNTIME
            }
        )
    }
    if (statusDialogVisible) {
        NavigationStatusDialog(
            state = state,
            onDismiss = { statusDialogVisible = false }
        )
    }
    confirmation?.let { action ->
        NavigationConfirmationDialog(
            action = action,
            state = state,
            onDismiss = { confirmation = null },
            onConfirm = {
                when (action) {
                    NavigationConfirmation.LOAD_MAP -> {
                        state.mapNavigationState.selectedMap?.let {
                            controller.switchSavedMap(it.mapId, it.revision)
                        }
                    }
                    NavigationConfirmation.SUBMIT_INITIAL_POSE -> {
                        controller.submitInitialPose()
                    }
                    NavigationConfirmation.STOP_RUNTIME -> {
                        controller.stopLocalizationRuntime()
                    }
                    NavigationConfirmation.START_NAVIGATION -> {
                        controller.startNavigation()
                    }
                }
                confirmation = null
            }
        )
    }
}

@Composable
private fun NavigationHeader(
    state: ControllerState,
    onClose: () -> Unit,
    onSwitchToMapping: () -> Unit,
    onOpenMaps: () -> Unit,
    onOpenStatus: () -> Unit
) {
    val navigation = state.mapNavigationState
    val presentation = NavigationWorkflowPresenter.present(
        state.controllerSnapshot,
        navigation
    )
    val selected = navigation.maps.firstOrNull { it.identity == navigation.selectedMap }
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回遥控主屏",
                tint = Color.White
            )
        }
        Column {
            Text("定位与导航", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                selected?.let { "${it.displayName} · rev ${it.identity.revision}" } ?: "未选择地图",
                color = Color(0xFF91A7A5),
                fontSize = 10.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(14.dp))
        StatusPill(
            state.controllerConnectionState.displayName,
            if (state.controllerConnectionState == RobotControllerConnectionState.CONNECTED) {
                NavigationStatusTone.GOOD
            } else {
                NavigationStatusTone.WARNING
            }
        )
        Spacer(Modifier.width(6.dp))
        StatusPill(
            presentation.localizationLabel,
            presentation.localizationTone
        )
        Spacer(Modifier.width(6.dp))
        StatusPill(
            navigation.controlOwner.displayName(),
            if (navigation.controlOwner == MapControlOwner.DISABLED) {
                NavigationStatusTone.WARNING
            } else {
                NavigationStatusTone.GOOD
            }
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onSwitchToMapping) {
            Text("切换到建图")
        }
        TextButton(onClick = onOpenMaps) {
            Icon(Icons.Default.Map, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("地图")
        }
        TextButton(onClick = onOpenStatus) {
            Icon(Icons.Default.Info, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("详情")
        }
    }
}

@Composable
private fun MapManagementDialog(
    state: ControllerState,
    controller: Controller,
    onDismiss: () -> Unit,
    onLoadMap: () -> Unit,
    onStopRuntime: () -> Unit
) {
    val navigation = state.mapNavigationState
    val canLoad = navigation.canLoadSelectedMap(
        state.isActionAllowed(ActionCode.ACTION_CODE_SWITCH_MAP)
    )
    OperatorAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("地图与定位", modifier = Modifier.weight(1f))
                IconButton(onClick = controller::refreshSavedMaps) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新地图列表")
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 330.dp)) {
                if (navigation.maps.isEmpty()) {
                    Text(
                        "暂无合法地图，或列表尚未返回",
                        color = Color(0xFF9FB2B0),
                        fontSize = 13.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 270.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(
                            navigation.maps,
                            key = { "${it.identity.mapId}:${it.identity.revision}" }
                        ) { map ->
                            MapListItem(
                                map = map,
                                selected = navigation.selectedMap == map.identity,
                                current = navigation.currentMap == map.identity,
                                onClick = {
                                    controller.selectSavedMap(
                                        map.identity.mapId,
                                        map.identity.revision
                                    )
                                }
                            )
                        }
                    }
                }
                if (state.isActionAllowed(ActionCode.ACTION_CODE_STOP_RUNTIME)) {
                    TextButton(
                        onClick = onStopRuntime,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("停止定位运行时", color = Color(0xFFFF9D88))
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = canLoad, onClick = onLoadMap) {
                Text("加载选中地图")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun MapListItem(
    map: SavedMapDescriptor,
    selected: Boolean,
    current: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(9.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) colors.primaryContainer else colors.surfaceContainerHighest,
                shape
            )
            .border(
                1.dp,
                if (current || selected) colors.primary else colors.outlineVariant,
                shape
            )
            .clickable(onClick = onClick)
            .padding(9.dp)
    ) {
        Text(
            if (current) "${map.displayName} · 当前" else map.displayName,
            color = colors.onSurface,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            "rev ${map.identity.revision} · ${map.coordinates.widthCells}×${map.coordinates.heightCells}",
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        if (map.createdUtcNs > 0L) {
            Text(
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                    .format(Date(map.createdUtcNs / 1_000_000L)),
                color = colors.onSurfaceVariant.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SavedMapCanvas(
    state: ControllerState,
    controller: Controller,
    editMode: PoseEditMode,
    editDraft: MappingPose?,
    viewTransform: MapViewTransform,
    expanded: Boolean,
    interactionMessage: String,
    onInteractionMessage: (String) -> Unit,
    onEditDraftChange: (MappingPose) -> Unit,
    onViewTransformChange: (MapViewTransform) -> Unit,
    onResetView: () -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onOpenMaps: () -> Unit,
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
    val latestViewTransform by rememberUpdatedState(viewTransform)
    val viewportShape = RoundedCornerShape(if (expanded) 0.dp else 12.dp)

    Box(
        modifier = modifier
            .clip(viewportShape)
            .background(Color(0xFF151B1C), viewportShape)
            .border(
                1.dp,
                Color(0xFF314343),
                viewportShape
            )
            .pointerInput(bitmap, descriptor, editMode, expanded) {
                if (bitmap == null || descriptor == null) {
                    return@pointerInput
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val viewport = MapViewport(size.width.toDouble(), size.height.toDouble())
                    var gestureTransform = latestViewTransform
                    var transformingView = editMode == PoseEditMode.NONE
                    val displayPoint = ScreenPoint(
                        down.position.x.toDouble(),
                        down.position.y.toDouble()
                    )
                    val point = gestureTransform.invert(viewport, displayPoint)
                    val allowUnknown = editMode == PoseEditMode.INITIAL_POSE
                    if (editMode != PoseEditMode.NONE && expanded) {
                        val pixel = MapNavigationCoordinates.screenToImagePixel(
                            descriptor.coordinates,
                            viewport,
                            point
                        )
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
                    }

                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            transformingView = true
                            dragStart = null
                            dragEnd = null
                            onInteractionMessage("双指缩放或移动地图，右上角可还原")
                        }
                        if (transformingView && pressedCount > 0) {
                            val centroid = event.calculateCentroid()
                            val pan = event.calculatePan()
                            gestureTransform = gestureTransform.update(
                                viewport = viewport,
                                centroid = ScreenPoint(
                                    centroid.x.toDouble(),
                                    centroid.y.toDouble()
                                ),
                                panX = pan.x.toDouble(),
                                panY = pan.y.toDouble(),
                                zoomFactor = event.calculateZoom().toDouble()
                            )
                            onViewTransformChange(gestureTransform)
                            event.changes.forEach { it.consume() }
                        } else if (dragStart != null) {
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change != null) {
                                val next = gestureTransform.invert(
                                    viewport,
                                    ScreenPoint(
                                        change.position.x.toDouble(),
                                        change.position.y.toDouble()
                                    )
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
                                    next,
                                    minimumDragPx = 8.0 / gestureTransform.scale
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
                        }
                    } while (event.changes.any { it.pressed })

                    val start = dragStart
                    val end = dragEnd
                    val position = start?.let {
                        MapNavigationCoordinates.screenToWorld(descriptor.coordinates, viewport, it)
                    }
                    val yaw = if (start != null && end != null) {
                        MapNavigationCoordinates.dragToWorldYaw(
                            descriptor.coordinates,
                            start,
                            end,
                            minimumDragPx = 8.0 / gestureTransform.scale
                        )
                    } else {
                        null
                    }
                    if (transformingView || start == null) {
                        // 起点被地图边界或栅格状态拒绝时，保留拒绝原因。
                    } else if (position == null || yaw == null) {
                        onInteractionMessage("拖动距离过短，请重新选择位置和朝向")
                    } else {
                        val pose = position.copy(yaw = yaw)
                        val message = "x=%.2f m · y=%.2f m · yaw=%.1f°；点击使用此位姿".format(
                            pose.x,
                            pose.y,
                            Math.toDegrees(pose.yaw)
                        )
                        onEditDraftChange(pose)
                        if (editMode == PoseEditMode.INITIAL_POSE) {
                            onInteractionMessage("初始位姿：$message")
                        } else {
                            onInteractionMessage("导航目标：$message")
                        }
                    }
                    dragStart = null
                    dragEnd = null
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitmap
        if (descriptor == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("尚未选择地图", color = Color(0xFFAFC4C2))
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenMaps) { Text("选择地图") }
            }
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
                val fitScale = min(
                    size.width / currentBitmap.width,
                    size.height / currentBitmap.height
                )
                val baseWidth = currentBitmap.width * fitScale
                val baseHeight = currentBitmap.height * fitScale
                val baseLeft = (size.width - baseWidth) / 2f
                val baseTop = (size.height - baseHeight) / 2f
                val viewport = MapViewport(size.width.toDouble(), size.height.toDouble())
                val transformedTopLeft = viewTransform.apply(
                    viewport,
                    ScreenPoint(baseLeft.toDouble(), baseTop.toDouble())
                )
                val renderedWidth = baseWidth * viewTransform.scale.toFloat()
                val renderedHeight = baseHeight * viewTransform.scale.toFloat()
                drawImage(
                    currentBitmap.asImageBitmap(),
                    dstOffset = IntOffset(
                        transformedTopLeft.x.toInt(),
                        transformedTopLeft.y.toInt()
                    ),
                    dstSize = IntSize(renderedWidth.toInt(), renderedHeight.toInt())
                )
                drawRect(
                    Color(0xFF607978),
                    topLeft = Offset(
                        transformedTopLeft.x.toFloat(),
                        transformedTopLeft.y.toFloat()
                    ),
                    size = Size(renderedWidth, renderedHeight),
                    style = Stroke(1f)
                )
                navigation.pathPreview?.points?.zipWithNext()?.forEach { (start, end) ->
                    drawLine(
                        color = Color(0xFF4CE8D3),
                        start = start.toOffset(descriptor.coordinates, viewport, viewTransform),
                        end = end.toOffset(descriptor.coordinates, viewport, viewTransform),
                        strokeWidth = 4f
                    )
                }
                navigation.navigationPath?.points?.zipWithNext()?.forEach { (start, end) ->
                    drawLine(
                        color = Color(0xFFFFA726),
                        start = start.toOffset(descriptor.coordinates, viewport, viewTransform),
                        end = end.toOffset(descriptor.coordinates, viewport, viewTransform),
                        strokeWidth = 6f
                    )
                }
                navigation.initialPoseDraft?.let {
                    drawPose(
                        it,
                        descriptor.coordinates,
                        viewport,
                        viewTransform,
                        Color(0xFFFFC857)
                    )
                }
                navigation.targetDraft?.let {
                    drawPose(
                        it,
                        descriptor.coordinates,
                        viewport,
                        viewTransform,
                        Color(0xFFFF6B6B)
                    )
                }
                navigation.robotPose?.let {
                    drawPose(
                        it,
                        descriptor.coordinates,
                        viewport,
                        viewTransform,
                        Color(0xFF00E5FF)
                    )
                }
                editDraft?.let {
                    drawPose(
                        it,
                        descriptor.coordinates,
                        viewport,
                        viewTransform,
                        if (editMode == PoseEditMode.INITIAL_POSE) {
                            Color(0xFFFFC857)
                        } else {
                            Color(0xFFFF6B6B)
                        }
                    )
                }
                val start = dragStart
                val end = dragEnd
                val dragPosition = start?.let {
                    MapNavigationCoordinates.screenToWorld(descriptor.coordinates, viewport, it)
                }
                val dragYaw = if (start != null && end != null) {
                    MapNavigationCoordinates.dragToWorldYaw(
                        descriptor.coordinates,
                        start,
                        end,
                        minimumDragPx = 8.0 / viewTransform.scale
                    )
                } else {
                    null
                }
                if (dragPosition != null && dragYaw != null) {
                    drawPose(
                        dragPosition.copy(yaw = dragYaw),
                        descriptor.coordinates,
                        viewport,
                        viewTransform,
                        if (editMode == PoseEditMode.INITIAL_POSE) {
                            Color(0xFFFFC857)
                        } else {
                            Color(0xFFFF6B6B)
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onResetView,
                enabled = !viewTransform.isIdentity()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(3.dp))
                Text("还原")
            }
            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    if (expanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (expanded) "退出全屏" else "全屏地图",
                    tint = Color.White
                )
            }
        }
        if (!expanded) {
            navigation.navigationPath?.let {
                Text(
                    "实时路线 %.2f m · %d 点".format(it.lengthM, it.points.size),
                    color = Color(0xFFFFC26A),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        }
        if (expanded && editMode != PoseEditMode.NONE) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancelEdit) { Text("取消") }
                Button(onClick = onCommitEdit, enabled = editDraft != null) {
                    Text("使用此位姿")
                }
            }
        }
        val message = state.mapNavigationError.ifEmpty { interactionMessage }
        if (message.isNotEmpty()) {
            Text(
                message,
                color = if (state.mapNavigationError.isEmpty()) Color.White else Color(0xFFFFB29E),
                fontSize = 12.sp,
                modifier = Modifier.align(
                    if (expanded && editMode != PoseEditMode.NONE) {
                        Alignment.TopCenter
                    } else {
                        Alignment.BottomCenter
                    }
                ).padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun NavigationStatusDialog(
    state: ControllerState,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        scrollState.scrollTo(0)
    }
    OperatorAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导航状态详情") },
        text = {
            NavigationStatus(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(scrollState)
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun NavigationConfirmationDialog(
    action: NavigationConfirmation,
    state: ControllerState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val navigation = state.mapNavigationState
    val selected = navigation.maps.firstOrNull { it.identity == navigation.selectedMap }
    val title: String
    val message: String
    val confirmLabel: String
    val enabled: Boolean
    when (action) {
        NavigationConfirmation.LOAD_MAP -> {
            title = "加载定位地图"
            message = selected?.let {
                "将加载 ${it.displayName}（rev ${it.identity.revision}），并进入定位初始化流程。"
            } ?: "尚未选择可加载地图。"
            confirmLabel = "确认加载"
            enabled = navigation.canLoadSelectedMap(
                state.isActionAllowed(ActionCode.ACTION_CODE_SWITCH_MAP)
            )
        }
        NavigationConfirmation.SUBMIT_INITIAL_POSE -> {
            title = "发送初始位姿"
            val pose = navigation.initialPoseDraft
            val map = navigation.initialPoseMap
            message = if (pose != null && map != null) {
                "地图 ${map.mapId} · rev ${map.revision}\n${pose.displayCoordinates()}"
            } else {
                "尚未选择有效初始位姿。"
            }
            confirmLabel = "确认发送"
            enabled = pose != null && map != null &&
                state.isActionAllowed(ActionCode.ACTION_CODE_SET_INITIAL_POSE)
        }
        NavigationConfirmation.STOP_RUNTIME -> {
            title = "停止定位运行时"
            message = "停止后将退出当前定位导航流程，并清除实时位姿、目标和路线。"
            confirmLabel = "停止定位"
            enabled = state.isActionAllowed(ActionCode.ACTION_CODE_STOP_RUNTIME)
        }
        NavigationConfirmation.START_NAVIGATION -> {
            title = "开始自动导航"
            val target = navigation.targetDraft
            val preview = navigation.pathPreview
            message = if (target != null && preview != null) {
                "${target.displayCoordinates()}\n规划长度 %.2f m · %d 点\n确认后控制权将交给自动导航。".format(
                    preview.lengthM,
                    preview.points.size
                )
            } else {
                "当前目标没有有效规划预览。"
            }
            confirmLabel = "开始导航"
            enabled = target != null && preview != null &&
                state.isActionAllowed(ActionCode.ACTION_CODE_START_NAVIGATION)
        }
    }
    OperatorAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                enabled = enabled,
                onClick = onConfirm,
                colors = if (action == NavigationConfirmation.STOP_RUNTIME) {
                    ButtonDefaults.buttonColors(containerColor = Color(0xFFB84A3A))
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun NavigationStatus(state: ControllerState, modifier: Modifier) {
    val navigation = state.mapNavigationState
    val snapshot = state.controllerSnapshot
    val presentation = NavigationWorkflowPresenter.present(snapshot, navigation)
    Column(
        modifier = modifier
            .background(Color(0xFF151B1C), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("权威状态", color = Color.White, fontSize = 15.sp)
        InfoRow("运行模式", snapshot?.operation_mode?.displayName() ?: "未知")
        InfoRow("定位", presentation.localizationLabel)
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
        InfoRow("当前步骤", presentation.stepLabel)
        presentation.primaryAction?.let {
            InfoRow(
                "步骤状态",
                when (presentation.actionAllowed) {
                    true -> "可执行"
                    false -> "已阻断"
                    null -> "等待主控授权"
                }
            )
        }
        navigation.navigationPath?.let {
            InfoRow(
                "实时路线",
                "%.2f m · %d 点 · #%d".format(
                    it.lengthM,
                    it.points.size,
                    it.pathSequence
                )
            )
        }
        if (presentation.blockingReasons.isNotEmpty()) {
            Text("当前步骤阻断", color = Color(0xFFFFB29E), fontSize = 13.sp)
            presentation.blockingReasons.forEach { reason ->
                Text("· ${reason.message}", color = Color(0xFFFFC5B7), fontSize = 11.sp)
                Text(reason.code, color = Color(0xFF8DA3A1), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun NavigationActions(
    state: ControllerState,
    controller: Controller,
    onOpenMaps: () -> Unit,
    onEditMode: (PoseEditMode) -> Unit,
    onConfirm: (NavigationConfirmation) -> Unit
) {
    val navigation = state.mapNavigationState
    val presentation = NavigationWorkflowPresenter.present(
        state.controllerSnapshot,
        navigation
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF151B1C),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    presentation.stageText,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                navigation.pathPreview
                    ?.takeIf { presentation.step != NavigationWorkflowStep.NAVIGATING }
                    ?.let {
                        Text(
                            "规划 %.2f m · %d 点".format(it.lengthM, it.points.size),
                            color = Color(0xFF8EA5A3),
                            fontSize = 10.sp
                        )
                    }
            }
            when {
                presentation.step == NavigationWorkflowStep.SELECT_MAP -> {
                    Button(onClick = onOpenMaps) { Text("选择地图") }
                }
                presentation.step == NavigationWorkflowStep.LOAD_MAP -> {
                    Button(
                        onClick = { onConfirm(NavigationConfirmation.LOAD_MAP) },
                        enabled = presentation.actionAllowed == true
                    ) { Text("加载地图") }
                }
                presentation.step == NavigationWorkflowStep.NAVIGATING -> {
                    OutlinedButton(
                        onClick = controller::cancelNavigation,
                        enabled = state.isActionAllowed(
                            ActionCode.ACTION_CODE_CANCEL_NAVIGATION
                        ),
                        colors = navigationOutlinedButtonColors()
                    ) { Text("取消导航") }
                }
                presentation.step == NavigationWorkflowStep.SET_INITIAL_POSE ||
                    presentation.step == NavigationWorkflowStep.CONFIRM_INITIAL_POSE -> {
                    if (navigation.initialPoseDraft != null) {
                        OutlinedButton(
                            onClick = { onEditMode(PoseEditMode.INITIAL_POSE) },
                            enabled = state.isActionAllowed(
                                ActionCode.ACTION_CODE_SET_INITIAL_POSE
                            ),
                            colors = navigationOutlinedButtonColors()
                        ) { Text("重新设置") }
                        Button(
                            onClick = {
                                onConfirm(NavigationConfirmation.SUBMIT_INITIAL_POSE)
                            },
                            enabled = state.isActionAllowed(
                                ActionCode.ACTION_CODE_SET_INITIAL_POSE
                            )
                        ) { Text("确认初始位姿") }
                    } else {
                        Button(
                            onClick = { onEditMode(PoseEditMode.INITIAL_POSE) },
                            enabled = state.isActionAllowed(
                                ActionCode.ACTION_CODE_SET_INITIAL_POSE
                            )
                        ) { Text("设置初始位姿") }
                    }
                }
                presentation.step == NavigationWorkflowStep.SELECT_GOAL ||
                    presentation.step == NavigationWorkflowStep.PLAN_ROUTE ||
                    presentation.step == NavigationWorkflowStep.START_NAVIGATION -> {
                    OutlinedButton(
                        onClick = { onEditMode(PoseEditMode.NAVIGATION_GOAL) },
                        enabled = state.isActionAllowed(
                            ActionCode.ACTION_CODE_PREVIEW_GOAL
                        ),
                        colors = navigationOutlinedButtonColors()
                    ) {
                        Text(if (navigation.targetDraft == null) "设置目标" else "重新选点")
                    }
                    if (navigation.targetDraft != null) {
                        if (navigation.pathPreview == null) {
                            Button(
                                onClick = controller::requestNavigationPreview,
                                enabled = state.isActionAllowed(
                                    ActionCode.ACTION_CODE_PREVIEW_GOAL
                                )
                            ) { Text("规划路线") }
                        } else {
                            OutlinedButton(
                                onClick = controller::requestNavigationPreview,
                                enabled = state.isActionAllowed(
                                    ActionCode.ACTION_CODE_PREVIEW_GOAL
                                ),
                                colors = navigationOutlinedButtonColors()
                            ) { Text("重新规划") }
                            Button(
                                onClick = {
                                    onConfirm(NavigationConfirmation.START_NAVIGATION)
                                },
                                enabled = state.isActionAllowed(
                                    ActionCode.ACTION_CODE_START_NAVIGATION
                                )
                            ) { Text("开始导航") }
                        }
                    }
                }
            }
        }
    }
}

private fun MappingPose.toOffset(
    coordinates: SavedMapCoordinates,
    viewport: MapViewport,
    viewTransform: MapViewTransform
): Offset {
    val point = viewTransform.apply(
        viewport,
        MapNavigationCoordinates.worldToScreen(coordinates, viewport, this)
    )
    return Offset(point.x.toFloat(), point.y.toFloat())
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPose(
    pose: MappingPose,
    coordinates: SavedMapCoordinates,
    viewport: MapViewport,
    viewTransform: MapViewTransform,
    color: Color
) {
    val center = pose.toOffset(coordinates, viewport, viewTransform)
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

private fun MappingPose.displayCoordinates(): String {
    return "x=%.2f m · y=%.2f m · yaw=%.1f°".format(
        x,
        y,
        Math.toDegrees(yaw)
    )
}

@Composable
private fun StatusPill(text: String, tone: NavigationStatusTone) {
    val foreground = when (tone) {
        NavigationStatusTone.GOOD -> Color(0xFF79E2D4)
        NavigationStatusTone.NEUTRAL -> Color(0xFFB5C4C2)
        NavigationStatusTone.WARNING -> Color(0xFFFFB29E)
    }
    val background = when (tone) {
        NavigationStatusTone.GOOD -> Color(0x3327C7C4)
        NavigationStatusTone.NEUTRAL -> Color(0x332B3D3C)
        NavigationStatusTone.WARNING -> Color(0x33E45D3D)
    }
    Text(
        text = text,
        color = foreground,
        fontSize = 12.sp,
        modifier = Modifier
            .background(background, RoundedCornerShape(12.dp))
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
