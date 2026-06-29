package com.helywin.leggedjoystick.input.remote.unirc

import com.helywin.leggedjoystick.input.remote.RemoteInputListener
import com.helywin.leggedjoystick.input.remote.RemoteInputNormalizationConfig
import com.helywin.leggedjoystick.input.remote.RemoteInputSnapshot
import com.helywin.leggedjoystick.input.remote.RemoteInputSource
import com.helywin.leggedjoystick.input.remote.RemoteInputSourceDescriptor
import com.helywin.leggedjoystick.input.remote.RemoteInputStatus
import timber.log.Timber
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class UniRcUdpInputConfig(
    val remoteHost: String = "127.0.0.1",
    val remotePort: Int = 19856,
    val localPort: Int = 0,
    val frequency: UniRcChannelFrequency = UniRcChannelFrequency.HZ_50,
    val subscribeRepeatCount: Int = 3,
    val receiveTimeoutMs: Int = 100,
    val resubscribeIntervalMs: Long = 1000L,
    val rawForward: UniRcRawUdpForwardConfig = UniRcRawUdpForwardConfig(),
    val normalization: RemoteInputNormalizationConfig = RemoteInputNormalizationConfig()
)

data class UniRcRawUdpForwardConfig(
    val enabled: Boolean = true,
    val targetHost: String = "127.0.0.1",
    val targetPort: Int = 19857
)

class UniRcUdpInputSource(
    private val config: UniRcUdpInputConfig = UniRcUdpInputConfig()
) : RemoteInputSource {
    override val descriptor = RemoteInputSourceDescriptor(
        id = "unirc_udp",
        displayName = "UniRC UDP",
        transport = "udp"
    )

    private val running = AtomicBoolean(false)

    @Volatile
    private var channel: DatagramChannel? = null

    @Volatile
    private var executor: ExecutorService? = null

    @Volatile
    private var future: Future<*>? = null

    override fun start(listener: RemoteInputListener) {
        if (!running.compareAndSet(false, true)) {
            Timber.w("[UniRC] UDP 输入源已经启动")
            return
        }

        listener.onStatusChanged(descriptor, RemoteInputStatus.STARTING)
        executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "UniRcUdpInput").apply {
                isDaemon = true
            }
        }
        future = executor?.submit { runLoop(listener) }
    }

    override fun stop() {
        running.set(false)
        future?.cancel(true)
        future = null
        channel?.close()
        channel = null

        executor?.shutdownNow()
        try {
            executor?.awaitTermination(800, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            executor = null
        }
    }

    private fun runLoop(listener: RemoteInputListener) {
        val remoteAddress = InetSocketAddress(config.remoteHost, config.remotePort)
        val startAtMs = System.currentTimeMillis()
        var lastFrameAtMs = 0L
        var lastSubscribeAtMs = 0L
        var timeoutEmitted = false
        var firstFrameLogged = false
        var ignoredNonChannelFrameCount = 0
        var invalidFrameCount = 0
        var lastSpeedSelectorRaw: Int? = null
        val frameAssembler = UniRcFrameAssembler()

        try {
            DatagramChannel.open().use { currentChannel ->
                channel = currentChannel
                currentChannel.configureBlocking(false)
                currentChannel.bind(InetSocketAddress(config.localPort))
                val rawForwarder = UniRcRawUdpPacketForwarder(
                    config = config.rawForward,
                    inputLocalPort = currentChannel.socket().localPort,
                    remoteAddress = remoteAddress
                )
                try {
                listener.onStatusChanged(descriptor, RemoteInputStatus.STARTING, "等待 UniRC 通道帧")
                Timber.i(
                    "[UniRC] UDP 输入源启动，本地端口=%d，远端=%s:%d",
                    currentChannel.socket().localPort,
                    config.remoteHost,
                    config.remotePort
                )

                sendSubscribeFrames(currentChannel, remoteAddress)
                lastSubscribeAtMs = System.currentTimeMillis()

                val receiveBuffer = ByteBuffer.allocate(512)
                while (running.get() && !Thread.currentThread().isInterrupted) {
                    receiveBuffer.clear()
                    val senderAddress = currentChannel.receive(receiveBuffer)
                    if (senderAddress != null) {
                        receiveBuffer.flip()
                        val data = ByteArray(receiveBuffer.remaining())
                        receiveBuffer.get(data)
                        rawForwarder.forward(data)

                        frameAssembler.append(data).forEach { rawFrame ->
                            if (UniRcProtocol.isIgnorableNonChannelFrame(rawFrame)) {
                                ignoredNonChannelFrameCount++
                                if (ignoredNonChannelFrameCount == 1 || ignoredNonChannelFrameCount % 200 == 0) {
                                    val info = UniRcProtocol.inspectFrame(rawFrame)
                                    Timber.d(
                                        "[UniRC] 忽略非通道帧，cmd=0x%02X，dataLen=%d，来源=%s，累计=%d",
                                        info?.commandId ?: -1,
                                        info?.dataLength ?: -1,
                                        senderAddress,
                                        ignoredNonChannelFrameCount
                                    )
                                }
                                return@forEach
                            }

                            try {
                                val frame = UniRcProtocol.parseChannelFrame(rawFrame)
                                val speedSelectorChannel = config.normalization.mapping.speedSelector.channel
                                val speedSelectorRaw = frame.channels.getOrNull(speedSelectorChannel - 1)
                                if (speedSelectorRaw != null && speedSelectorRaw != lastSpeedSelectorRaw) {
                                    lastSpeedSelectorRaw = speedSelectorRaw
                                    Timber.i(
                                        "[UniRC] 速度选择通道变化: CH%d=%d",
                                        speedSelectorChannel,
                                        speedSelectorRaw
                                    )
                                }
                                val mapping = UniRcProtocol.mapMovement(frame.channels, config.normalization)
                                val snapshot = RemoteInputSnapshot(
                                    descriptor = descriptor,
                                    movementIntent = mapping.movementIntent,
                                    headControlIntent = mapping.headControlIntent,
                                    speedLevelRequest = mapping.speedLevelRequest,
                                    rawChannels = frame.channels,
                                    normalizedAxes = mapping.normalizedAxes,
                                    sequence = frame.sequence,
                                    receivedAtMs = System.currentTimeMillis()
                                )

                                if (timeoutEmitted) {
                                    Timber.i("[UniRC] 通道数据已恢复，序列=%d", frame.sequence)
                                }
                                lastFrameAtMs = snapshot.receivedAtMs
                                timeoutEmitted = false
                                if (!firstFrameLogged) {
                                    firstFrameLogged = true
                                    Timber.i(
                                        "[UniRC] 已收到首帧通道数据，序列=%d，通道=%s",
                                        frame.sequence,
                                        frame.channels
                                    )
                                }
                                listener.onSnapshot(snapshot)
                            } catch (e: IllegalArgumentException) {
                                invalidFrameCount++
                                if (invalidFrameCount <= 3 || invalidFrameCount % 100 == 0) {
                                    Timber.w(
                                        "[UniRC] 丢弃无效通道帧，原因=%s，长度=%d，来源=%s，数据=%s，累计=%d",
                                        e.message,
                                        rawFrame.size,
                                        senderAddress,
                                        rawFrame.toHexPreview(),
                                        invalidFrameCount
                                    )
                                }
                            }
                        }
                    } else {
                        handleIdleTick(
                            listener = listener,
                            remoteAddress = remoteAddress,
                            currentChannel = currentChannel,
                            startAtMs = startAtMs,
                            lastFrameAtMs = lastFrameAtMs,
                            lastSubscribeAtMs = lastSubscribeAtMs,
                            timeoutEmitted = timeoutEmitted
                        )
                            .also { result ->
                                lastSubscribeAtMs = if (result.subscribedAtMs > 0) {
                                    result.subscribedAtMs
                                } else {
                                    lastSubscribeAtMs
                                }
                                timeoutEmitted = result.timeoutEmitted
                            }
                        Thread.sleep(config.receiveTimeoutMs.coerceIn(10, 20).toLong())
                    }
                }
                } finally {
                    rawForwarder.close()
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            if (running.get()) {
                listener.onStatusChanged(descriptor, RemoteInputStatus.ERROR, e.message.orEmpty())
                Timber.e(e, "[UniRC] UDP 输入源异常退出")
            }
        } finally {
            running.set(false)
            channel = null
            listener.onStatusChanged(descriptor, RemoteInputStatus.STOPPED)
            Timber.i("[UniRC] UDP 输入源已停止")
        }
    }

    private fun handleIdleTick(
        listener: RemoteInputListener,
        remoteAddress: InetSocketAddress,
        currentChannel: DatagramChannel,
        startAtMs: Long,
        lastFrameAtMs: Long,
        lastSubscribeAtMs: Long,
        timeoutEmitted: Boolean
    ): IdleTickResult {
        val now = System.currentTimeMillis()
        var updatedTimeoutEmitted = timeoutEmitted
        var subscribedAtMs = 0L
        val lastValidFrameOrStartAtMs = if (lastFrameAtMs > 0) lastFrameAtMs else startAtMs

        if (now - lastValidFrameOrStartAtMs > config.normalization.timeoutMs && !updatedTimeoutEmitted) {
            listener.onStatusChanged(descriptor, RemoteInputStatus.TIMEOUT, "UniRC 输入超时")
            updatedTimeoutEmitted = true
        }

        if (now - lastSubscribeAtMs > config.resubscribeIntervalMs) {
            sendSubscribeFrames(currentChannel, remoteAddress)
            subscribedAtMs = now
        }

        return IdleTickResult(
            timeoutEmitted = updatedTimeoutEmitted,
            subscribedAtMs = subscribedAtMs
        )
    }

    private fun sendSubscribeFrames(channel: DatagramChannel, remoteAddress: InetSocketAddress) {
        repeat(config.subscribeRepeatCount) { index ->
            val frame = UniRcProtocol.createChannelFrequencyFrame(index, config.frequency)
            channel.send(ByteBuffer.wrap(frame), remoteAddress)
        }
    }

    private fun ByteArray.toHexPreview(maxBytes: Int = 32): String {
        return take(maxBytes).joinToString(separator = " ") { byte ->
            "%02X".format(byte.toInt() and 0xFF)
        }
    }

    private data class IdleTickResult(
        val timeoutEmitted: Boolean,
        val subscribedAtMs: Long
    )
}

private class UniRcRawUdpPacketForwarder(
    config: UniRcRawUdpForwardConfig,
    inputLocalPort: Int,
    remoteAddress: InetSocketAddress
) : AutoCloseable {
    private val targetAddress: InetSocketAddress?
    private val forwardChannel: DatagramChannel?
    private var failureCount = 0

    init {
        val targetHost = config.targetHost.trim().ifEmpty { "127.0.0.1" }
        val targetPort = config.targetPort
        targetAddress = when {
            !config.enabled -> null
            targetPort !in 1..65535 -> {
                Timber.w("[UniRC] 原始 UDP 转发端口无效: %d", targetPort)
                null
            }
            isLoopbackHost(targetHost) && targetPort == inputLocalPort -> {
                Timber.w("[UniRC] 原始 UDP 转发端口不能等于输入本地端口: %d", targetPort)
                null
            }
            isRemoteTarget(targetHost, targetPort, remoteAddress) -> {
                Timber.w("[UniRC] 原始 UDP 转发目标不能等于 UniRC 远端: %s:%d", targetHost, targetPort)
                null
            }
            else -> InetSocketAddress(targetHost, targetPort)
        }
        forwardChannel = targetAddress?.let { target ->
            try {
                DatagramChannel.open().also { channel ->
                    channel.configureBlocking(false)
                    Timber.i("[UniRC] 原始 UDP 转发已开启，目标=%s", target)
                }
            } catch (e: Exception) {
                Timber.w(e, "[UniRC] 原始 UDP 转发 socket 打开失败，目标=%s", target)
                null
            }
        }
    }

    fun forward(rawDatagram: ByteArray) {
        val channel = forwardChannel ?: return
        val target = targetAddress ?: return
        try {
            channel.send(ByteBuffer.wrap(rawDatagram), target)
        } catch (e: Exception) {
            failureCount++
            if (failureCount <= 3 || failureCount % 100 == 0) {
                Timber.w(e, "[UniRC] 原始 UDP 转发失败，目标=%s，累计=%d", target, failureCount)
            }
        }
    }

    override fun close() {
        try {
            forwardChannel?.close()
        } catch (e: Exception) {
            Timber.w(e, "[UniRC] 关闭原始 UDP 转发 socket 失败")
        }
    }

    private fun isLoopbackHost(host: String): Boolean {
        return host == "127.0.0.1" || host == "localhost" || host == "::1"
    }

    private fun isRemoteTarget(host: String, port: Int, remoteAddress: InetSocketAddress): Boolean {
        if (port != remoteAddress.port) return false
        return host == remoteAddress.hostString ||
            (isLoopbackHost(host) && remoteAddress.address?.isLoopbackAddress == true)
    }
}
