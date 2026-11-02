package com.pulse.bluetoothdisable.cover.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryTrashVideoInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var clock = System.currentTimeMillis()
    private val repository = GalleryRepository(context) { clock }

    @Before fun before() = clear()
    @After fun after() = clear()

    private fun clear() {
        (repository.images() + repository.recentlyDeleted()).forEach { repository.delete(it.id) }
    }

    @Test fun trashSurvivesReopenAndRestoreRetainsFavoriteAlbumsAndEncryptedData() {
        val photo = importPhoto("trash-restore.jpg", Color.RED)
        val album = repository.createAlbum("Trash restoration test")
        repository.addToAlbum(photo.id, album.id)
        repository.toggleFavorite(photo.id)
        val original = repository.imageBytes(photo.id)
        assertTrue(repository.moveToTrash(photo.id))
        assertFalse(repository.moveToTrash(photo.id)) // Repeated deletion cannot extend the deadline.
        assertTrue(repository.images().isEmpty())
        val reopened = GalleryRepository(context) { clock }
        assertEquals(clock, reopened.recentlyDeleted().single().deletedAt)
        assertArrayEquals(original, reopened.imageBytes(photo.id))
        clock += GalleryTrashPolicy.RETENTION_MILLIS - 1
        assertTrue(reopened.restore(photo.id))
        val restored = reopened.images().single()
        assertTrue(restored.favorite)
        assertTrue(album.id in restored.albumIds)
        assertNull(restored.deletedAt)
        assertArrayEquals(original, reopened.imageBytes(photo.id))
    }

    @Test fun expiryDeletesOnlyExpiredMediaAndBothEncryptedFilesAtExactlyTwentyNineDays() {
        val expired = importPhoto("trash-expiry.jpg", Color.BLUE)
        val kept = importPhoto("trash-kept.jpg", Color.GREEN)
        repository.moveToTrash(expired.id)
        val root = File(context.noBackupFilesDir, "gallery_v1")
        val file = File(File(root, "images"), expired.encryptedFileName)
        val thumb = File(File(root, "thumbs"), expired.thumbnailFileName)
        clock += GalleryTrashPolicy.RETENTION_MILLIS - 1
        assertEquals(1, repository.recentlyDeleted().size)
        assertTrue(file.exists())
        clock++
        assertTrue(repository.recentlyDeleted().isEmpty())
        assertFalse(file.exists())
        assertFalse(thumb.exists())
        assertFalse(repository.restore(expired.id))
        assertEquals(kept.id, repository.images().single().id)
    }

    @Test fun importingTheSameDeletedPhotoRestoresOneRecordInsteadOfDuplicatingIt() {
        val photo = importPhoto("trash-reimport.jpg", Color.RED)
        repository.moveToTrash(photo.id)
        val added = repository.importUris(context.contentResolver, listOf(Uri.fromFile(File(context.cacheDir, "trash-reimport.jpg"))))
        assertEquals(photo.id, added.single().id)
        assertEquals(photo.id, repository.images().single().id)
        assertTrue(repository.recentlyDeleted().isEmpty())
    }

    @Test fun chunkedVideoSupportsRandomReadsReopenAndDetectsTamperingAndWrongIdentity() {
        val bytes = ByteArray(GalleryVideoDataSource.BLOCK_BYTES * 2 + 123) { (it * 31).toByte() }
        val file = File(context.cacheDir, "encrypted-video-test.bin")
        try {
            val (size, _) = GalleryVideoDataSource.write(ByteArrayInputStream(bytes), file, "test")
            assertEquals(bytes.size.toLong(), size)
            assertFalse(bytes.contentEquals(file.readBytes()))
            GalleryVideoDataSource(file, "test", size).use { source ->
                for (offset in listOf(65_530, 0, bytes.size - 20, 70_000)) {
                    val result = ByteArray(100)
                    val count = source.readAt(offset.toLong(), result, 0, result.size)
                    val expected = minOf(100, bytes.size - offset)
                    assertEquals(expected, count)
                    assertArrayEquals(bytes.copyOfRange(offset, offset + expected), result.copyOf(count))
                }
                assertEquals(-1, source.readAt(size, ByteArray(1), 0, 1))
                assertEquals(0, source.readAt(size, ByteArray(1), 0, 0))
            }
            GalleryVideoDataSource(file, "wrong-photo", size).use { source ->
                assertThrows(IOException::class.java) { source.readAt(0, ByteArray(10), 0, 10) }
            }
            RandomAccessFile(file, "rw").use { it.seek(40); val previous = it.readUnsignedByte(); it.seek(40); it.writeByte(previous xor 1) }
            GalleryVideoDataSource(file, "test", size).use { source ->
                assertThrows(IOException::class.java) { source.readAt(0, ByteArray(10), 0, 10) }
            }
        } finally { file.delete() }
    }

    @Test fun realVideoImportsPersistsRestoresAndReturnsTheOriginalBytesBySeeking() {
        val fixture = File(context.cacheDir, "gallery-test-video.mp4")
        val original = InstrumentationRegistry.getInstrumentation().context.assets.open("gallery-test-video.mp4").use { it.readBytes() }
        fixture.writeBytes(original)
        try {
            val video = repository.importUris(context.contentResolver, listOf(Uri.fromFile(fixture))).single()
            assertTrue(video.isVideo)
            assertEquals(96, video.width)
            assertEquals(64, video.height)
            assertTrue(video.durationMillis!! >= 1000)
            assertTrue(repository.thumbnailBytes(video.id).isNotEmpty())
            repository.openVideo(video.id).use { source ->
                val restored = ByteArray(original.size)
                assertEquals(original.size, source.readAt(0, restored, 0, restored.size))
                assertArrayEquals(original, restored)
            }
            repository.moveToTrash(video.id)
            assertTrue(repository.images().none { it.isVideo })
            val imported = repository.importUris(context.contentResolver, listOf(Uri.fromFile(fixture)))
            assertEquals(video.id, imported.single().id)
            assertEquals(video.id, repository.images().single().id)
            assertTrue(repository.recentlyDeleted().isEmpty())
        } finally { fixture.delete() }
    }

    private fun importPhoto(name: String, color: Int): GalleryImage {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        try { file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) } }
        finally { bitmap.recycle() }
        return repository.importUris(context.contentResolver, listOf(Uri.fromFile(file))).single()
    }
}
