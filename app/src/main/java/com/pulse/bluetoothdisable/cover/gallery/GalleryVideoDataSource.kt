package com.pulse.bluetoothdisable.cover.gallery

import android.media.MediaDataSource
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Seekable authenticated video storage. Only one 64 KiB plaintext block is kept in memory. */
internal class GalleryVideoDataSource(
    file: File,
    private val id: String,
    private val plainSize: Long,
) : MediaDataSource() {
    init { require(plainSize in 1..MAX_VIDEO_BYTES) }

    private var source: RandomAccessFile? = RandomAccessFile(file, "r")
    private var cachedIndex = -1L
    private var cachedBlock = ByteArray(0)

    @Synchronized override fun getSize(): Long = plainSize

    @Synchronized override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position < 0 || offset < 0 || size < 0 || offset > buffer.size - size) throw IOException("Invalid video read")
        val input = source ?: throw IOException("Video source is closed")
        if (size == 0) return 0
        if (position >= plainSize) return -1
        val requested = minOf(size.toLong(), plainSize - position).toInt()
        var copied = 0
        while (copied < requested) {
            val cursor = position + copied
            val index = cursor / BLOCK_BYTES
            if (cachedIndex != index) {
                val expected = minOf(BLOCK_BYTES.toLong(), plainSize - index * BLOCK_BYTES).toInt()
                try {
                    input.seek(index * (BLOCK_BYTES + RECORD_OVERHEAD))
                    val length = input.readInt()
                    if (length != expected + ENVELOPE_OVERHEAD) throw IOException("Invalid video block")
                    val encrypted = ByteArray(length)
                    input.readFully(encrypted)
                    val decoded = GalleryCipher.decrypt(encrypted, purpose(id, index))
                    if (decoded.size != expected) throw IOException("Invalid video block size")
                    cachedBlock.fill(0)
                    cachedBlock = decoded
                    cachedIndex = index
                } catch (error: Exception) {
                    throw IOException("Unable to read authenticated video", error)
                }
            }
            val inBlock = (cursor % BLOCK_BYTES).toInt()
            val count = minOf(requested - copied, cachedBlock.size - inBlock)
            cachedBlock.copyInto(buffer, offset + copied, inBlock, inBlock + count)
            copied += count
        }
        return copied
    }

    @Synchronized override fun close() {
        val input = source
        source = null
        try { input?.close() } finally {
            cachedBlock.fill(0)
            cachedBlock = ByteArray(0)
            cachedIndex = -1
        }
    }

    companion object {
        internal const val BLOCK_BYTES = 64 * 1024
        internal const val MAX_VIDEO_BYTES = 512L * 1024 * 1024
        private const val ENVELOPE_OVERHEAD = 30 // v1 + IV length + 12-byte IV + 16-byte GCM tag
        private const val RECORD_OVERHEAD = ENVELOPE_OVERHEAD + 4
        private fun purpose(id: String, index: Long) = "video:$id:$index"

        /** Caller owns rollback: the encrypted file is not indexed until metadata validation succeeds. */
        internal fun write(input: InputStream, file: File, id: String): Pair<Long, String> {
            val digest = MessageDigest.getInstance("SHA-256")
            val block = ByteArray(BLOCK_BYTES)
            var total = 0L
            var index = 0L
            try {
                DataOutputStream(file.outputStream().buffered()).use { output ->
                    while (true) {
                        var used = 0
                        while (used < block.size) {
                            val count = input.read(block, used, block.size - used)
                            if (count < 0) break
                            if (count == 0) {
                                val single = input.read()
                                if (single < 0) break
                                block[used++] = single.toByte()
                                continue
                            }
                            used += count
                        }
                        if (used == 0) break
                        total += used
                        require(total <= MAX_VIDEO_BYTES) { "Selected video is too large" }
                        digest.update(block, 0, used)
                        val plain = block.copyOf(used)
                        val encrypted = try { GalleryCipher.encrypt(plain, purpose(id, index++)) }
                        finally { plain.fill(0) }
                        check(encrypted.size == used + ENVELOPE_OVERHEAD) { "Unsupported video envelope" }
                        output.writeInt(encrypted.size)
                        output.write(encrypted)
                        if (used < block.size) break
                    }
                }
                require(total > 0) { "Empty video" }
                return total to digest.digest().joinToString("") { "%02x".format(it) }
            } finally { block.fill(0) }
        }
    }
}
