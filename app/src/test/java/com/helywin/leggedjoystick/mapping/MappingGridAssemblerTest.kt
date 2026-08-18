package com.helywin.leggedjoystick.mapping

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.zip.Deflater

class MappingGridAssemblerTest {
    @Test
    fun accept_reassemblesOutOfOrderChunksAndInflatesCells() {
        val cells = byteArrayOf(-1, 0, 25, 100, 1, 2)
        val chunks = chunksFor(cells, sequence = 7L, chunkBytes = 3)
        val assembler = MappingGridAssembler()

        val order = chunks.indices.reversed().toList()
        order.dropLast(1).forEach { index ->
            assertEquals(MappingGridAssembler.Result.Pending, assembler.accept(chunks[index]))
        }
        val result = assembler.accept(chunks[order.last()]) as MappingGridAssembler.Result.Complete

        assertEquals(7L, result.frame.metadata.frameSequence)
        assertArrayEquals(cells, result.frame.cells)
    }

    @Test
    fun accept_hashMismatchKeepsFrameRejected() {
        val chunks = chunksFor(byteArrayOf(-1, 0, 100), sequence = 1L, chunkBytes = 1024)
        val bad = chunks.single().copy(
            metadata = chunks.single().metadata.copy(sha256 = "0".repeat(64))
        )

        val result = MappingGridAssembler().accept(bad)

        assertTrue(result is MappingGridAssembler.Result.Rejected)
        assertTrue((result as MappingGridAssembler.Result.Rejected).reason.contains("SHA-256"))
    }

    @Test
    fun accept_newFrameDropsIncompleteOldFrameAndIgnoresLateChunk() {
        val old = chunksFor(ByteArray(64) { 0 }, sequence = 2L, chunkBytes = 4)
        val latest = chunksFor(ByteArray(4) { 100 }, sequence = 3L, chunkBytes = 1024)
        val assembler = MappingGridAssembler()

        assertEquals(MappingGridAssembler.Result.Pending, assembler.accept(old.first()))
        assertTrue(assembler.accept(latest.single()) is MappingGridAssembler.Result.Complete)
        assertEquals(MappingGridAssembler.Result.IgnoredOldFrame, assembler.accept(old.last()))
    }

    @Test
    fun expireIncomplete_requestsLatestAfterBoundedTimeout() {
        var now = 100L
        val assembler = MappingGridAssembler(assemblyTimeoutMs = 50L, nowMs = { now })
        val chunks = chunksFor(ByteArray(64) { 0 }, sequence = 2L, chunkBytes = 4)
        assembler.accept(chunks.first())

        now = 151L
        val result = assembler.expireIncomplete()

        assertTrue(result is MappingGridAssembler.Result.Rejected)
    }

    @Test
    fun accept_rejectsInvalidOccupancyValue() {
        val result = MappingGridAssembler().accept(
            chunksFor(byteArrayOf(101), sequence = 1L, chunkBytes = 1024).single()
        )

        assertTrue(result is MappingGridAssembler.Result.Rejected)
        assertTrue((result as MappingGridAssembler.Result.Rejected).reason.contains("-1..100"))
    }

    private fun chunksFor(
        cells: ByteArray,
        sequence: Long,
        chunkBytes: Int
    ): List<MappingGridChunkModel> {
        val compressed = deflate(cells)
        val metadata = MappingGridMetadataModel(
            frameSequence = sequence,
            frameId = "map",
            sourceTimeNs = 123L,
            resolutionM = 0.1,
            widthCells = cells.size,
            heightCells = 1,
            origin = MappingPose(0.0, 0.0, 0.0),
            encodingValue = 1,
            uncompressedSizeBytes = cells.size.toLong(),
            compressedSizeBytes = compressed.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256")
                .digest(compressed)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        )
        val parts = compressed.toList().chunked(chunkBytes).map { it.toByteArray() }
        return parts.mapIndexed { index, bytes ->
            MappingGridChunkModel(metadata, index, parts.size, bytes)
        }
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_SPEED)
        return try {
            deflater.setInput(data)
            deflater.finish()
            val output = ByteArray(data.size + 64)
            output.copyOf(deflater.deflate(output))
        } finally {
            deflater.end()
        }
    }
}
