package com.helywin.leggedjoystick.mapping

import java.security.MessageDigest
import java.util.Locale
import java.util.zip.DataFormatException
import java.util.zip.Inflater

class MappingGridAssembler(
    private val maxUncompressedBytes: Int = 16 * 1024 * 1024,
    private val maxCompressedBytes: Int = 20 * 1024 * 1024,
    private val maxChunkCount: Int = 20_000,
    private val assemblyTimeoutMs: Long = 3_000L,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    sealed interface Result {
        data object Pending : Result
        data object IgnoredOldFrame : Result
        data class Complete(val frame: MappingGridFrame) : Result
        data class Rejected(val reason: String, val requestLatest: Boolean = true) : Result
    }

    private data class Assembly(
        val metadata: MappingGridMetadataModel,
        val chunkCount: Int,
        val chunks: Array<ByteArray?>,
        val startedAtMs: Long,
        var receivedBytes: Long = 0L,
        var receivedChunks: Int = 0
    )

    private var assembly: Assembly? = null
    private var latestCompleteSequence: Long = 0L

    @Synchronized
    fun accept(chunk: MappingGridChunkModel): Result {
        val metadataError = validateMetadata(chunk.metadata)
        if (metadataError != null) {
            assembly = null
            return Result.Rejected(metadataError)
        }
        if (chunk.chunkCount !in 1..maxChunkCount ||
            chunk.chunkIndex !in 0 until chunk.chunkCount
        ) {
            assembly = null
            return Result.Rejected("实时地图分片索引或数量无效")
        }
        if (chunk.metadata.frameSequence <= latestCompleteSequence) {
            return Result.IgnoredOldFrame
        }

        val current = assembly
        if (current != null && nowMs() - current.startedAtMs > assemblyTimeoutMs) {
            assembly = null
        }
        val active = assembly
        if (active != null && chunk.metadata.frameSequence < active.metadata.frameSequence) {
            return Result.IgnoredOldFrame
        }
        if (active == null || chunk.metadata.frameSequence > active.metadata.frameSequence) {
            assembly = Assembly(
                metadata = chunk.metadata,
                chunkCount = chunk.chunkCount,
                chunks = arrayOfNulls(chunk.chunkCount),
                startedAtMs = nowMs()
            )
        }

        val target = checkNotNull(assembly)
        if (target.metadata != chunk.metadata || target.chunkCount != chunk.chunkCount) {
            assembly = null
            return Result.Rejected("同一实时地图帧的元数据不一致")
        }

        val previous = target.chunks[chunk.chunkIndex]
        if (previous != null) {
            return if (previous.contentEquals(chunk.data)) {
                Result.Pending
            } else {
                assembly = null
                Result.Rejected("同一实时地图分片重复但内容冲突")
            }
        }
        if (target.receivedBytes + chunk.data.size > target.metadata.compressedSizeBytes ||
            target.receivedBytes + chunk.data.size > maxCompressedBytes
        ) {
            assembly = null
            return Result.Rejected("实时地图压缩分片超过声明或本地上限")
        }

        target.chunks[chunk.chunkIndex] = chunk.data.copyOf()
        target.receivedBytes += chunk.data.size
        target.receivedChunks += 1
        if (target.receivedChunks != target.chunkCount) {
            return Result.Pending
        }

        return try {
            val frame = decode(target)
            assembly = null
            latestCompleteSequence = frame.metadata.frameSequence
            Result.Complete(frame)
        } catch (error: IllegalArgumentException) {
            assembly = null
            Result.Rejected(error.message ?: "实时地图校验失败")
        }
    }

    @Synchronized
    fun expireIncomplete(): Result? {
        val current = assembly ?: return null
        if (nowMs() - current.startedAtMs <= assemblyTimeoutMs) return null
        assembly = null
        return Result.Rejected("实时地图分片组装超时")
    }

    @Synchronized
    fun reset() {
        assembly = null
        latestCompleteSequence = 0L
    }

    private fun validateMetadata(metadata: MappingGridMetadataModel): String? {
        if (metadata.frameSequence <= 0L || metadata.frameId != "map") {
            return "实时地图帧号或坐标系无效"
        }
        if (metadata.sourceTimeNs < 0L ||
            !metadata.resolutionM.isFinite() || metadata.resolutionM <= 0.0 ||
            !metadata.origin.x.isFinite() || !metadata.origin.y.isFinite() ||
            !metadata.origin.yaw.isFinite()
        ) {
            return "实时地图时间、分辨率或原点无效"
        }
        if (metadata.widthCells <= 0 || metadata.heightCells <= 0) {
            return "实时地图宽高无效"
        }
        val expectedSize = metadata.widthCells.toLong() * metadata.heightCells.toLong()
        if (expectedSize != metadata.uncompressedSizeBytes ||
            expectedSize > maxUncompressedBytes
        ) {
            return "实时地图宽高与未压缩长度不一致或超过上限"
        }
        if (metadata.compressedSizeBytes <= 0L ||
            metadata.compressedSizeBytes > maxCompressedBytes
        ) {
            return "实时地图压缩长度无效或超过上限"
        }
        if (metadata.encodingValue != ZLIB_INT8_ENCODING ||
            !SHA256_PATTERN.matches(metadata.sha256.lowercase(Locale.US))
        ) {
            return "实时地图编码或 SHA-256 无效"
        }
        return null
    }

    private fun decode(target: Assembly): MappingGridFrame {
        require(target.receivedBytes == target.metadata.compressedSizeBytes) {
            "实时地图压缩长度与分片总和不一致"
        }
        val compressed = ByteArray(target.receivedBytes.toInt())
        var offset = 0
        target.chunks.forEach { chunk ->
            requireNotNull(chunk) { "实时地图仍有缺失分片" }
            chunk.copyInto(compressed, offset)
            offset += chunk.size
        }
        require(sha256(compressed) == target.metadata.sha256.lowercase(Locale.US)) {
            "实时地图 SHA-256 校验失败"
        }

        val cells = inflateExact(compressed, target.metadata.uncompressedSizeBytes.toInt())
        require(cells.all { it.toInt() in -1..100 }) {
            "实时地图占用值超出 -1..100"
        }
        return MappingGridFrame(target.metadata, cells, nowMs())
    }

    private fun inflateExact(compressed: ByteArray, expectedSize: Int): ByteArray {
        val inflater = Inflater()
        return try {
            inflater.setInput(compressed)
            val output = ByteArray(expectedSize)
            var offset = 0
            while (!inflater.finished() && offset < output.size) {
                val count = inflater.inflate(output, offset, output.size - offset)
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                offset += count
            }
            require(inflater.finished() && offset == expectedSize && inflater.remaining == 0) {
                "实时地图 zlib 解压长度不匹配"
            }
            output
        } catch (error: DataFormatException) {
            throw IllegalArgumentException("实时地图 zlib 数据损坏", error)
        } finally {
            inflater.end()
        }
    }

    private fun sha256(data: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(data)
            .joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }
    }

    private companion object {
        const val ZLIB_INT8_ENCODING = 1
        val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")
    }
}
