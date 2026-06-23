package com.helywin.leggedjoystick.input.remote.mock

import com.helywin.leggedjoystick.input.remote.RemoteInputListener
import com.helywin.leggedjoystick.input.remote.MovementIntent
import com.helywin.leggedjoystick.input.remote.RemoteInputNormalizationConfig
import com.helywin.leggedjoystick.input.remote.RemoteInputSnapshot
import com.helywin.leggedjoystick.input.remote.RemoteInputSource
import com.helywin.leggedjoystick.input.remote.RemoteInputSourceDescriptor
import com.helywin.leggedjoystick.input.remote.RemoteInputStatus
import com.helywin.leggedjoystick.input.remote.normalizeRemoteChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class MockRemoteInputConfig(
    val intervalMs: Long = 100L,
    val normalization: RemoteInputNormalizationConfig = RemoteInputNormalizationConfig()
)

class MockRemoteInputSource(
    private val config: MockRemoteInputConfig = MockRemoteInputConfig()
) : RemoteInputSource {
    override val descriptor = RemoteInputSourceDescriptor(
        id = "mock_remote",
        displayName = "工程 Mock",
        transport = "mock"
    )

    private val running = AtomicBoolean(false)

    @Volatile
    private var executor: ExecutorService? = null

    @Volatile
    private var future: Future<*>? = null

    override fun start(listener: RemoteInputListener) {
        if (!running.compareAndSet(false, true)) return

        listener.onStatusChanged(descriptor, RemoteInputStatus.STARTING, "工程 Mock 输入")
        executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "MockRemoteInput").apply {
                isDaemon = true
            }
        }
        future = executor?.submit { runLoop(listener) }
    }

    override fun stop() {
        running.set(false)
        future?.cancel(true)
        future = null

        executor?.shutdownNow()
        try {
            executor?.awaitTermination(500, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            executor = null
        }
    }

    private fun runLoop(listener: RemoteInputListener) {
        var sequence = 0
        try {
            while (running.get() && !Thread.currentThread().isInterrupted) {
                val channels = List(16) { config.normalization.center }
                listener.onSnapshot(
                    RemoteInputSnapshot(
                        descriptor = descriptor,
                        movementIntent = MovementIntent.ZERO,
                        rawChannels = channels,
                        normalizedAxes = normalizedAxes(channels),
                        sequence = sequence++,
                        receivedAtMs = System.currentTimeMillis()
                    )
                )
                Thread.sleep(config.intervalMs.coerceAtLeast(20L))
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            running.set(false)
            listener.onStatusChanged(descriptor, RemoteInputStatus.STOPPED)
        }
    }

    private fun normalizedAxes(channels: List<Int>): Map<String, Float> {
        return mapOf(
            "forward" to normalizeRemoteChannel(
                raw = channels[config.normalization.mapping.forward.channel - 1],
                axisMapping = config.normalization.mapping.forward,
                config = config.normalization
            ),
            "strafeRight" to normalizeRemoteChannel(
                raw = channels[config.normalization.mapping.strafeRight.channel - 1],
                axisMapping = config.normalization.mapping.strafeRight,
                config = config.normalization
            ),
            "yawRight" to normalizeRemoteChannel(
                raw = channels[config.normalization.mapping.yawRight.channel - 1],
                axisMapping = config.normalization.mapping.yawRight,
                config = config.normalization
            )
        )
    }
}
