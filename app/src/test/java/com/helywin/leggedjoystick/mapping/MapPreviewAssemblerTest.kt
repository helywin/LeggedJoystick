package com.helywin.leggedjoystick.mapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class MapPreviewAssemblerTest {
    @Test
    fun chunksMayArriveOutOfOrderAndCompleteOnlyAfterSha256Matches() {
        val bytes = "complete-png-payload".toByteArray()
        val chunks = chunks(11L, bytes, 3)
        val assembler = MapPreviewAssembler()
        assembler.begin(chunks.first().key)

        assertEquals(MapPreviewAssembler.Result.Pending, assembler.accept(chunks[2]))
        assertEquals(MapPreviewAssembler.Result.Pending, assembler.accept(chunks[0]))
        val result = assembler.accept(chunks[1])

        assertTrue(result is MapPreviewAssembler.Result.Complete)
        assertTrue((result as MapPreviewAssembler.Result.Complete).preview.bytes.contentEquals(bytes))
    }

    @Test
    fun duplicateConflictRejectsAssembly() {
        val chunks = chunks(12L, "payload".toByteArray(), 2)
        val assembler = MapPreviewAssembler()
        assembler.begin(chunks.first().key)
        assertEquals(MapPreviewAssembler.Result.Pending, assembler.accept(chunks.first()))

        val result = assembler.accept(chunks.first().copy(data = byteArrayOf(1, 2, 3)))

        assertTrue(result is MapPreviewAssembler.Result.Rejected)
    }

    @Test
    fun oldRequestCannotReplaceCurrentRequest() {
        val old = chunks(20L, "old".toByteArray(), 1).single()
        val current = chunks(21L, "current".toByteArray(), 1).single()
        val assembler = MapPreviewAssembler()
        assembler.begin(current.key)

        assertEquals(MapPreviewAssembler.Result.Ignored, assembler.accept(old))
        assertTrue(assembler.accept(current) is MapPreviewAssembler.Result.Complete)
    }

    @Test
    fun mismatchedMetadataAndHashAreRejected() {
        val chunks = chunks(30L, "preview-data".toByteArray(), 2)
        val assembler = MapPreviewAssembler()
        assembler.begin(chunks.first().key)
        assertEquals(MapPreviewAssembler.Result.Pending, assembler.accept(chunks.first()))

        val mismatch = chunks.last().copy(totalSizeBytes = chunks.last().totalSizeBytes + 1L)
        assertTrue(assembler.accept(mismatch) is MapPreviewAssembler.Result.Rejected)

        val badHash = chunks(31L, "preview-data".toByteArray(), 1).single()
            .copy(sha256 = "0".repeat(64))
        assembler.begin(badHash.key)
        val result = assembler.accept(badHash)
        assertTrue(result is MapPreviewAssembler.Result.Rejected)
        assertTrue((result as MapPreviewAssembler.Result.Rejected).reason.contains("SHA-256"))
    }

    @Test
    fun incompleteAssemblyExpiresWithinBoundedWindow() {
        var now = 100L
        val chunks = chunks(40L, "timeout".toByteArray(), 2)
        val assembler = MapPreviewAssembler(assemblyTimeoutMs = 50L, nowMs = { now })
        assembler.begin(chunks.first().key)
        assembler.accept(chunks.first())

        now = 151L
        assertTrue(assembler.expireIncomplete() is MapPreviewAssembler.Result.Rejected)
    }

    private fun chunks(requestId: Long, bytes: ByteArray, chunkCount: Int): List<MapPreviewChunkModel> {
        val key = MapPreviewRequestKey(requestId, MapIdentityModel("map-a", 7L))
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val chunkSize = (bytes.size + chunkCount - 1) / chunkCount
        return (0 until chunkCount).map { index ->
            val start = index * chunkSize
            val end = minOf(bytes.size, start + chunkSize)
            MapPreviewChunkModel(
                key = key,
                chunkIndex = index,
                chunkCount = chunkCount,
                totalSizeBytes = bytes.size.toLong(),
                sha256 = hash,
                data = bytes.copyOfRange(start, end)
            )
        }
    }
}
