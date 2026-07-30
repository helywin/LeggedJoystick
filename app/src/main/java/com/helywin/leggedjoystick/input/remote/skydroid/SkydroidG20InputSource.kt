package com.helywin.leggedjoystick.input.remote.skydroid

import android.content.Context
import com.helywin.leggedjoystick.BuildConfig
import com.helywin.leggedjoystick.input.remote.HeadControlIntent
import com.helywin.leggedjoystick.input.remote.MovementIntent
import com.helywin.leggedjoystick.input.remote.RemoteInputListener
import com.helywin.leggedjoystick.input.remote.RemoteInputNormalizationConfig
import com.helywin.leggedjoystick.input.remote.RemoteInputSnapshot
import com.helywin.leggedjoystick.input.remote.RemoteInputSource
import com.helywin.leggedjoystick.input.remote.RemoteInputSourceDescriptor
import com.helywin.leggedjoystick.input.remote.RemoteInputStatus
import com.helywin.leggedjoystick.input.remote.normalizeRemoteChannel
import com.skydroid.rcsdk.KeyManager
import com.skydroid.rcsdk.RCSDKManager
import com.skydroid.rcsdk.SDKManagerCallBack
import com.skydroid.rcsdk.common.DeviceType
import com.skydroid.rcsdk.common.callback.CompletionCallbackWith
import com.skydroid.rcsdk.common.error.SkyException
import com.skydroid.rcsdk.key.RemoteControllerKey
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class SkydroidG20InputConfig(
    val pollIntervalMs: Long = 100L,
    val reconnectDelayMs: Long = 2_000L,
    val normalization: RemoteInputNormalizationConfig = RemoteInputNormalizationConfig(
        min = CHANNEL_MIN,
        center = CHANNEL_CENTER,
        max = CHANNEL_MAX
    )
) {
    init {
        require(pollIntervalMs >= 100L) { "G20 通道读取周期不得短于 100ms" }
        require(reconnectDelayMs > 0L) { "G20 重连间隔必须大于 0" }
    }

    companion object {
        const val CHANNEL_MIN = 900
        const val CHANNEL_CENTER = 1500
        const val CHANNEL_MAX = 2100
        const val CHANNEL_COUNT = 16
    }
}

object SkydroidG20ChannelMapper {
    fun toSnapshot(
        channels: List<Int>,
        sequence: Int,
        receivedAtMs: Long,
        descriptor: RemoteInputSourceDescriptor,
        normalization: RemoteInputNormalizationConfig
    ): RemoteInputSnapshot {
        val paddedChannels = channels
            .take(SkydroidG20InputConfig.CHANNEL_COUNT)
            .let { current ->
                current + List(SkydroidG20InputConfig.CHANNEL_COUNT - current.size) {
                    normalization.center
                }
            }
        val mapping = normalization.mapping

        return RemoteInputSnapshot(
            descriptor = descriptor,
            movementIntent = MovementIntent(
                forward = normalizeRemoteChannel(
                    paddedChannels.channel(mapping.forward.channel, normalization.center),
                    mapping.forward,
                    normalization
                ),
                strafeRight = normalizeRemoteChannel(
                    paddedChannels.channel(mapping.strafeRight.channel, normalization.center),
                    mapping.strafeRight,
                    normalization
                ),
                yawRight = normalizeRemoteChannel(
                    paddedChannels.channel(mapping.yawRight.channel, normalization.center),
                    mapping.yawRight,
                    normalization
                )
            ).clamped(),
            headControlIntent = HeadControlIntent(
                pitchUp = normalizeRemoteChannel(
                    paddedChannels.channel(mapping.headPitchUp.channel, normalization.center),
                    mapping.headPitchUp,
                    normalization
                )
            ).clamped(),
            speedLevelRequest = null,
            rawChannels = paddedChannels,
            normalizedAxes = mapOf(
                "forward" to normalizeRemoteChannel(
                    paddedChannels.channel(mapping.forward.channel, normalization.center),
                    mapping.forward,
                    normalization
                ),
                "strafe_right" to normalizeRemoteChannel(
                    paddedChannels.channel(mapping.strafeRight.channel, normalization.center),
                    mapping.strafeRight,
                    normalization
                ),
                "yaw_right" to normalizeRemoteChannel(
                    paddedChannels.channel(mapping.yawRight.channel, normalization.center),
                    mapping.yawRight,
                    normalization
                ),
                "head_pitch_up" to normalizeRemoteChannel(
                    paddedChannels.channel(mapping.headPitchUp.channel, normalization.center),
                    mapping.headPitchUp,
                    normalization
                )
            ),
            sequence = sequence,
            receivedAtMs = receivedAtMs
        )
    }

    private fun List<Int>.channel(channel: Int, fallback: Int): Int {
        return getOrNull(channel - 1) ?: fallback
    }
}

class SkydroidG20InputSource(
    applicationContext: Context,
    private val config: SkydroidG20InputConfig = SkydroidG20InputConfig()
) : RemoteInputSource {
    override val descriptor = RemoteInputSourceDescriptor(
        id = "skydroid_g20_rcsdk",
        displayName = "云卓 G20 RCSDK",
        transport = "rcsdk"
    )

    private val context = applicationContext.applicationContext
    private val running = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)
    private val readPending = AtomicBoolean(false)
    private val runningStatusEmitted = AtomicBoolean(false)
    private val timeoutEmitted = AtomicBoolean(false)
    private val reconnectScheduled = AtomicBoolean(false)
    private val sequence = AtomicInteger(0)
    private val lastFrameAtMs = AtomicLong(0L)
    private val connectedAtMs = AtomicLong(0L)
    private val readFailureCount = AtomicInteger(0)

    @Volatile
    private var listener: RemoteInputListener? = null

    @Volatile
    private var executor: ScheduledExecutorService? = null

    @Volatile
    private var pollFuture: ScheduledFuture<*>? = null

    private val sdkCallback = object : SDKManagerCallBack {
        override fun onRcConnected() {
            if (!running.get()) return

            val deviceType = RCSDKManager.getDeviceType()
            if (deviceType != DeviceType.G20) {
                connected.set(false)
                listener?.onStatusChanged(
                    descriptor,
                    RemoteInputStatus.ERROR,
                    "RCSDK 设备类型不匹配: $deviceType"
                )
                Timber.e("[G20] RCSDK 设备类型不匹配: %s", deviceType)
                return
            }

            connected.set(true)
            connectedAtMs.set(System.currentTimeMillis())
            reconnectScheduled.set(false)
            readFailureCount.set(0)
            Timber.i("[G20] RCSDK 已连接，开始读取摇杆通道")
            startPolling()
        }

        override fun onRcConnectFail(e: SkyException?) {
            if (!running.get()) return
            connected.set(false)
            stopPolling()
            listener?.onStatusChanged(
                descriptor,
                RemoteInputStatus.ERROR,
                "G20 RCSDK 连接失败: ${e ?: "未知错误"}"
            )
            Timber.w("[G20] RCSDK 连接失败: %s", e)
            scheduleReconnect()
        }

        override fun onRcDisconnect() {
            if (!running.get()) return
            connected.set(false)
            stopPolling()
            listener?.onStatusChanged(
                descriptor,
                RemoteInputStatus.ERROR,
                "G20 RCSDK 已断开"
            )
            Timber.w("[G20] RCSDK 已断开")
            scheduleReconnect()
        }
    }

    override fun start(listener: RemoteInputListener) {
        if (!running.compareAndSet(false, true)) {
            Timber.w("[G20] RCSDK 输入源已经启动")
            return
        }

        this.listener = listener
        resetRuntimeState()
        executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "SkydroidG20Input").apply {
                isDaemon = true
            }
        }
        listener.onStatusChanged(descriptor, RemoteInputStatus.STARTING, "正在连接 G20 RCSDK")

        RCSDKManager.setDebug(BuildConfig.DEBUG)
        RCSDKManager.setMainThreadCallBack(false)
        RCSDKManager.initSDK(context, sdkCallback)
        requestConnect()
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) return

        stopPolling()
        reconnectScheduled.set(false)
        runCatching { RCSDKManager.disconnectRC() }
            .onFailure { error -> Timber.w(error, "[G20] RCSDK 断开请求失败") }
        executor?.shutdownNow()
        executor = null
        connected.set(false)
        readPending.set(false)
        listener?.onStatusChanged(descriptor, RemoteInputStatus.STOPPED, "G20 输入已停止")
        listener = null
        resetRuntimeState()
    }

    private fun requestConnect() {
        if (!running.get()) return
        runCatching { RCSDKManager.connectToRC() }
            .onFailure { error ->
                listener?.onStatusChanged(
                    descriptor,
                    RemoteInputStatus.ERROR,
                    "G20 RCSDK 连接请求失败: ${error.message.orEmpty()}"
                )
                Timber.w(error, "[G20] RCSDK 连接请求失败")
                scheduleReconnect()
            }
    }

    private fun startPolling() {
        stopPolling()
        pollFuture = executor?.scheduleWithFixedDelay(
            ::pollChannels,
            0L,
            config.pollIntervalMs,
            TimeUnit.MILLISECONDS
        )
    }

    private fun stopPolling() {
        pollFuture?.cancel(false)
        pollFuture = null
        readPending.set(false)
    }

    private fun pollChannels() {
        if (!running.get() || !connected.get()) return

        emitTimeoutIfNeeded()
        if (!readPending.compareAndSet(false, true)) return

        KeyManager.get(
            RemoteControllerKey.KeyChannels,
            object : CompletionCallbackWith<IntArray> {
                override fun onSuccess(value: IntArray?) {
                    readPending.set(false)
                    if (!running.get() || !connected.get() || value == null) return

                    val now = System.currentTimeMillis()
                    lastFrameAtMs.set(now)
                    readFailureCount.set(0)
                    timeoutEmitted.set(false)
                    val snapshot = SkydroidG20ChannelMapper.toSnapshot(
                        channels = value.toList(),
                        sequence = sequence.incrementAndGet(),
                        receivedAtMs = now,
                        descriptor = descriptor,
                        normalization = config.normalization
                    )
                    listener?.onSnapshot(snapshot)
                    if (runningStatusEmitted.compareAndSet(false, true)) {
                        listener?.onStatusChanged(
                            descriptor,
                            RemoteInputStatus.RUNNING,
                            "G20 摇杆通道已连接"
                        )
                        Timber.i("[G20] 已收到首帧摇杆通道: %s", snapshot.rawChannels)
                    }
                }

                override fun onFailure(e: SkyException) {
                    readPending.set(false)
                    val failures = readFailureCount.incrementAndGet()
                    if (failures == 1 || failures % 20 == 0) {
                        Timber.w("[G20] RCSDK 通道读取失败，连续次数=%d: %s", failures, e)
                    }
                    emitTimeoutIfNeeded()
                }
            }
        )
    }

    private fun emitTimeoutIfNeeded() {
        val referenceAtMs = lastFrameAtMs.get().takeIf { it > 0L } ?: connectedAtMs.get()
        if (referenceAtMs <= 0L) return
        if (System.currentTimeMillis() - referenceAtMs < config.normalization.timeoutMs) return
        if (!timeoutEmitted.compareAndSet(false, true)) return

        runningStatusEmitted.set(false)
        listener?.onStatusChanged(
            descriptor,
            RemoteInputStatus.TIMEOUT,
            "G20 摇杆输入超时"
        )
        Timber.w("[G20] 摇杆输入超时")
        connected.set(false)
        stopPolling()
        runCatching { RCSDKManager.disconnectRC() }
            .onFailure { error -> Timber.w(error, "[G20] 输入超时后断开 RCSDK 失败") }
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        val currentExecutor = executor ?: return
        if (!running.get() || !reconnectScheduled.compareAndSet(false, true)) return

        currentExecutor.schedule(
            {
                reconnectScheduled.set(false)
                requestConnect()
            },
            config.reconnectDelayMs,
            TimeUnit.MILLISECONDS
        )
    }

    private fun resetRuntimeState() {
        connected.set(false)
        readPending.set(false)
        runningStatusEmitted.set(false)
        timeoutEmitted.set(false)
        sequence.set(0)
        lastFrameAtMs.set(0L)
        connectedAtMs.set(0L)
        readFailureCount.set(0)
    }
}
