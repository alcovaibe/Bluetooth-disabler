package com.pulse.bluetoothdisable.cover.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pulse.bluetoothdisable.cover.CoverMode
import com.pulse.bluetoothdisable.cover.CoverModeManager
import com.pulse.bluetoothdisable.cover.calculator.CalculatorAccessCodeManager
import com.pulse.bluetoothdisable.launcher.LauncherIconController
import com.pulse.bluetoothdisable.launcher.LauncherStyle
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryCoverPersistenceInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = CoverModeManager(context)
    private val repository = GalleryRepository(context)
    private val access = GalleryAccessManager(context)
    private val legacyPreferences = context.getSharedPreferences(GalleryAccessManager.PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Before fun before() {
        manager.resetToDefault()
        repository.images().forEach { repository.delete(it.id) }
        access.clear()
    }

    @After fun after() {
        if (manager.activeMode() == CoverMode.GALLERY) manager.resetGalleryCover() else manager.resetToDefault()
        repository.images().forEach { repository.delete(it.id) }
        access.clear()
    }

    @Test fun importedPhotoIsSanitizedEncryptedAndPersistsAcrossCoverChanges() {
        val source = createJpegWithExif()
        val added = repository.importUris(context.contentResolver, listOf(Uri.fromFile(source)))
        assertEquals(1, added.size)
        val image = added.single()

        assertEquals(96, image.width)
        assertEquals(64, image.height)
        assertEquals(1, image.orientation)
        assertEquals(GalleryRepository.SOURCE_USER_PICKER, image.source)
        assertNotNull("Color information should be retained in Gallery DB", image.colorSpace)
        assertNotNull("Exposure parameters should be retained in Gallery DB", image.shooting["ExposureTime"])
        assertNotNull("ISO should be retained in Gallery DB", image.shooting["PhotographicSensitivity"])

        val sanitized = ExifInterface(ByteArrayInputStream(repository.imageBytes(image.id)))
        for (removed in listOf(
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_X_RESOLUTION,
            ExifInterface.TAG_Y_RESOLUTION,
        )) {
            assertNull("$removed must not survive sanitized local copy", sanitized.getAttribute(removed))
        }
        assertNull(sanitized.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(sanitized.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))

        legacyPreferences.edit().putString("secret_image_id", image.id).putString("secret_sequence_hmac", "legacy").commit()
        manager.activateGallery()
        assertTrue(manager.isGalleryReady())
        assertTrue(legacyPreferences.all.isEmpty())
        assertEquals(LauncherStyle.GALLERY, LauncherIconController(context).selectedStyle())

        manager.activateCalculator("58317")
        assertTrue(manager.isCalculatorReady())
        assertTrue(legacyPreferences.all.isEmpty())
        assertTrue(CalculatorAccessCodeManager(context).verify("58317"))
        assertEquals(image.id, repository.images().single().id)

        manager.activateGallery()
        assertTrue(manager.isGalleryReady())
        assertTrue(legacyPreferences.all.isEmpty())
        assertEquals(1, repository.images().size)

        repository.toggleFavorite(image.id)
        assertTrue(repository.image(image.id)!!.favorite)
        manager.resetGalleryCover()
        assertEquals(CoverMode.DEFAULT, manager.activeMode())
        assertTrue(legacyPreferences.all.isEmpty())
        assertEquals(1, repository.images().size)
        assertTrue(repository.image(image.id)!!.favorite)
    }

    @Test fun deletingPhotoKeepsGalleryActiveWithoutAnAccessRule() {
        val first = repository.importUris(
            context.contentResolver,
            listOf(Uri.fromFile(createPlainJpeg("first.jpg"))),
        ).single()
        val second = repository.importUris(
            context.contentResolver,
            listOf(Uri.fromFile(createPlainJpeg("second.jpg", Color.BLUE))),
        ).single()
        manager.activateGallery()
        assertTrue(legacyPreferences.all.isEmpty())

        repository.delete(first.id)

        assertTrue(legacyPreferences.all.isEmpty())
        assertEquals(listOf(second.id), repository.images().map { it.id })
        assertTrue(manager.isGalleryReady())
    }

    @Test fun galleryActivationAndUpgradeNeedNoSecretPhotoAndPreserveAlbums() {
        val album = repository.createAlbum("Preserved")
        manager.activateGallery()
        assertTrue(manager.isGalleryReady())
        assertTrue(repository.images().isEmpty())
        // Simulate credentials left by an older Gallery APK before application startup.
        legacyPreferences.edit().putString("secret_image_id", "old-photo").putString("secret_sequence_hmac", "old-rule").commit()
        manager.recoverInterruptedSetup()
        assertTrue(manager.isGalleryReady())
        assertTrue(legacyPreferences.all.isEmpty())
        assertTrue(repository.albums().any { it.id == album.id })
        manager.resetGalleryCover()
        assertEquals(CoverMode.DEFAULT, manager.activeMode())
        assertEquals(LauncherStyle.DEFAULT, LauncherIconController(context).selectedStyle())
        assertTrue(repository.albums().any { it.id == album.id })
    }

    @Test fun atomicIndexRecoversCommittedGalleryAfterInterruptedReplacement() {
        val image = repository.importUris(
            context.contentResolver,
            listOf(Uri.fromFile(createPlainJpeg("atomic-recovery.jpg"))),
        ).single()
        val root = File(context.noBackupFilesDir, "gallery_v1")
        val index = File(root, "index.enc")
        val legacyBackup = File(root, "index.enc.bak")

        assertTrue(index.exists())
        legacyBackup.delete()
        assertTrue(index.renameTo(legacyBackup))
        index.writeBytes(byteArrayOf(0x01, 0x02, 0x03))

        val recovered = GalleryRepository(context).images()

        assertEquals(listOf(image.id), recovered.map { it.id })
        assertTrue(index.exists())
        assertFalse(legacyBackup.exists())
        assertArrayEquals(
            repository.imageBytes(image.id),
            GalleryRepository(context).imageBytes(image.id),
        )
    }

    @Test fun editingPhotoCommitsNewEncryptedFilesBeforeDeletingOldRevision() {
        val image = repository.importUris(
            context.contentResolver,
            listOf(Uri.fromFile(createPlainJpeg("copy-on-write.jpg"))),
        ).single()
        val root = File(context.noBackupFilesDir, "gallery_v1")
        val oldImageFile = File(File(root, "images"), image.encryptedFileName)
        val oldThumbFile = File(File(root, "thumbs"), image.thumbnailFileName)
        assertTrue(oldImageFile.exists())
        assertTrue(oldThumbFile.exists())

        val changed = repository.rotate(image.id, 90)
        val decodedBytes = repository.imageBytes(image.id)
        val decoded = checkNotNull(BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size))

        assertNotEquals(image.encryptedFileName, changed.encryptedFileName)
        assertNotEquals(image.thumbnailFileName, changed.thumbnailFileName)
        assertFalse(oldImageFile.exists())
        assertFalse(oldThumbFile.exists())
        assertEquals(64, changed.width)
        assertEquals(96, changed.height)
        assertEquals(changed.width, decoded.width)
        assertEquals(changed.height, decoded.height)
        decoded.recycle()
    }

    @Test fun committedIndexReconciliationRemovesUnreferencedEncryptedFiles() {
        val image = repository.importUris(
            context.contentResolver,
            listOf(Uri.fromFile(createPlainJpeg("orphan-cleanup.jpg"))),
        ).single()
        val root = File(context.noBackupFilesDir, "gallery_v1")
        val orphanImage = File(File(root, "images"), "orphan.bin")
        val orphanThumb = File(File(root, "thumbs"), "orphan.bin")
        orphanImage.writeBytes(byteArrayOf(1, 2, 3))
        orphanThumb.writeBytes(byteArrayOf(4, 5, 6))
        assertTrue(orphanImage.exists())
        assertTrue(orphanThumb.exists())

        val loaded = GalleryRepository(context).images()

        assertEquals(listOf(image.id), loaded.map { it.id })
        assertFalse(orphanImage.exists())
        assertFalse(orphanThumb.exists())
    }

    @Test fun appRequestsNoBroadPhotoLibraryPermission() {
        val requested = context.packageManager
            .getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.toSet()
            .orEmpty()
        assertFalse("android.permission.READ_MEDIA_IMAGES" in requested)
        assertFalse("android.permission.READ_EXTERNAL_STORAGE" in requested)
    }

    private fun createJpegWithExif(): File {
        val file = createPlainJpeg("gallery-source-exif.jpg")
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_MAKE, "PrivateCameraMaker")
            setAttribute(ExifInterface.TAG_MODEL, "PrivateCameraModel")
            setAttribute(ExifInterface.TAG_SOFTWARE, "PrivateEditor")
            setAttribute(ExifInterface.TAG_DATETIME, "2026:09:30 20:15:00")
            setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "private description")
            setAttribute(ExifInterface.TAG_X_RESOLUTION, "300/1")
            setAttribute(ExifInterface.TAG_Y_RESOLUTION, "300/1")
            setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "0.008")
            setAttribute(ExifInterface.TAG_F_NUMBER, "1.8")
            setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "200")
            saveAttributes()
        }
        val sourceExif = ExifInterface(file.absolutePath)
        assertNotNull(sourceExif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME))
        assertNotNull(sourceExif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY))
        return file
    }

    private fun createPlainJpeg(name: String, color: Int = Color.RED): File {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        FileOutputStream(file).use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
        }
        bitmap.recycle()
        return file
    }
}
