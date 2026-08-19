package com.helywin.leggedjoystick.mapping

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale

class MapPreviewCache(
    private val directory: File,
    private val maxTotalBytes: Long = 64L * 1024L * 1024L,
    private val maxEntryBytes: Int = 16 * 1024 * 1024,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    init {
        require(maxTotalBytes > 0L && maxEntryBytes > 0) { "地图预览缓存上限必须为正数" }
        require(directory.mkdirs() || directory.isDirectory) { "无法创建地图预览缓存目录" }
        directory.listFiles { file -> file.name.startsWith(TEMP_PREFIX) }
            ?.forEach(File::delete)
    }

    @Synchronized
    fun read(expectedSha256: String): ByteArray? {
        val hash = normalizeHash(expectedSha256) ?: return null
        val file = cacheFile(hash)
        if (!file.isFile || file.length() !in 1L..maxEntryBytes.toLong()) return null
        val bytes = file.readBytes()
        if (sha256(bytes) != hash) {
            file.delete()
            return null
        }
        file.setLastModified(nowMs())
        return bytes
    }

    @Synchronized
    fun put(
        expectedSha256: String,
        bytes: ByteArray,
        isDecodableImage: (ByteArray) -> Boolean
    ): Boolean {
        val hash = normalizeHash(expectedSha256) ?: return false
        if (bytes.isEmpty() || bytes.size > maxEntryBytes || sha256(bytes) != hash) return false
        if (!isDecodableImage(bytes)) return false

        val temporary = File.createTempFile(TEMP_PREFIX, ".tmp", directory)
        return try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            moveAtomically(temporary, cacheFile(hash))
            cacheFile(hash).setLastModified(nowMs())
            evictToLimit()
            cacheFile(hash).isFile
        } finally {
            temporary.delete()
        }
    }

    @Synchronized
    fun clear() {
        cacheFiles().forEach(File::delete)
    }

    private fun evictToLimit() {
        val files = cacheFiles().sortedBy(File::lastModified).toMutableList()
        var total = files.sumOf(File::length)
        while (total > maxTotalBytes && files.isNotEmpty()) {
            val oldest = files.removeAt(0)
            val length = oldest.length()
            if (oldest.delete()) total -= length
        }
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun cacheFiles(): List<File> {
        return directory.listFiles { file ->
            file.isFile && file.name.endsWith(FILE_SUFFIX) &&
                SHA256_PATTERN.matches(file.name.removeSuffix(FILE_SUFFIX))
        }?.toList().orEmpty()
    }

    private fun cacheFile(hash: String) = File(directory, "$hash$FILE_SUFFIX")

    private fun normalizeHash(value: String): String? {
        val normalized = value.removePrefix("sha256:").lowercase(Locale.US)
        return normalized.takeIf(SHA256_PATTERN::matches)
    }

    private fun sha256(data: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(data)
            .joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }
    }

    private companion object {
        const val TEMP_PREFIX = ".map-preview-"
        const val FILE_SUFFIX = ".png"
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
