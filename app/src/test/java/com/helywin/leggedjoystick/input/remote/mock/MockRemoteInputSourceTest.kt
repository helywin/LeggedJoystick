package com.helywin.leggedjoystick.input.remote.mock

import com.helywin.leggedjoystick.input.remote.RemoteInputListener
import com.helywin.leggedjoystick.input.remote.RemoteInputSnapshot
import com.helywin.leggedjoystick.input.remote.RemoteInputSourceDescriptor
import com.helywin.leggedjoystick.input.remote.RemoteInputStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class MockRemoteInputSourceTest {
    @Test
    fun start_emitsCenteredChannelsAndZeroMovement() {
        val listener = RecordingRemoteInputListener()
        val source = MockRemoteInputSource(
            MockRemoteInputConfig(intervalMs = 20L)
        )

        try {
            source.start(listener)

            val snapshot = listener.awaitSnapshot()
            assertEquals("工程 Mock", snapshot.descriptor.displayName)
            assertEquals(List(16) { 1500 }, snapshot.rawChannels)
            assertTrue(snapshot.movementIntent.isZero)
            assertEquals(0f, snapshot.normalizedAxes["forward"] ?: 1f, FLOAT_DELTA)
            assertEquals(0f, snapshot.normalizedAxes["strafeRight"] ?: 1f, FLOAT_DELTA)
            assertEquals(0f, snapshot.normalizedAxes["yawRight"] ?: 1f, FLOAT_DELTA)
        } finally {
            source.stop()
        }
    }

    @Test
    fun stop_emitsStoppedStatus() {
        val listener = RecordingRemoteInputListener()
        val source = MockRemoteInputSource(
            MockRemoteInputConfig(intervalMs = 20L)
        )

        source.start(listener)
        listener.awaitSnapshot()
        source.stop()

        assertEquals(RemoteInputStatus.STOPPED, listener.awaitStatus(RemoteInputStatus.STOPPED).status)
    }

    private class RecordingRemoteInputListener : RemoteInputListener {
        private val statuses = LinkedBlockingQueue<StatusEvent>()
        private val snapshots = LinkedBlockingQueue<RemoteInputSnapshot>()

        override fun onStatusChanged(
            descriptor: RemoteInputSourceDescriptor,
            status: RemoteInputStatus,
            message: String
        ) {
            statuses.offer(StatusEvent(status = status, message = message))
        }

        override fun onSnapshot(snapshot: RemoteInputSnapshot) {
            snapshots.offer(snapshot)
        }

        fun awaitStatus(status: RemoteInputStatus, timeoutMs: Long = 1200L): StatusEvent {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val event = statuses.poll(50, TimeUnit.MILLISECONDS) ?: continue
                if (event.status == status) return event
            }
            throw AssertionError("未收到输入状态: $status")
        }

        fun awaitSnapshot(timeoutMs: Long = 1200L): RemoteInputSnapshot {
            return snapshots.poll(timeoutMs, TimeUnit.MILLISECONDS)
                ?: throw AssertionError("未收到输入快照")
        }
    }

    private data class StatusEvent(
        val status: RemoteInputStatus,
        val message: String
    )

    private companion object {
        const val FLOAT_DELTA = 0.0001f
    }
}
