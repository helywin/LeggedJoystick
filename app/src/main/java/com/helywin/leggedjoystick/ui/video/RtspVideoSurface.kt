package com.helywin.leggedjoystick.ui.video

import android.content.ContentValues
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Surface
import android.view.TextureView
import android.view.View
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
import timber.log.Timber
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer
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
private const val IJK_RTSP_TIMEOUT_US = 5_000_000L
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
    private var ijkInitialized = false
    private var appContext: Context? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var linkStateReceiver: BroadcastReceiver? = null

    fun player(context: Context, slot: RtspVideoSlot): RtspVideoPlayer {
        synchronized(lock) {
            ensureIjkInitialized()
            return players.getOrPut(slot) { RtspVideoPlayer(slot) }
        }
    }

    private fun ensureIjkInitialized() {
        if (ijkInitialized) return

        try {
            IjkMediaPlayer.loadLibrariesOnce(null)
            IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_SILENT)
            ijkInitialized = true
            Timber.i("[RtspVideoRuntime] 进程级 IJKPlayer 已初始化")
        } catch (e: Throwable) {
            Timber.e(e, "[RtspVideoRuntime] IJKPlayer 初始化失败")
            throw e
        }
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
        }
    }
}

interface RtspVideoPlayerListener {
    fun onOpening()
    fun onPlaying()
    fun onStopped()
    fun onError(reason: String)
}

class RtspVideoPlayer internal constructor(
    private val slot: RtspVideoSlot
) {
    private var mediaPlayer: IjkMediaPlayer? = null
    private var attachedTextureView: TextureView? = null
    private var attachedSurfaceTexture: SurfaceTexture? = null
    private var attachedSurface: Surface? = null
    private var textureListener: TextureView.SurfaceTextureListener? = null
    private var layoutChangeListener: View.OnLayoutChangeListener? = null
    private var playbackListener: RtspVideoPlayerListener? = null
    private var attached = false
    private var released = false
    private var currentUrl: String? = null
    private var currentScaleMode = RtspVideoScaleMode.BestFit
    private var videoWidth = 0
    private var videoHeight = 0

    val isAttached: Boolean
        get() = attached && !released && attachedSurface?.isValid == true

    fun setEventListener(listener: RtspVideoPlayerListener?) {
        playbackListener = listener
    }

    fun attach(
        textureView: TextureView,
        scaleMode: RtspVideoScaleMode,
        onSurfaceReady: () -> Unit
    ): Boolean {
        check(!released) { "RTSP 播放器已释放: $slot" }

        currentScaleMode = scaleMode
        val shouldAttach = attachedTextureView !== textureView || textureListener == null
        if (shouldAttach) {
            detachTextureView("重新绑定视频输出")
            attachedTextureView = textureView
            installTextureCallbacks(textureView, onSurfaceReady)
            Timber.i("[RtspVideoRuntime] %s 视频输出已绑定到 TextureView", slot)
        }

        applyVideoTransform()
        val surfaceBound = if (textureView.isAvailable) {
            bindSurface(textureView.surfaceTexture, "TextureView 已可用")
        } else {
            false
        }
        if (surfaceBound) {
            onSurfaceReady()
        }
        return shouldAttach || surfaceBound
    }

    fun playUrl(rtspUrl: String, forceReload: Boolean = false) {
        check(!released) { "RTSP 播放器已释放: $slot" }
        if (!attached) return

        val normalizedUrl = rtspUrl.trim()
        if (normalizedUrl.isBlank()) {
            stopPlayback("空视频地址")
            return
        }
        if (!forceReload && currentUrl == normalizedUrl && mediaPlayer.safeIsPlaying()) {
            return
        }

        closeMediaPlayer(if (forceReload) "网络变化后重拉视频流" else "切换视频流")
        currentUrl = normalizedUrl
        videoWidth = 0
        videoHeight = 0

        val player = createMediaPlayer(normalizedUrl)
        mediaPlayer = player
        attachedSurface?.takeIf { it.isValid }?.let { player.setSurface(it) }
        playbackListener?.onOpening()
        player.prepareAsync()
        Timber.i("[RtspVideoRuntime] %s 开始加载 IJK RTSP 流: %s, forceReload=%s", slot, normalizedUrl, forceReload)
    }

    fun stopPlayback(reason: String) {
        if (released) return
        closeMediaPlayer(reason)
        currentUrl = null
        playbackListener?.onStopped()
    }

    fun stopAndDetach(reason: String) {
        stopPlayback(reason)
        detachTextureView(reason)
    }

    private fun createMediaPlayer(rtspUrl: String): IjkMediaPlayer {
        return IjkMediaPlayer().apply {
            setLogEnabled(false)
            setVolume(0f, 0f)
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "tcp")
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "stimeout", IJK_RTSP_TIMEOUT_US)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", IJK_RTSP_TIMEOUT_US)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 32L * 1024L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 5L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48L)
            setOnPreparedListener { player ->
                try {
                    player.start()
                    Timber.i("[RtspVideoRuntime] %s IJK RTSP 已准备并开始播放", slot)
                } catch (e: Exception) {
                    Timber.e(e, "[RtspVideoRuntime] %s IJK RTSP start 失败", slot)
                    playbackListener?.onError("IJK start 失败")
                }
            }
            setOnInfoListener { _, what, extra ->
                handlePlayerInfo(what, extra)
                false
            }
            setOnErrorListener { _, what, extra ->
                val reason = "IJK 播放错误 what=$what extra=$extra"
                Timber.e("[RtspVideoRuntime] %s %s", slot, reason)
                playbackListener?.onError(reason)
                true
            }
            setOnCompletionListener {
                Timber.w("[RtspVideoRuntime] %s IJK RTSP 流结束", slot)
                playbackListener?.onError("IJK 视频流结束")
            }
            setOnVideoSizeChangedListener { player, width, height, _, _ ->
                this@RtspVideoPlayer.videoWidth = width.takeIf { it > 0 } ?: player.videoWidth
                this@RtspVideoPlayer.videoHeight = height.takeIf { it > 0 } ?: player.videoHeight
                applyVideoTransform()
            }
            setDataSource(rtspUrl)
        }
    }

    private fun handlePlayerInfo(what: Int, extra: Int) {
        when (what) {
            IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START,
            IMediaPlayer.MEDIA_INFO_VIDEO_DECODED_START -> {
                playbackListener?.onPlaying()
                Timber.i("[RtspVideoRuntime] %s IJK 首帧开始渲染: info=%s extra=%s", slot, what, extra)
            }
            IMediaPlayer.MEDIA_INFO_BUFFERING_START -> playbackListener?.onOpening()
            IMediaPlayer.MEDIA_INFO_BUFFERING_END -> playbackListener?.onPlaying()
            else -> Unit
        }
    }

    private fun closeMediaPlayer(reason: String) {
        val player = mediaPlayer ?: return
        mediaPlayer = null
        if (released) return
        try {
            player.setOnPreparedListener(null)
            player.setOnInfoListener(null)
            player.setOnErrorListener(null)
            player.setOnCompletionListener(null)
            player.setOnVideoSizeChangedListener(null)
            player.setSurface(null)
            try {
                player.stop()
            } catch (e: Exception) {
                Timber.d(e, "[RtspVideoRuntime] %s IJK stop 忽略异常: %s", slot, reason)
            }
            player.release()
        } catch (e: Exception) {
            Timber.w(e, "[RtspVideoRuntime] %s 释放 IJK 播放器失败: %s", slot, reason)
        }
    }

    private fun installTextureCallbacks(
        textureView: TextureView,
        onSurfaceReady: () -> Unit
    ) {
        val listener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                if (bindSurface(surface, "TextureView 可用")) {
                    onSurfaceReady()
                }
                applyVideoTransform()
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                applyVideoTransform()
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                stopPlayback("TextureView 销毁")
                releaseSurface("TextureView 销毁")
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
        val onLayoutChange = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyVideoTransform()
        }
        textureListener = listener
        layoutChangeListener = onLayoutChange
        textureView.surfaceTextureListener = listener
        textureView.addOnLayoutChangeListener(onLayoutChange)
    }

    private fun bindSurface(surfaceTexture: SurfaceTexture?, reason: String): Boolean {
        if (surfaceTexture == null || released) return false
        if (attachedSurfaceTexture === surfaceTexture && attachedSurface?.isValid == true) {
            return false
        }

        releaseSurface("更换视频输出")
        attachedSurfaceTexture = surfaceTexture
        attachedSurface = Surface(surfaceTexture)
        attached = true
        mediaPlayer?.setSurface(attachedSurface)
        Timber.i("[RtspVideoRuntime] %s IJK Surface 已绑定: %s", slot, reason)
        return true
    }

    private fun detachTextureView(reason: String) {
        attachedTextureView?.let { textureView ->
            if (textureView.surfaceTextureListener === textureListener) {
                textureView.surfaceTextureListener = null
            }
            layoutChangeListener?.let(textureView::removeOnLayoutChangeListener)
        }
        textureListener = null
        layoutChangeListener = null
        attachedTextureView = null
        releaseSurface(reason)
    }

    private fun releaseSurface(reason: String) {
        try {
            mediaPlayer?.setSurface(null)
        } catch (e: Exception) {
            Timber.d(e, "[RtspVideoRuntime] %s 清理 IJK Surface 时忽略异常: %s", slot, reason)
        }
        attachedSurface?.release()
        attachedSurface = null
        attachedSurfaceTexture = null
        attached = false
    }

    private fun applyVideoTransform() {
        val textureView = attachedTextureView ?: return
        val viewWidth = textureView.width
        val viewHeight = textureView.height
        if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            textureView.setTransform(null)
            return
        }

        val scaleX = viewWidth.toFloat() / videoWidth.toFloat()
        val scaleY = viewHeight.toFloat() / videoHeight.toFloat()
        val scale = when (currentScaleMode) {
            RtspVideoScaleMode.BestFit -> minOf(scaleX, scaleY)
            RtspVideoScaleMode.Fill -> maxOf(scaleX, scaleY)
        }
        val scaledWidth = videoWidth * scale
        val scaledHeight = videoHeight * scale
        val matrix = Matrix().apply {
            setScale(
                scaledWidth / viewWidth.toFloat(),
                scaledHeight / viewHeight.toFloat(),
                viewWidth / 2f,
                viewHeight / 2f
            )
        }
        textureView.setTransform(matrix)
    }

    private fun IjkMediaPlayer?.safeIsPlaying(): Boolean {
        return try {
            this?.isPlaying == true
        } catch (e: Exception) {
            false
        }
    }

    fun release() {
        if (released) return
        stopAndDetach("释放播放器")
        setEventListener(null)
        released = true
    }
}

@Composable
fun RtspVideoSurface(
    rtspUrl: String,
    modifier: Modifier = Modifier,
    slot: RtspVideoSlot = RtspVideoSlot.Main,
    scaleMode: RtspVideoScaleMode = RtspVideoScaleMode.BestFit,
    showStatus: Boolean = true,
    onTextureViewReady: (TextureView?) -> Unit = {}
) {
    val isInPreview = LocalInspectionMode.current
    val latestTextureCallback by rememberUpdatedState(onTextureViewReady)

    if (isInPreview) {
        DisposableEffect(Unit) {
            latestTextureCallback(null)
            onDispose {
                latestTextureCallback(null)
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
                    latestTextureCallback(null)
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
        val eventListener = object : RtspVideoPlayerListener {
            override fun onOpening() {
                runOnMain {
                    playbackState = VideoPlaybackState.LOADING
                }
            }

            override fun onPlaying() {
                runOnMain {
                    retryAttempt = 0
                    errorMessage = null
                    playbackState = VideoPlaybackState.PLAYING
                }
            }

            override fun onStopped() {
                runOnMain {
                    playbackState = VideoPlaybackState.IDLE
                }
            }

            override fun onError(reason: String) {
                runOnMain {
                    player.stopPlayback(reason)
                    playbackState = VideoPlaybackState.LOADING
                    errorMessage = "正在重连视频流"
                    Timber.e("[RtspVideoSurface] IJK 播放异常，继续重连: %s, reason=%s", latestRtspUrl, reason)
                    scheduleVideoRetry(reason)
                }
            }
        }
        player.setEventListener(eventListener)

        onDispose {
            player.setEventListener(null)
            player.stopAndDetach("RTSP 组件离开组合")
            latestTextureCallback(null)
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
            Timber.e(e, "[RtspVideoSurface] IJK RTSP 流加载失败，继续重试: %s", rtspUrl)
            scheduleVideoRetry("IJK RTSP 流加载异常")
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
                TextureView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    isOpaque = true
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { textureView ->
                latestTextureCallback(textureView)
                player.attach(textureView, scaleMode) {
                    attachGeneration++
                }
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

fun captureRtspSurfaceSnapshot(
    context: Context,
    primaryTextureView: TextureView?,
    secondaryTextureView: TextureView?,
    onFinished: (Boolean) -> Unit
) {
    val primary = captureTextureFrame(primaryTextureView, "主背景")
    val secondary = captureTextureFrame(secondaryTextureView, "小视频")
    val combinedBitmap = createStackedSnapshot(primary, secondary)
    primary.bitmap?.recycle()
    secondary.bitmap?.recycle()
    saveBitmapToGallery(context, combinedBitmap, onFinished)
}

private data class VideoSnapshotFrame(
    val bitmap: Bitmap?,
    val sourceWidth: Int,
    val sourceHeight: Int
)

private fun captureTextureFrame(
    textureView: TextureView?,
    label: String
): VideoSnapshotFrame {
    if (textureView == null || !textureView.isAvailable || textureView.width <= 0 || textureView.height <= 0) {
        Timber.w("[RtspVideoSurface] %s视频 TextureView 未准备好，截图使用黑色占位", label)
        return VideoSnapshotFrame(null, DEFAULT_SNAPSHOT_WIDTH, DEFAULT_SNAPSHOT_HEIGHT)
    }

    val width = textureView.width
    val height = textureView.height
    try {
        val bitmap = textureView.getBitmap(width, height)
        return if (bitmap != null) {
            VideoSnapshotFrame(bitmap, width, height)
        } else {
            Timber.e("[RtspVideoSurface] %s视频 TextureView 取帧失败，截图使用黑色占位", label)
            VideoSnapshotFrame(null, width, height)
        }
    } catch (e: Exception) {
        Timber.e(e, "[RtspVideoSurface] %s视频 TextureView 取帧异常，截图使用黑色占位", label)
        return VideoSnapshotFrame(null, width, height)
    }
}

private fun createStackedSnapshot(
    primary: VideoSnapshotFrame,
    secondary: VideoSnapshotFrame
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

private fun scaledSnapshotHeight(frame: VideoSnapshotFrame, targetWidth: Int): Int {
    val sourceWidth = frame.sourceWidth.takeIf { it > 0 } ?: DEFAULT_SNAPSHOT_WIDTH
    val sourceHeight = frame.sourceHeight.takeIf { it > 0 } ?: DEFAULT_SNAPSHOT_HEIGHT
    return (targetWidth.toFloat() * sourceHeight / sourceWidth).roundToInt().coerceAtLeast(1)
}

private fun drawSnapshotFrame(
    canvas: Canvas,
    frame: VideoSnapshotFrame,
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
