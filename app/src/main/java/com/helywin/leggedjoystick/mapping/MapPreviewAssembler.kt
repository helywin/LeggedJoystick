package com.helywin.leggedjoystick.mapping

import java.security.MessageDigest
import java.util.Locale

data class MapIdentityModel(
    val mapId: String,
    val revision: Long
)

data class MapPreviewRequestKey(
    val requestId: Long,
    val map: MapIdentityModel
)

data class MapPreviewChunkModel(
    val key: MapPreviewRequestKey,
    val chunkIndex: Int,
    val chunkCount: Int,
    val totalSizeBytes: Long,
    val sha256: String,
    val data: ByteArray
)

data class MapPreviewData(
    val key: MapPreviewRequestKey,
    val sha256: String,
    val bytes: ByteArray,
    val receivedAtMs: Long
)

class MapPreviewAssembler(
    private val maxPreviewBytes: Int = 16 * 1024 * 1024,
    private val maxChunkCount: Int = 4_096,
    private val assemblyTimeoutMs: Long = 4_000L,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    sealed interface Result {
        data object Pending : Result
        data object Ignored : Result
        data class Complete(val preview: MapPreviewData) : Result
        data class Rejected(val reason: String) : Result
    }

    private data class Assembly(
        val key: MapPreviewRequestKey,
        val chunkCount: Int,
        val totalSizeBytes: Long,
        val sha256: String,
        val chunks: Array<ByteArray?>,
        val startedAtMs: Long,
        var receivedBytes: Long = 0L,
        var receivedChunks: Int = 0
    )

    private var expectedKey: MapPreviewRequestKey? = null
    private var assembly: Assembly? = null

    @Synchronized
    fun begin(key: MapPreviewRequestKey) {
        require(key.requestId > 0L) { "地图预览请求号必须为正数" }
        require(key.map.mapId.isNotBlank() && key.map.revision > 0L) {
            "地图预览 identity 无效"
        }
        expectedKey = key
        assembly = null
    }

    @Synchronized
    fun accept(chunk: MapPreviewChunkModel): Result {
        if (chunk.key != expectedKey) return Result.Ignored
        validateChunk(chunk)?.let {
            clear()
            return Result.Rejected(it)
        }

        val current = assembly
        if (current != null && nowMs() - current.startedAtMs > assemblyTimeoutMs) {
            clear()
            return Result.Rejected("地图预览分片组装超时")
        }
        if (assembly == null) {
            assembly = Assembly(
                key = chunk.key,
                chunkCount = chunk.chunkCount,
                totalSizeBytes = chunk.totalSizeBytes,
                sha256 = chunk.sha256.lowercase(Locale.US),
                chunks = arrayOfNulls(chunk.chunkCount),
                startedAtMs = nowMs()
            )
        }

        val target = checkNotNull(assembly)
        if (target.chunkCount != chunk.chunkCount ||
            target.totalSizeBytes != chunk.totalSizeBytes ||
            target.sha256 != chunk.sha256.lowercase(Locale.US)
        ) {
            clear()
            return Result.Rejected("同一地图预览请求的分片元数据不一致")
        }

        val previous = target.chunks[chunk.chunkIndex]
        if (previous != null) {
            return if (previous.contentEquals(chunk.data)) {
                Result.Pending
            } else {
                clear()
                Result.Rejected("同一地图预览分片重复但内容冲突")
            }
        }
        if (target.receivedBytes + chunk.data.size > target.totalSizeBytes) {
            clear()
            return Result.Rejected("地图预览分片超过声明总长度")
        }

        target.chunks[chunk.chunkIndex] = chunk.data.copyOf()
        target.receivedBytes += chunk.data.size
        target.receivedChunks += 1
        if (target.receivedChunks != target.chunkCount) return Result.Pending

        return try {
            require(target.receivedBytes == target.totalSizeBytes) {
                "地图预览分片总长度与声明不一致"
            }
            val bytes = ByteArray(target.totalSizeBytes.toInt())
            var offset = 0
            target.chunks.forEach { part ->
                requireNotNull(part) { "地图预览仍有缺失分片" }
                part.copyInto(bytes, offset)
                offset += part.size
            }
            require(sha256(bytes) == target.sha256) { "地图预览 SHA-256 校验失败" }
            val preview = MapPreviewData(target.key, target.sha256, bytes, nowMs())
            clear()
            Result.Complete(preview)
        } catch (error: IllegalArgumentException) {
            clear()
            Result.Rejected(error.message ?: "地图预览校验失败")
        }
    }

    @Synchronized
    fun expireIncomplete(): Result? {
        val current = assembly ?: return null
        if (nowMs() - current.startedAtMs <= assemblyTimeoutMs) return null
        clear()
        return Result.Rejected("地图预览分片组装超时")
    }

    @Synchronized
    fun reset() = clear()

    private fun validateChunk(chunk: MapPreviewChunkModel): String? {
        if (chunk.chunkCount !in 1..maxChunkCount ||
            chunk.chunkIndex !in 0 until chunk.chunkCount
        ) {
            return "地图预览分片索引或数量无效"
        }
        if (chunk.totalSizeBytes !in 1L..maxPreviewBytes.toLong()) {
            return "地图预览总长度无效或超过上限"
        }
        if (chunk.data.isEmpty() || chunk.data.size > maxPreviewBytes) {
            return "地图预览分片为空或超过上限"
        }
        if (!SHA256_PATTERN.matches(chunk.sha256)) {
            return "地图预览 SHA-256 格式无效"
        }
        return null
    }

    private fun clear() {
        expectedKey = null
        assembly = null
    }

    private fun sha256(data: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(data)
            .joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }
    }

    private companion object {
        val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")
    }
}
