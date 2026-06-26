package com.helywin.leggedjoystick.ui.video

import android.content.ContentValues
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.EnumMap
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val NETWORK_RECOVERY_DELAY_MS = 700L
private const val RTSP_RETRY_INITIAL_DELAY_MS = 1_000L
private const val RTSP_RETRY_MAX_DELAY_MS = 5_000L
private const val RTSP_START_TIMEOUT_MS = 6_000L
private const val RTSP_MAX_RETRY_ATTEMPT = 6
private const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
private const val DEFAULT_SNAPSHOT_WIDTH = 1920
private const val DEFAULT_SNAPSHOT_HEIGHT = 1080

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

enum class RtspVideoSlot {
    Main,
    Secondary
}

object RtspVideoRuntime {
    private val lock = Any()
    private val players = EnumMap<RtspVideoSlot, RtspVideoPlayer>(RtspVideoSlot::class.java)
    private val networkGeneration = MutableStateFlow(0)
    private var libVLC: LibVLC? = null
    private var appContext: Context? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var linkStateReceiver: BroadcastReceiver? = null

    fun player(context: Context, slot: RtspVideoSlot): RtspVideoPlayer {
        synchronized(lock) {
            val vlc = libVLC ?: createLibVLC(context.applicationContext).also {
                libVLC = it
                Timber.i("[RtspVideoRuntime] 进程级 LibVLC 已初始化")
            }
            return players.getOrPut(slot) { RtspVideoPlayer(slot, vlc) }
        }
    }

    private fun createLibVLC(context: Context): LibVLC {
        return LibVLC(
            context,
            arrayListOf(
                "--rtsp-tcp",
                "--drop-late-frames",
                "--skip-frames",
                "--no-video-title-show",
                "--no-osd",
                "--quiet"
            )
        )
    }

    fun networkGeneration(context: Context): StateFlow<Int> {
        ensureNetworkCallback(context.applicationContext)
        ensureLinkStateReceiver(context.applicationContext)
        return networkGeneration.asStateFlow()
    }

    private fun ensureNetworkCallback(context: Context) {
        synchronized(lock) {
            if (networkCallback != null) return

            val manager = context.getSystemService(ConnectivityManager::class.java)
            if (manager == null) {
                Timber.w("[RtspVideoRuntime] 无法获取 ConnectivityManager，网络变化后不会自动重拉 RTSP")
                return
            }

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    notifyNetworkChanged("available", network)
                }

                override fun onLost(network: Network) {
                    notifyNetworkChanged("lost", network)
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    notifyNetworkChanged(describeNetworkCapabilities(networkCapabilities), network)
                }
            }

            try {
                manager.registerDefaultNetworkCallback(callback)
                connectivityManager = manager
                networkCallback = callback
                Timber.i("[RtspVideoRuntime] 已注册默认网络变化监听")
            } catch (e: Exception) {
                Timber.w(e, "[RtspVideoRuntime] 注册默认网络变化监听失败")
            }
        }
    }

    private fun ensureLinkStateReceiver(context: Context) {
        synchronized(lock) {
            if (linkStateReceiver != null) return

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val action = intent?.action ?: return
                    notifyVideoLinkChanged("broadcast:$action")
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(ACTION_USB_STATE)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(receiver, filter)
                }
                appContext = context
                linkStateReceiver = receiver
                Timber.i("[RtspVideoRuntime] 已注册 USB/电源链路变化监听")
            } catch (e: Exception) {
                Timber.w(e, "[RtspVideoRuntime] 注册 USB/电源链路变化监听失败")
            }
        }
    }

    private fun notifyNetworkChanged(reason: String, network: Network) {
        notifyVideoLinkChanged("network:$reason:$network")
    }

    private fun notifyVideoLinkChanged(reason: String) {
        val nextGeneration = synchronized(lock) {
            val next = networkGeneration.value + 1
            networkGeneration.value = next
            next
        }
        Timber.i(
            "[RtspVideoRuntime] 视频链路变化，准备重拉 RTSP: reason=%s, generation=%s",
            reason,
            nextGeneration
        )
    }

    private fun describeNetworkCapabilities(capabilities: NetworkCapabilities): String {
        return buildString {
            append("capabilities")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) append(":wifi")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) append(":cellular")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) append(":ethernet")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB)) append(":usb")
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) append(":validated")
        }
    }

    fun releaseAll() {
        synchronized(lock) {
            players.values.forEach { it.release() }
            players.clear()
            networkCallback?.let { callback ->
                try {
                    connectivityManager?.unregisterNetworkCallback(callback)
                } catch (e: Exception) {
                    Timber.w(e, "[RtspVideoRuntime] 注销默认网络变化监听失败")
                }
            }
            linkStateReceiver?.let { receiver ->
                try {
                    appContext?.unregisterReceiver(receiver)
                } catch (e: Exception) {
                    Timber.w(e, "[RtspVideoRuntime] 注销 USB/电源链路变化监听失败")
                }
            }
            linkStateReceiver = null
            networkCallback = null
            appContext = null
            connectivityManager = null
            libVLC?.release()
            libVLC = null
        }
    }
}

class RtspVideoPlayer internal constructor(
    private val slot: RtspVideoSlot,
    private val libVLC: LibVLC
) {
    private val mediaPlayer = MediaPlayer(libVLC)
    private var attachedLayout: VLCVideoLayout? = null
    private var attached = false
    private var released = false
    private var currentUrl: String? = null
    private var currentUseTextureView = false

    val isAttached: Boolean
        get() = attached && !released

    fun setEventListener(listener: MediaPlayer.EventListener?) {
        if (!released) {
            mediaPlayer.setEventListener(listener)
        }
    }

    fun attach(
        layout: VLCVideoLayout,
        useTextureView: Boolean,
        scaleMode: RtspVideoScaleMode
    ): Boolean {
        check(!released) { "RTSP 播放器已释放: $slot" }

        val shouldAttach = !attached || attachedLayout !== layout || currentUseTextureView != useTextureView
        if (shouldAttach) {
            detachViews("重新绑定视频输出")
            mediaPlayer.attachViews(layout, null, true, useTextureView)
            attachedLayout = layout
            attached = true
            currentUseTextureView = useTextureView
            Timber.i("[RtspVideoRuntime] %s 视频输出已绑定，texture=%s", slot, useTextureView)
        }

        mediaPlayer.setVideoScale(scaleMode.toVlcScaleType())
        return shouldAttach
    }

    fun playUrl(rtspUrl: String, forceReload: Boolean = false) {
        check(!released) { "RTSP 播放器已释放: $slot" }
        if (!attached) return

        val normalizedUrl = rtspUrl.trim()
        if (normalizedUrl.isBlank()) {
            stopPlayback("空视频地址")
            return
        }
        if (!forceReload && currentUrl == normalizedUrl && mediaPlayer.isPlaying) {
            return
        }

        stopPlayback(if (forceReload) "网络变化后重拉视频流" else "切换视频流")
        val media = Media(libVLC, Uri.parse(normalizedUrl))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=300")
        media.addOption(":live-caching=300")
        media.addOption(":rtsp-tcp")
        media.addOption(":no-audio")
        media.addOption(":no-spu")
        media.addOption(":no-sub-autodetect-file")
        mediaPlayer.media = media
        media.release()
        currentUrl = normalizedUrl
        mediaPlayer.play()
        Timber.i("[RtspVideoRuntime] %s 开始加载 RTSP 流: %s, forceReload=%s", slot, normalizedUrl, forceReload)
    }

    fun stopPlayback(reason: String) {
        if (released) return
        try {
            mediaPlayer.stop()
        } catch (e: Exception) {
            Timber.w(e, "[RtspVideoRuntime] %s 停止视频流失败: %s", slot, reason)
        } finally {
            currentUrl = null
        }
    }

    fun stopAndDetach(reason: String) {
        stopPlayback(reason)
        detachViews(reason)
    }

    private fun detachViews(reason: String) {
        if (released) return
        try {
            mediaPlayer.detachViews()
        } catch (e: Exception) {
            Timber.w(e, "[RtspVideoRuntime] %s 解绑视频输出失败: %s", slot, reason)
        } finally {
            attached = false
            attachedLayout = null
        }
    }

    fun release() {
        if (released) return
        stopAndDetach("释放播放器")
        setEventListener(null)
        mediaPlayer.release()
        released = true
    }
}

@Composable
fun RtspVideoSurface(
    rtspUrl: String,
    modifier: Modifier = Modifier,
    slot: RtspVideoSlot = RtspVideoSlot.Main,
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
    val latestPlaybackState by rememberUpdatedState(playbackState)
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attachGeneration by remember { mutableIntStateOf(0) }
    var resumeGeneration by remember { mutableIntStateOf(0) }
    var retryGeneration by remember { mutableIntStateOf(0) }
    var retryAttempt by remember { mutableIntStateOf(0) }
    var playAttemptGeneration by remember { mutableIntStateOf(0) }
    val player = remember(slot) { RtspVideoRuntime.player(context, slot) }
    val networkGeneration by RtspVideoRuntime.networkGeneration(context).collectAsState()
    var handledNetworkGeneration by remember { mutableIntStateOf(networkGeneration) }
    var handledRetryGeneration by remember { mutableIntStateOf(retryGeneration) }
    var lifecycleActive by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val latestLifecycleActive by rememberUpdatedState(lifecycleActive)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    fun scheduleVideoRetry(reason: String) {
        runOnMain {
            if (latestRtspUrl.isBlank() || !player.isAttached || !latestLifecycleActive) {
                return@runOnMain
            }
            retryAttempt = (retryAttempt + 1).coerceAtMost(RTSP_MAX_RETRY_ATTEMPT)
            retryGeneration++
            playbackState = VideoPlaybackState.LOADING
            errorMessage = "正在重连视频流"
            Timber.w(
                "[RtspVideoSurface] RTSP 将继续重试: slot=%s, reason=%s, attempt=%s, url=%s",
                slot,
                reason,
                retryAttempt,
                latestRtspUrl
            )
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    lifecycleActive = false
                    player.stopAndDetach("Activity 进入后台")
                    playbackState = VideoPlaybackState.IDLE
                    latestSurfaceCallback(null)
                }
                Lifecycle.Event.ON_RESUME -> {
                    lifecycleActive = true
                    resumeGeneration++
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(player) {
        val eventListener = MediaPlayer.EventListener { event ->
            runOnMain {
                when (event.type) {
                    MediaPlayer.Event.Opening -> playbackState = VideoPlaybackState.LOADING
                    MediaPlayer.Event.Buffering -> {
                        playbackState = if (event.buffering >= 100f) {
                            retryAttempt = 0
                            errorMessage = null
                            VideoPlaybackState.PLAYING
                        } else {
                            VideoPlaybackState.LOADING
                        }
                    }
                    MediaPlayer.Event.Playing -> {
                        retryAttempt = 0
                        errorMessage = null
                        playbackState = VideoPlaybackState.PLAYING
                    }
                    MediaPlayer.Event.Stopped -> playbackState = VideoPlaybackState.IDLE
                    MediaPlayer.Event.EndReached -> {
                        playbackState = VideoPlaybackState.LOADING
                        scheduleVideoRetry("VLC 视频流结束")
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        player.stopPlayback("VLC 播放错误")
                        playbackState = VideoPlaybackState.LOADING
                        errorMessage = "正在重连视频流"
                        Timber.e("[RtspVideoSurface] VLC 播放错误，继续重连: %s", latestRtspUrl)
                        scheduleVideoRetry("VLC 播放错误")
                    }
                    else -> Unit
                }
            }
        }
        player.setEventListener(eventListener)

        onDispose {
            player.setEventListener(null)
            player.stopAndDetach("RTSP 组件离开组合")
            latestSurfaceCallback(null)
            Timber.d("[RtspVideoSurface] RTSP 组件已解绑: %s", latestRtspUrl)
        }
    }

    LaunchedEffect(attachGeneration, resumeGeneration, networkGeneration, retryGeneration, rtspUrl) {
        if (!player.isAttached) return@LaunchedEffect
        if (rtspUrl.isBlank()) {
            playbackState = VideoPlaybackState.IDLE
            errorMessage = "未配置视频流"
            return@LaunchedEffect
        }

        val networkChanged = networkGeneration != handledNetworkGeneration
        val retryRequested = retryGeneration != handledRetryGeneration
        handledNetworkGeneration = networkGeneration
        handledRetryGeneration = retryGeneration
        if (!networkChanged && !retryRequested) {
            retryAttempt = 0
        }
        playbackState = VideoPlaybackState.LOADING
        errorMessage = null
        try {
            if (networkChanged) {
                Timber.i("[RtspVideoSurface] 检测到视频链路变化，重拉视频流: slot=%s, url=%s", slot, rtspUrl)
                delay(NETWORK_RECOVERY_DELAY_MS)
            } else if (retryRequested) {
                delay(retryDelayMillis(retryAttempt))
            }
            delay(slot.startDelayMillis())
            player.playUrl(rtspUrl, forceReload = networkChanged || retryRequested)
            playAttemptGeneration++
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            playbackState = VideoPlaybackState.LOADING
            errorMessage = "正在重连视频流"
            Timber.e(e, "[RtspVideoSurface] VLC RTSP 流加载失败，继续重试: %s", rtspUrl)
            scheduleVideoRetry("VLC RTSP 流加载异常")
        }
    }

    LaunchedEffect(playAttemptGeneration, rtspUrl) {
        if (playAttemptGeneration == 0 || rtspUrl.isBlank()) return@LaunchedEffect
        delay(RTSP_START_TIMEOUT_MS)
        if (
            player.isAttached &&
            latestLifecycleActive &&
            latestRtspUrl.isNotBlank() &&
            latestPlaybackState != VideoPlaybackState.PLAYING
        ) {
            scheduleVideoRetry("RTSP 启动超时")
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
                val attached = player.attach(layout, useTextureView, scaleMode)
                if (attached) {
                    attachGeneration++
                }
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

private fun RtspVideoSlot.startDelayMillis(): Long {
    return when (this) {
        RtspVideoSlot.Main -> 80L
        RtspVideoSlot.Secondary -> 360L
    }
}

private fun retryDelayMillis(attempt: Int): Long {
    if (attempt <= 1) return RTSP_RETRY_INITIAL_DELAY_MS
    return (RTSP_RETRY_INITIAL_DELAY_MS * attempt).coerceAtMost(RTSP_RETRY_MAX_DELAY_MS)
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
    primarySurfaceView: SurfaceView?,
    secondarySurfaceView: SurfaceView?,
    onFinished: (Boolean) -> Unit
) {
    val handler = Handler(Looper.getMainLooper())
    captureSurfaceFrame(primarySurfaceView, "主背景", handler) { primary ->
        captureSurfaceFrame(secondarySurfaceView, "小视频", handler) { secondary ->
            val combinedBitmap = createStackedSnapshot(primary, secondary)
            primary.bitmap?.recycle()
            secondary.bitmap?.recycle()
            saveBitmapToGallery(context, combinedBitmap, onFinished)
        }
    }
}

private data class SurfaceSnapshotFrame(
    val bitmap: Bitmap?,
    val sourceWidth: Int,
    val sourceHeight: Int
)

private fun captureSurfaceFrame(
    surfaceView: SurfaceView?,
    label: String,
    handler: Handler,
    onResult: (SurfaceSnapshotFrame) -> Unit
) {
    if (surfaceView == null || surfaceView.width <= 0 || surfaceView.height <= 0) {
        Timber.w("[RtspVideoSurface] %s视频 Surface 未准备好，截图使用黑色占位", label)
        onResult(SurfaceSnapshotFrame(null, DEFAULT_SNAPSHOT_WIDTH, DEFAULT_SNAPSHOT_HEIGHT))
        return
    }

    val width = surfaceView.width
    val height = surfaceView.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        PixelCopy.request(
            surfaceView,
            bitmap,
            { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    onResult(SurfaceSnapshotFrame(bitmap, width, height))
                } else {
                    bitmap.recycle()
                    Timber.e("[RtspVideoSurface] %s视频 PixelCopy 失败，截图使用黑色占位: %s", label, copyResult)
                    onResult(SurfaceSnapshotFrame(null, width, height))
                }
            },
            handler
        )
    } catch (e: Exception) {
        bitmap.recycle()
        Timber.e(e, "[RtspVideoSurface] %s视频 PixelCopy 异常，截图使用黑色占位", label)
        onResult(SurfaceSnapshotFrame(null, width, height))
    }
}

private fun createStackedSnapshot(
    primary: SurfaceSnapshotFrame,
    secondary: SurfaceSnapshotFrame
): Bitmap {
    val targetWidth = maxOf(
        primary.sourceWidth.takeIf { it > 0 } ?: DEFAULT_SNAPSHOT_WIDTH,
        secondary.sourceWidth.takeIf { it > 0 } ?: DEFAULT_SNAPSHOT_WIDTH,
        DEFAULT_SNAPSHOT_WIDTH
    )
    val primaryHeight = scaledSnapshotHeight(primary, targetWidth)
    val secondaryHeight = scaledSnapshotHeight(secondary, targetWidth)
    val output = Bitmap.createBitmap(targetWidth, primaryHeight + secondaryHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawColor(android.graphics.Color.BLACK)
    drawSnapshotFrame(canvas, primary, targetWidth, primaryHeight, 0)
    drawSnapshotFrame(canvas, secondary, targetWidth, secondaryHeight, primaryHeight)
    Timber.i(
        "[RtspVideoSurface] 已生成上下拼接截图: %sx%s, top=%sx%s, bottom=%sx%s",
        output.width,
        output.height,
        targetWidth,
        primaryHeight,
        targetWidth,
        secondaryHeight
    )
    return output
}

private fun scaledSnapshotHeight(frame: SurfaceSnapshotFrame, targetWidth: Int): Int {
    val sourceWidth = frame.sourceWidth.takeIf { it > 0 } ?: DEFAULT_SNAPSHOT_WIDTH
    val sourceHeight = frame.sourceHeight.takeIf { it > 0 } ?: DEFAULT_SNAPSHOT_HEIGHT
    return (targetWidth.toFloat() * sourceHeight / sourceWidth).roundToInt().coerceAtLeast(1)
}

private fun drawSnapshotFrame(
    canvas: Canvas,
    frame: SurfaceSnapshotFrame,
    targetWidth: Int,
    targetHeight: Int,
    top: Int
) {
    val bitmap = frame.bitmap ?: return
    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    try {
        canvas.drawBitmap(scaledBitmap, 0f, top.toFloat(), null)
    } finally {
        if (scaledBitmap !== bitmap) {
            scaledBitmap.recycle()
        }
    }
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
