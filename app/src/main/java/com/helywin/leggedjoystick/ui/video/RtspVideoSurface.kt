package com.helywin.leggedjoystick.ui.video

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
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

private enum class VideoPlaybackState {
    IDLE,
    LOADING,
    PLAYING,
    ERROR
}

enum class RtspVideoScaleMode {
    BestFit,
    Fill
}

@Composable
fun RtspVideoSurface(
    rtspUrl: String,
    modifier: Modifier = Modifier,
    scaleMode: RtspVideoScaleMode = RtspVideoScaleMode.BestFit,
    useTextureView: Boolean = false,
    showStatus: Boolean = true,
    onSurfaceViewReady: (SurfaceView?) -> Unit = {}
) {
    val isInPreview = LocalInspectionMode.current
    val latestSurfaceCallback by rememberUpdatedState(onSurfaceViewReady)

    if (isInPreview) {
        DisposableEffect(Unit) {
            latestSurfaceCallback(null)
            onDispose {
                latestSurfaceCallback(null)
            }
        }
        RtspVideoPreviewPlaceholder(
            rtspUrl = rtspUrl,
            showStatus = showStatus,
            modifier = modifier
        )
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val latestRtspUrl by rememberUpdatedState(rtspUrl)
    var playbackState by remember { mutableStateOf(VideoPlaybackState.IDLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isViewAttached by remember { mutableStateOf(false) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    val libVLC = remember {
        LibVLC(
            context,
            arrayListOf(
                "--no-drop-late-frames",
                "--no-skip-frames",
                "--rtsp-tcp",
                "-vv"
            )
        )
    }
    val mediaPlayer = remember { MediaPlayer(libVLC) }

    DisposableEffect(lifecycleOwner, mediaPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    try {
                        mediaPlayer.stop()
                        mediaPlayer.detachViews()
                    } catch (e: Exception) {
                        Timber.w(e, "[RtspVideoSurface] 后台释放视频输出失败: %s", latestRtspUrl)
                    } finally {
                        isViewAttached = false
                        playbackState = VideoPlaybackState.IDLE
                        latestSurfaceCallback(null)
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    retryTrigger++
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(mediaPlayer, libVLC) {
        val eventListener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> playbackState = VideoPlaybackState.LOADING
                MediaPlayer.Event.Buffering -> {
                    playbackState = if (event.buffering >= 100f) {
                        VideoPlaybackState.PLAYING
                    } else {
                        VideoPlaybackState.LOADING
                    }
                }
                MediaPlayer.Event.Playing -> playbackState = VideoPlaybackState.PLAYING
                MediaPlayer.Event.Stopped,
                MediaPlayer.Event.EndReached -> playbackState = VideoPlaybackState.IDLE
                MediaPlayer.Event.EncounteredError -> {
                    playbackState = VideoPlaybackState.ERROR
                    errorMessage = "视频流播放错误"
                    Timber.e("[RtspVideoSurface] VLC 播放错误: %s", rtspUrl)
                }
                else -> Unit
            }
        }
        mediaPlayer.setEventListener(eventListener)

        onDispose {
            try {
                mediaPlayer.stop()
                mediaPlayer.detachViews()
                mediaPlayer.release()
                libVLC.release()
            } finally {
                latestSurfaceCallback(null)
                Timber.d("[RtspVideoSurface] VLC 资源已释放: %s", rtspUrl)
            }
        }
    }

    LaunchedEffect(isViewAttached, retryTrigger, rtspUrl) {
        if (!isViewAttached) return@LaunchedEffect
        if (rtspUrl.isBlank()) {
            playbackState = VideoPlaybackState.IDLE
            errorMessage = "未配置视频流"
            return@LaunchedEffect
        }

        playbackState = VideoPlaybackState.LOADING
        errorMessage = null
        try {
            delay(120)
            mediaPlayer.stop()
            val media = Media(libVLC, Uri.parse(rtspUrl))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":network-caching=500")
            media.addOption(":live-caching=500")
            media.addOption(":rtsp-tcp")
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
            Timber.i("[RtspVideoSurface] VLC 开始加载 RTSP 流: %s", rtspUrl)
        } catch (e: Exception) {
            playbackState = VideoPlaybackState.ERROR
            errorMessage = e.message ?: "加载失败"
            Timber.e(e, "[RtspVideoSurface] VLC RTSP 流加载失败: %s", rtspUrl)
        }
    }

    Box(
        modifier = modifier.background(Color.Black)
    ) {
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
                if (!isViewAttached) {
                    mediaPlayer.attachViews(layout, null, false, useTextureView)
                    isViewAttached = true
                }
                mediaPlayer.setVideoScale(scaleMode.toVlcScaleType())
                latestSurfaceCallback(findRtspSurfaceView(layout))
            }
        )

        if (showStatus) {
            when (playbackState) {
                VideoPlaybackState.LOADING -> VideoStatusOverlay(text = "连接视频流")
                VideoPlaybackState.ERROR -> VideoErrorOverlay(errorMessage ?: "播放错误")
                VideoPlaybackState.IDLE -> {
                    if (rtspUrl.isBlank()) {
                        VideoStatusOverlay(text = "未配置视频流")
                    }
                }
                VideoPlaybackState.PLAYING -> Unit
            }
        }
    }
}

@Composable
private fun RtspVideoPreviewPlaceholder(
    rtspUrl: String,
    showStatus: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color(0xFF101414)),
        contentAlignment = Alignment.Center
    ) {
        if (showStatus) {
            Text(
                text = if (rtspUrl.isBlank()) "未配置视频流" else "视频预览占位",
                color = Color.White.copy(alpha = 0.72f)
            )
        }
    }
}

private fun RtspVideoScaleMode.toVlcScaleType(): MediaPlayer.ScaleType {
    return when (this) {
        RtspVideoScaleMode.BestFit -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
        RtspVideoScaleMode.Fill -> MediaPlayer.ScaleType.SURFACE_FILL
    }
}

fun captureRtspSurfaceSnapshot(
    context: Context,
    surfaceView: SurfaceView,
    onFinished: (Boolean) -> Unit
) {
    if (surfaceView.width <= 0 || surfaceView.height <= 0) {
        Toast.makeText(context, "视频视图未准备好", Toast.LENGTH_SHORT).show()
        onFinished(false)
        return
    }

    val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
    PixelCopy.request(
        surfaceView,
        bitmap,
        { copyResult ->
            if (copyResult == PixelCopy.SUCCESS) {
                saveBitmapToGallery(context, bitmap, onFinished)
            } else {
                bitmap.recycle()
                Toast.makeText(context, "截图失败: $copyResult", Toast.LENGTH_SHORT).show()
                Timber.e("[RtspVideoSurface] PixelCopy 失败: %s", copyResult)
                onFinished(false)
            }
        },
        Handler(Looper.getMainLooper())
    )
}

private fun findRtspSurfaceView(viewGroup: ViewGroup): SurfaceView? {
    for (index in 0 until viewGroup.childCount) {
        val child = viewGroup.getChildAt(index)
        if (child is SurfaceView) {
            return child
        }
        if (child is ViewGroup) {
            val found = findRtspSurfaceView(child)
            if (found != null) return found
        }
    }
    return null
}

@Composable
private fun VideoStatusOverlay(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Text(text = text, color = Color.White)
        }
    }
}

@Composable
private fun VideoErrorOverlay(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = Color(0xFFE45D3D),
                modifier = Modifier.size(30.dp)
            )
            Text(text = message, color = Color.White)
        }
    }
}

private fun saveBitmapToGallery(
    context: Context,
    bitmap: Bitmap,
    onFinished: (Boolean) -> Unit
) {
    Thread {
        var saved = false
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
                        saved = bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "LeggedJoystick")
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                val file = File(appDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    saved = bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
            }

            Timber.i("[RtspVideoSurface] 截图保存结果: %s", saved)
        } catch (e: Exception) {
            Timber.e(e, "[RtspVideoSurface] 保存截图失败")
        } finally {
            bitmap.recycle()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    if (saved) "截图已保存到相册" else "保存截图失败",
                    Toast.LENGTH_SHORT
                ).show()
                onFinished(saved)
            }
        }
    }.start()
}
