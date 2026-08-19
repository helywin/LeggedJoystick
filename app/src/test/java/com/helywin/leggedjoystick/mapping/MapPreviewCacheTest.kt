package com.helywin.leggedjoystick.mapping

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

class MapPreviewCacheTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun validPreviewIsWrittenAtomicallyAndCanBeReadByHash() {
        val directory = temporary.newFolder("preview-cache")
        val bytes = pngLikeBytes("valid")
        val hash = sha256(bytes)
        val cache = MapPreviewCache(directory)

        assertTrue(cache.put("sha256:$hash", bytes, ::isPngLike))
        assertTrue(cache.read(hash)!!.contentEquals(bytes))
        assertFalse(directory.listFiles()!!.any { it.name.startsWith(".map-preview-") })
    }

    @Test
    fun hashMismatchAndDecodeFailureNeverPolluteCache() {
        val directory = temporary.newFolder("invalid-cache")
        val bytes = pngLikeBytes("invalid")
        val cache = MapPreviewCache(directory)

        assertFalse(cache.put("0".repeat(64), bytes, ::isPngLike))
        assertFalse(cache.put(sha256(bytes), bytes) { false })
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test
    fun corruptedEntryIsDeletedAndOldestEntriesAreEvicted() {
        val directory = temporary.newFolder("bounded-cache")
        var now = 1L
        val first = pngLikeBytes("first-entry")
        val second = pngLikeBytes("second-entry")
        val cache = MapPreviewCache(
            directory = directory,
            maxTotalBytes = second.size.toLong(),
            nowMs = { now }
        )
        val firstHash = sha256(first)
        val secondHash = sha256(second)

        assertTrue(cache.put(firstHash, first, ::isPngLike))
        now = 2L
        assertTrue(cache.put(secondHash, second, ::isPngLike))
        assertNull(cache.read(firstHash))
        assertTrue(cache.read(secondHash)!!.contentEquals(second))

        directory.resolve("$secondHash.png").writeText("corrupted")
        assertNull(cache.read(secondHash))
        assertFalse(directory.resolve("$secondHash.png").exists())
    }

    private fun pngLikeBytes(payload: String): ByteArray {
        return byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47) + payload.toByteArray()
    }

    private fun isPngLike(bytes: ByteArray): Boolean {
        return bytes.size >= 4 && bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() && bytes[2] == 0x4e.toByte() &&
            bytes[3] == 0x47.toByte()
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
