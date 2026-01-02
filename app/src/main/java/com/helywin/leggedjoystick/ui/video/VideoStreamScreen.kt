/*********************************************************************************
 * FileName: VideoStreamScreen.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 全屏RTSP视频流播放界面（使用VLC）
 * Others:
 *********************************************************************************/

package com.helywin.leggedjoystick.ui.video

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 视频流播放状态
 */
enum class VideoPlaybackState {
    LOADING,
    PLAYING,
    ERROR,
    IDLE
}

/**
 * 全屏视频流播放界面（使用VLC）
 */
@Composable
fun VideoStreamScreen(
    rtspUrl: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var playbackState by remember { mutableStateOf(VideoPlaybackState.LOADING) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isViewAttached by remember { mutableStateOf(false) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var isTakingSnapshot by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingStartTime by remember { mutableLongStateOf(0L) }
    var recordingDuration by remember { mutableStateOf("00:00") }
    var currentRecordingFile by remember { mutableStateOf<File?>(null) }
    var videoLayoutRef by remember { mutableStateOf<VLCVideoLayout?>(null) }
    var needsReconnect by remember { mutableStateOf(false) }

    // 按钮交互状态
    val recordButtonInteractionSource = remember { MutableInteractionSource() }
    val isRecordButtonPressed by recordButtonInteractionSource.collectIsPressedAsState()
    val snapshotButtonInteractionSource = remember { MutableInteractionSource() }
    val isSnapshotButtonPressed by snapshotButtonInteractionSource.collectIsPressedAsState()

    // 按钮缩放动画
    val recordButtonScale by animateFloatAsState(
        targetValue = if (isRecordButtonPressed) 0.85f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "recordButtonScale"
    )
    val snapshotButtonScale by animateFloatAsState(
        targetValue = if (isSnapshotButtonPressed) 0.85f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "snapshotButtonScale"
    )

    // 沉浸式全屏 + 保持屏幕常亮
    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity
        val window = activity?.window

        // 保持屏幕常亮
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 沉浸式全屏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window?.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }

        Timber.d("[VideoStream] 已启用沉浸式全屏和屏幕常亮")

        onDispose {
            // 恢复状态栏和导航栏
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window?.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            } else {
                @Suppress("DEPRECATION")
                window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }

            // 清除屏幕常亮
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            Timber.d("[VideoStream] 已禁用沉浸式全屏和屏幕常亮")
        }
    }

    // 创建 LibVLC 实例
    val libVLC = remember {
        LibVLC(context, arrayListOf(
            "--no-drop-late-frames",
            "--no-skip-frames",
            "--rtsp-tcp",
            "-vvv"
        ))
    }

    // 创建 MediaPlayer 实例
    val mediaPlayer = remember {
        MediaPlayer(libVLC)
    }

    // 生命周期监听 - 处理应用前后台切换
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // 应用进入后台，暂停播放
                    if (mediaPlayer.isPlaying) {
                        mediaPlayer.pause()
                        Timber.d("[VideoStream] 应用进入后台，暂停播放")
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    // 应用恢复到前台，重新连接
                    if (isViewAttached) {
                        needsReconnect = true
                        Timber.d("[VideoStream] 应用恢复到前台，标记需要重新连接")
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 处理重新连接
    LaunchedEffect(needsReconnect) {
        if (needsReconnect && isViewAttached) {
            needsReconnect = false
            playbackState = VideoPlaybackState.LOADING
            errorMessage = null

            // 重新附加视图并播放
            videoLayoutRef?.let { layout ->
                try {
                    mediaPlayer.stop()
                    mediaPlayer.detachViews()
                    mediaPlayer.attachViews(layout, null, false, false)

                    val media = Media(libVLC, Uri.parse(rtspUrl))
                    media.setHWDecoderEnabled(true, false)
                    media.addOption(":network-caching=300")
                    media.addOption(":rtsp-tcp")
                    mediaPlayer.media = media
                    media.release()
                    mediaPlayer.play()
                    Timber.i("[VideoStream] 重新连接 RTSP 流: $rtspUrl")
                } catch (e: Exception) {
                    playbackState = VideoPlaybackState.ERROR
                    errorMessage = e.message ?: "重新连接失败"
                    Timber.e(e, "[VideoStream] 重新连接失败")
                }
            }
        }
    }

    // 录制时间更新
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                val elapsed = System.currentTimeMillis() - recordingStartTime
                val seconds = (elapsed / 1000) % 60
                val minutes = (elapsed / 1000) / 60
                recordingDuration = String.format("%02d:%02d", minutes, seconds)
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    // 使用 PixelCopy 进行截图
    fun captureSnapshot() {
        if (isTakingSnapshot) return
        isTakingSnapshot = true

        val layout = videoLayoutRef
        if (layout == null) {
            Toast.makeText(context, "视频视图未准备好", Toast.LENGTH_SHORT).show()
            isTakingSnapshot = false
            return
        }

        // 查找 SurfaceView
        val surfaceView = findSurfaceView(layout)
        if (surfaceView == null) {
            Toast.makeText(context, "无法获取视频表面", Toast.LENGTH_SHORT).show()
            isTakingSnapshot = false
            return
        }

        try {
            val bitmap = Bitmap.createBitmap(
                surfaceView.width,
                surfaceView.height,
                Bitmap.Config.ARGB_8888
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PixelCopy.request(
                    surfaceView,
                    bitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            saveBitmapToGallery(context, bitmap)
                        } else {
                            (context as? ComponentActivity)?.runOnUiThread {
                                Toast.makeText(context, "截图失败: $copyResult", Toast.LENGTH_SHORT).show()
                            }
                            Timber.e("[VideoStream] PixelCopy 失败: $copyResult")
                        }
                        isTakingSnapshot = false
                    },
                    Handler(Looper.getMainLooper())
                )
            } else {
                // Android N 以下使用 drawingCache（已弃用但作为后备）
                @Suppress("DEPRECATION")
                surfaceView.isDrawingCacheEnabled = true
                @Suppress("DEPRECATION")
                val cache = surfaceView.drawingCache
                if (cache != null) {
                    val copy = cache.copy(Bitmap.Config.ARGB_8888, false)
                    saveBitmapToGallery(context, copy)
                } else {
                    Toast.makeText(context, "截图失败", Toast.LENGTH_SHORT).show()
                }
                @Suppress("DEPRECATION")
                surfaceView.isDrawingCacheEnabled = false
                isTakingSnapshot = false
            }
        } catch (e: Exception) {
            Timber.e(e, "[VideoStream] 截图异常")
            Toast.makeText(context, "截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
            isTakingSnapshot = false
        }
    }

    // 开始录制
    fun startRecording() {
        if (isRecording) return

        try {
            // 录制到缓存目录
            val recordDir = File(context.cacheDir, "recordings")
            if (!recordDir.exists()) {
                val created = recordDir.mkdirs()
                Timber.d("[VideoStream] 创建录制目录: $created, 路径: ${recordDir.absolutePath}")
            }

            // VLC 的 record() 方法需要传入目录路径
            // 它会在这个目录下自动生成文件名如: vlc-record-xxx.mp4
            // 注意：路径末尾不能有文件名前缀，直接使用目录路径
            val recordPath = recordDir.absolutePath
            currentRecordingFile = recordDir // 保存目录，停止时再查找实际文件

            Timber.d("[VideoStream] 准备录制到目录: $recordPath")

            // 使用 VLC 的 record() 方法开始录制
            val recordStarted = mediaPlayer.record(recordPath)
            if (recordStarted) {
                isRecording = true
                recordingStartTime = System.currentTimeMillis()
                recordingDuration = "00:00"
                Toast.makeText(context, "开始录制", Toast.LENGTH_SHORT).show()
                Timber.i("[VideoStream] 开始录制到目录: $recordPath")
            } else {
                Toast.makeText(context, "录制启动失败", Toast.LENGTH_SHORT).show()
                Timber.e("[VideoStream] 录制启动失败")
            }
        } catch (e: Exception) {
            Timber.e(e, "[VideoStream] 录制异常")
            Toast.makeText(context, "录制失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 停止录制
    fun stopRecording() {
        if (!isRecording) return

        try {
            isRecording = false

            // 停止录制
            mediaPlayer.record(null)
            Timber.d("[VideoStream] 停止录制")

            // 等待文件写入完成后查找录制的文件
            Thread {
                try {
                    Thread.sleep(1000) // 等待文件写入完成

                    val recordDir = File(context.cacheDir, "recordings")
                    Timber.d("[VideoStream] 查找录制目录: ${recordDir.absolutePath}")

                    // 查找最新的录制文件
                    val recordedFiles = recordDir.listFiles { file ->
                        file.isFile && (file.name.endsWith(".ts") || file.name.endsWith(".mp4") || file.name.endsWith(".mkv"))
                    }

                    Timber.d("[VideoStream] 找到的文件: ${recordedFiles?.map { "${it.name} (${it.length()} bytes)" }}")

                    // 找到最新的文件
                    val latestFile = recordedFiles?.maxByOrNull { it.lastModified() }

                    if (latestFile != null && latestFile.exists() && latestFile.length() > 0) {
                        val fileSize = latestFile.length()
                        Timber.i("[VideoStream] 录制文件: ${latestFile.name}, 大小: $fileSize bytes")

                        // 保存到相册
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val contentValues = ContentValues().apply {
                                put(MediaStore.Video.Media.DISPLAY_NAME, latestFile.name)
                                put(MediaStore.Video.Media.MIME_TYPE, "video/mp2t")
                                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/LeggedJoystick")
                            }

                            val uri = context.contentResolver.insert(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                contentValues
                            )

                            if (uri != null) {
                                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                    latestFile.inputStream().use { inputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                                val savedSize = latestFile.length()
                                latestFile.delete()
                                (context as? ComponentActivity)?.runOnUiThread {
                                    Toast.makeText(context, "视频已保存到相册 (${savedSize / 1024}KB)", Toast.LENGTH_LONG).show()
                                }
                                Timber.i("[VideoStream] 视频已保存到相册: ${latestFile.name}, 大小: $savedSize bytes")
                            } else {
                                Timber.e("[VideoStream] 无法创建 MediaStore URI")
                                (context as? ComponentActivity)?.runOnUiThread {
                                    Toast.makeText(context, "保存失败: 无法创建文件", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                            val appDir = File(moviesDir, "LeggedJoystick")
                            if (!appDir.exists()) {
                                appDir.mkdirs()
                            }
                            val destFile = File(appDir, latestFile.name)
                            latestFile.copyTo(destFile, overwrite = true)
                            val savedSize = destFile.length()
                            latestFile.delete()
                            (context as? ComponentActivity)?.runOnUiThread {
                                Toast.makeText(context, "视频已保存: ${destFile.absolutePath} (${savedSize / 1024}KB)", Toast.LENGTH_LONG).show()
                            }
                            Timber.i("[VideoStream] 视频已保存: ${destFile.absolutePath}, 大小: $savedSize bytes")
                        }
                    } else {
                        Timber.e("[VideoStream] 未找到录制文件")
                        (context as? ComponentActivity)?.runOnUiThread {
                            Toast.makeText(context, "录制失败: 未生成视频文件", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[VideoStream] 保存视频失败")
                    (context as? ComponentActivity)?.runOnUiThread {
                        Toast.makeText(context, "保存视频失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                currentRecordingFile = null
            }.start()

            Timber.i("[VideoStream] 已发送停止录制命令")
        } catch (e: Exception) {
            Timber.e(e, "[VideoStream] 停止录制异常")
            Toast.makeText(context, "停止录制失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 设置播放器事件监听
    DisposableEffect(mediaPlayer) {
        val eventListener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> {
                    playbackState = VideoPlaybackState.LOADING
                    Timber.d("[VideoStream] VLC 正在打开流")
                }
                MediaPlayer.Event.Buffering -> {
                    if (event.buffering >= 100f) {
                        playbackState = VideoPlaybackState.PLAYING
                    } else {
                        playbackState = VideoPlaybackState.LOADING
                    }
                    Timber.d("[VideoStream] VLC 缓冲中: ${event.buffering}%")
                }
                MediaPlayer.Event.Playing -> {
                    playbackState = VideoPlaybackState.PLAYING
                    Timber.d("[VideoStream] VLC 正在播放")
                }
                MediaPlayer.Event.Paused -> {
                    Timber.d("[VideoStream] VLC 已暂停")
                }
                MediaPlayer.Event.Stopped -> {
                    playbackState = VideoPlaybackState.IDLE
                    Timber.d("[VideoStream] VLC 已停止")
                }
                MediaPlayer.Event.EndReached -> {
                    playbackState = VideoPlaybackState.IDLE
                    Timber.d("[VideoStream] VLC 播放结束")
                }
                MediaPlayer.Event.EncounteredError -> {
                    playbackState = VideoPlaybackState.ERROR
                    errorMessage = "视频流播放错误"
                    Timber.e("[VideoStream] VLC 播放错误")
                }
            }
        }
        mediaPlayer.setEventListener(eventListener)

        onDispose {
            // 停止录制（如果正在录制）
            if (isRecording) {
                mediaPlayer.record(null)
            }
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.release()
            libVLC.release()
            Timber.d("[VideoStream] VLC 资源已释放")
        }
    }

    // 当视图附加完成后开始播放
    LaunchedEffect(isViewAttached, retryTrigger) {
        if (isViewAttached) {
            playbackState = VideoPlaybackState.LOADING
            errorMessage = null
            try {
                val media = Media(libVLC, Uri.parse(rtspUrl))
                media.setHWDecoderEnabled(true, false)
                media.addOption(":network-caching=300")
                media.addOption(":rtsp-tcp")
                mediaPlayer.media = media
                media.release()
                mediaPlayer.play()
                Timber.i("[VideoStream] VLC 开始加载 RTSP 流: $rtspUrl")
            } catch (e: Exception) {
                playbackState = VideoPlaybackState.ERROR
                errorMessage = e.message ?: "加载失败"
                Timber.e(e, "[VideoStream] VLC RTSP 流加载失败")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // VLC 视频播放器视图
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { layout ->
                videoLayoutRef = layout
                if (!isViewAttached) {
                    mediaPlayer.attachViews(layout, null, false, false)
                    isViewAttached = true
                    Timber.d("[VideoStream] VLC 视图已附加")
                }
            }
        )

        // 加载中指示器
        if (playbackState == VideoPlaybackState.LOADING) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color.White
                    )
                    Text(
                        text = "正在连接视频流...",
                        color = Color.White
                    )
                }
            }
        }

        // 错误显示
        if (playbackState == VideoPlaybackState.ERROR) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = errorMessage ?: "播放错误",
                        color = Color.White
                    )
                    Button(
                        onClick = {
                            mediaPlayer.stop()
                            retryTrigger++
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("重试")
                    }
                }
            }
        }

        // 录制中指示器 - 左上角
        if (isRecording) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = recordingDuration,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }

        // 关闭按钮 - 右上角
        IconButton(
            onClick = {
                if (isRecording) {
                    stopRecording()
                }
                onBackClick()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        // 底部按钮区域
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 录制按钮
            FloatingActionButton(
                onClick = {
                    if (isRecording) {
                        stopRecording()
                    } else {
                        startRecording()
                    }
                },
                modifier = Modifier.scale(recordButtonScale),
                shape = CircleShape,
                containerColor = if (isRecording) Color.Red else Color.White,
                contentColor = if (isRecording) Color.White else Color.Red,
                interactionSource = recordButtonInteractionSource
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = if (isRecording) "停止录制" else "开始录制",
                    modifier = Modifier.size(28.dp)
                )
            }

            // 拍照按钮
            FloatingActionButton(
                onClick = { captureSnapshot() },
                modifier = Modifier.scale(snapshotButtonScale),
                shape = CircleShape,
                containerColor = if (isTakingSnapshot) Color.Gray else Color.White,
                contentColor = Color.Black,
                interactionSource = snapshotButtonInteractionSource
            ) {
                if (isTakingSnapshot) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "拍照",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

/**
 * 递归查找 VLCVideoLayout 中的 SurfaceView
 */
private fun findSurfaceView(viewGroup: ViewGroup): SurfaceView? {
    for (i in 0 until viewGroup.childCount) {
        val child = viewGroup.getChildAt(i)
        if (child is SurfaceView) {
            return child
        }
        if (child is ViewGroup) {
            val found = findSurfaceView(child)
            if (found != null) {
                return found
            }
        }
    }
    return null
}

/**
 * 保存 Bitmap 到相册
 */
private fun saveBitmapToGallery(context: android.content.Context, bitmap: Bitmap) {
    Thread {
        try {
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "snapshot_${dateFormat.format(Date())}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LeggedJoystick")
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    (context as? ComponentActivity)?.runOnUiThread {
                        Toast.makeText(context, "截图已保存到相册", Toast.LENGTH_SHORT).show()
                    }
                    Timber.i("[VideoStream] 截图已保存: $fileName")
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "LeggedJoystick")
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                val file = File(appDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                (context as? ComponentActivity)?.runOnUiThread {
                    Toast.makeText(context, "截图已保存: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
                }
                Timber.i("[VideoStream] 截图已保存: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Timber.e(e, "[VideoStream] 保存截图失败")
            (context as? ComponentActivity)?.runOnUiThread {
                Toast.makeText(context, "保存截图失败", Toast.LENGTH_SHORT).show()
            }
        }
    }.start()
}
