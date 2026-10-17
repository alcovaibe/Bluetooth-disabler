package com.pulse.bluetoothdisable.cover.gallery

import android.app.Activity
import android.app.Application
import android.app.Instrumentation.ActivityResult
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.graphics.toPixelMap
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.cover.CoverModeManager
import com.pulse.bluetoothdisable.localization.LanguageManager
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryPickerInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val manager = CoverModeManager(app)
    private val repository = GalleryRepository(app)
    private val picked = Uri.parse("content://com.pulse.bluetoothdisable.test.gallerypicker/picked.jpg")
    private val invalid = Uri.parse("content://com.pulse.bluetoothdisable.test.gallerypicker/invalid.jpg")
    private val videoUri = Uri.parse("content://com.pulse.bluetoothdisable.test.gallerypicker/picked.mp4")

    @Before fun before() {
        manager.resetToDefault()
        (repository.images() + repository.recentlyDeleted()).forEach { repository.delete(it.id) }
        LanguageManager.setSelectedLanguage(app, LanguageManager.ENGLISH)
        requireNotNull(app.contentResolver.openOutputStream(picked)).use { writeJpeg(it, Color.BLUE) }
        requireNotNull(app.contentResolver.openOutputStream(invalid)).use { it.write("not an image".toByteArray()) }
        requireNotNull(app.contentResolver.openOutputStream(videoUri)).use { output ->
            instrumentation.context.assets.open("gallery-test-video.mp4").use { it.copyTo(output) }
        }
        for (uri in listOf(picked, invalid, videoUri)) {
            instrumentation.context.grantUriPermission(app.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val seed = File(app.cacheDir, "gallery-picker-seed.jpg")
        writeJpeg(seed, Color.RED)
        repository.importUris(app.contentResolver, listOf(Uri.fromFile(seed))).single()
        manager.activateGallery()
    }

    @After fun after() {
        manager.resetToDefault()
        (repository.images() + repository.recentlyDeleted()).forEach { repository.delete(it.id) }
        for (uri in listOf(picked, invalid, videoUri)) instrumentation.context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        app.contentResolver.delete(picked, null, null)
        app.contentResolver.delete(invalid, null, null)
        app.contentResolver.delete(videoUri, null, null)
        File(app.cacheDir, "gallery-picker-seed.jpg").delete()
    }

    @Test fun pickerContentUriIsImportedAndResumeRefreshCannotHideIt() {
        val monitor = pickerResult(picked)
        try {
            ActivityScenario.launch<GalleryCoverActivity>(Intent(app, GalleryCoverActivity::class.java)).use { scenario ->
                compose.waitUntil(5_000) { !state(scenario).loading }
                compose.onNodeWithText(app.getString(R.string.gallery_add_photos)).performClick()
                compose.waitUntil(10_000) { !state(scenario).busy && state(scenario).images.size == 2 }
                repeat(3) { scenario.onActivity { ViewModelProvider(it)[GalleryViewModel::class.java].refresh() } }
                compose.waitForIdle()
                assertEquals(2, state(scenario).images.size)
                assertNull(state(scenario).photoError)
                assertFalse(state(scenario).storageError)
                assertEquals(2, repository.images().size)
                // Imported bytes remain readable after the external provider grant disappears.
                instrumentation.context.revokeUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                app.contentResolver.delete(picked, null, null)
                repository.images().forEach { assertTrue(repository.imageBytes(it.id).isNotEmpty()) }
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun unavailablePhotoDoesNotHideExistingGalleryAndCanBeRetried() {
        val monitor = pickerResult(invalid)
        try {
            ActivityScenario.launch<GalleryCoverActivity>(Intent(app, GalleryCoverActivity::class.java)).use { scenario ->
                compose.waitUntil(5_000) { !state(scenario).loading }
                compose.onNodeWithText(app.getString(R.string.gallery_add_photos)).performClick()
                compose.waitUntil(5_000) { state(scenario).photoError == GalleryPhotoError.IMPORT_FAILED }
                assertEquals(1, state(scenario).images.size)
                assertFalse(state(scenario).storageError)
                compose.onNodeWithText(app.getString(R.string.gallery_import_error)).assertIsDisplayed()
                compose.onNodeWithText(app.getString(R.string.cancel)).performClick()
                assertNull(state(scenario).photoError)
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun failedImportDuringInitialLoadStillShowsStoredPhotos() {
        lateinit var viewModel: GalleryViewModel
        instrumentation.runOnMainSync {
            viewModel = GalleryViewModel(ApplicationProvider.getApplicationContext<Application>())
            viewModel.importUris(listOf(invalid))
            viewModel.refresh()
        }
        compose.waitUntil(5_000) {
            var done = false
            instrumentation.runOnMainSync { done = !viewModel.uiState.busy && !viewModel.uiState.loading }
            done
        }
        instrumentation.runOnMainSync {
            assertEquals(1, viewModel.uiState.images.size)
            assertEquals(GalleryPhotoError.IMPORT_FAILED, viewModel.uiState.photoError)
            assertFalse(viewModel.uiState.storageError)
        }
    }

    @Test fun setupPickerShowsImportedPhotoWithoutCancellingOrReopeningSetup() {
        val previousId = repository.images().single().id
        val monitor = pickerResult(picked)
        try {
            ActivityScenario.launch<GalleryCoverSetupActivity>(Intent(app, GalleryCoverSetupActivity::class.java)).use {
                compose.onNodeWithText(app.getString(R.string.gallery_add_photos)).performClick()
                compose.waitUntil(10_000) { repository.images().size == 2 }
                val added = repository.images().single { it.id != previousId }
                assertPhoto("gallery_setup_image_${added.id}", Color.BLUE)
                compose.onNodeWithText(app.getString(R.string.gallery_finish_setup)).assertIsEnabled()
                assertEquals(2, repository.images().size)
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun swipingChangesRenderedPhotoWithoutCounterInBothDirections() {
        ActivityScenario.launch<GalleryCoverActivity>(Intent(app, GalleryCoverActivity::class.java)).use { scenario ->
            compose.waitUntil(5_000) { !state(scenario).loading }
            scenario.onActivity { ViewModelProvider(it)[GalleryViewModel::class.java].importUris(listOf(picked)) }
            compose.waitUntil(5_000) { !state(scenario).busy && state(scenario).images.size == 2 }
            val images = state(scenario).images
            val blue = images[0]
            val red = images[1]
            scenario.onActivity { ViewModelProvider(it)[GalleryViewModel::class.java].select(blue) }
            assertPhoto("gallery_viewer_image_${blue.id}", Color.BLUE)
            var firstPreview: androidx.compose.ui.graphics.ImageBitmap? = null
            scenario.onActivity { firstPreview = ViewModelProvider(it)[GalleryViewModel::class.java].cachedPreview(blue.id, false) }
            assertNotNull(firstPreview)
            compose.onNodeWithTag("gallery_info").assertIsDisplayed()
            compose.onNodeWithText("1/2").assertDoesNotExist()
            compose.onNodeWithTag("gallery_toolbar_actions").assertIsDisplayed()
            compose.onNodeWithContentDescription(app.getString(R.string.gallery_hide_actions)).performClick()
            compose.onNodeWithTag("gallery_toolbar_actions").assertDoesNotExist()
            compose.onNodeWithContentDescription(app.getString(R.string.gallery_show_actions)).assertIsDisplayed()
            repeat(3) {
                compose.onNodeWithTag("gallery_viewer").performTouchInput { swipeLeft(durationMillis = 350) }
                assertPhoto("gallery_viewer_image_${red.id}", Color.RED)
                compose.onNodeWithText("2/2").assertDoesNotExist()
                compose.onNodeWithTag("gallery_toolbar_actions").assertDoesNotExist()
                compose.onNodeWithTag("gallery_viewer").performTouchInput { swipeRight(durationMillis = 350) }
                assertPhoto("gallery_viewer_image_${blue.id}", Color.BLUE)
                scenario.onActivity {
                    assertSame(firstPreview, ViewModelProvider(it)[GalleryViewModel::class.java].cachedPreview(blue.id, false))
                }
                compose.onNodeWithText("1/2").assertDoesNotExist()
            }
            compose.onNodeWithContentDescription(app.getString(R.string.gallery_show_actions)).performClick()
            compose.onNodeWithTag("gallery_toolbar_actions").assertIsDisplayed()
            compose.onNodeWithText(app.getString(R.string.gallery_delete)).assertIsDisplayed()
            compose.onNodeWithTag("gallery_info").performClick()
            compose.onNodeWithText(app.getString(R.string.gallery_close)).performClick()
            assertTrue(manager.isGalleryReady())
        }
    }

    @Test fun photoDeletionUsesCompactSheetAndTrashRestorationReturnsThePhoto() {
        ActivityScenario.launch<GalleryCoverActivity>(Intent(app, GalleryCoverActivity::class.java)).use { scenario ->
            compose.waitUntil(5_000) { !state(scenario).loading }
            val photo = state(scenario).images.single()
            scenario.onActivity { ViewModelProvider(it)[GalleryViewModel::class.java].select(photo) }
            assertPhoto("gallery_viewer_image_${photo.id}", Color.RED)
            compose.onNodeWithText(app.getString(R.string.gallery_delete)).performClick()
            compose.onNodeWithTag("gallery_delete_dialog").assertIsDisplayed()
            compose.onNodeWithText(app.getString(R.string.gallery_delete_question)).assertIsDisplayed()
            captureGalleryPreview("gallery-delete-sheet.png")
            compose.onNodeWithTag("gallery_delete_confirm").performClick()
            compose.waitUntil(5_000) { !state(scenario).busy && state(scenario).recentlyDeleted.size == 1 }
            assertTrue(state(scenario).images.isEmpty())
            compose.onNodeWithTag("gallery_section_ALBUMS").performClick()
            compose.onNodeWithTag("gallery_recently_deleted_album").performScrollTo().assertIsDisplayed()
            captureGalleryPreview("gallery-trash-album.png")
            compose.onNodeWithTag("gallery_recently_deleted_album").performClick()
            val deleted = state(scenario).recentlyDeleted.single()
            scenario.onActivity { ViewModelProvider(it)[GalleryViewModel::class.java].select(deleted) }
            assertPhoto("gallery_viewer_image_${photo.id}", Color.RED)
            compose.onNodeWithText(app.getString(R.string.gallery_restore)).performClick()
            compose.waitUntil(5_000) { !state(scenario).busy && state(scenario).recentlyDeleted.isEmpty() }
            assertEquals(photo.id, state(scenario).images.single().id)
        }
    }

    @Test fun importedVideoCreatesItsAlbumAndPlaysFromEncryptedStorageAfterProviderRevocation() {
        ActivityScenario.launch<GalleryCoverActivity>(Intent(app, GalleryCoverActivity::class.java)).use { scenario ->
            compose.waitUntil(5_000) { !state(scenario).loading }
            scenario.onActivity { ViewModelProvider(it)[GalleryViewModel::class.java].importUris(listOf(videoUri)) }
            compose.waitUntil(10_000) { !state(scenario).busy && state(scenario).images.any { it.isVideo } }
            val video = state(scenario).images.single { it.isVideo }
            instrumentation.context.revokeUriPermission(videoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            app.contentResolver.delete(videoUri, null, null)
            compose.onNodeWithTag("gallery_section_ALBUMS").performClick()
            compose.onNodeWithTag("gallery_videos_album").assertIsDisplayed().performClick()
            scenario.onActivity { ViewModelProvider(it)[GalleryViewModel::class.java].select(video) }
            compose.waitUntil(10_000) {
                runCatching { compose.onNodeWithTag("gallery_video_play").assertIsEnabled() }.isSuccess
            }
            compose.onNodeWithTag("gallery_video_play").performClick()
            compose.waitUntil(15_000) {
                runCatching {
                    compose.onNodeWithTag("gallery_video_player").assert(SemanticsMatcher.expectValue(
                        androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                        app.getString(R.string.gallery_video_playing),
                    ))
                }.isSuccess
            }
            compose.onNodeWithTag("gallery_video_play").performClick()
            compose.onNodeWithTag("gallery_video_player").assert(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                app.getString(R.string.gallery_video_paused),
            ))
            assertEquals(video.id, state(scenario).selectedImageId)
        }
    }

    private fun captureGalleryPreview(name: String) {
        compose.waitForIdle()
        for (command in listOf("mkdir -p /data/local/tmp/calendar-previews",
            "screencap -p /data/local/tmp/calendar-previews/$name")) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
    }

    @Test fun largePreviewIsSampledAndKeepsAspectRatioAndColor() {
        val source = Bitmap.createBitmap(4096, 1024, Bitmap.Config.ARGB_8888)
        val data = ByteArrayOutputStream()
        try {
            source.eraseColor(Color.GREEN)
            assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, data))
        } finally { source.recycle() }
        val preview = requireNotNull(decodeGalleryPreview(data.toByteArray(), 2048))
        assertEquals(2048, preview.width)
        assertEquals(512, preview.height)
        val pixel = preview.toPixelMap()[preview.width / 2, preview.height / 2]
        assertTrue(pixel.green > 0.8f && pixel.red < 0.2f && pixel.blue < 0.2f)
    }

    private fun assertPhoto(tag: String, color: Int) {
        compose.waitUntil(5_000) {
            if (compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
                false
            } else {
                val image = compose.onNodeWithTag(tag).captureToImage()
                val pixel = image.toPixelMap()[image.width / 2, image.height / 2]
                kotlin.math.abs(pixel.red - Color.red(color) / 255f) < 0.2f &&
                    kotlin.math.abs(pixel.green - Color.green(color) / 255f) < 0.2f &&
                    kotlin.math.abs(pixel.blue - Color.blue(color) / 255f) < 0.2f
            }
        }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun state(scenario: ActivityScenario<GalleryCoverActivity>): GalleryUiState {
        lateinit var state: GalleryUiState
        scenario.onActivity { state = ViewModelProvider(it)[GalleryViewModel::class.java].uiState }
        return state
    }

    private fun pickerResult(uri: Uri): android.app.Instrumentation.ActivityMonitor {
        val filter = IntentFilter().apply {
            addAction("android.provider.action.PICK_IMAGES")
            addAction("androidx.activity.result.contract.action.PICK_IMAGES")
            addAction("com.google.android.gms.provider.action.PICK_IMAGES")
            addAction(Intent.ACTION_OPEN_DOCUMENT)
            addCategory(Intent.CATEGORY_OPENABLE)
            addDataType("image/*")
        }
        return instrumentation.addMonitor(filter, ActivityResult(Activity.RESULT_OK,
            Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)), true)
    }

    private fun writeJpeg(file: File, color: Int) {
        file.outputStream().use { writeJpeg(it, color) }
    }

    private fun writeJpeg(output: OutputStream, color: Int) {
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        } finally { bitmap.recycle() }
    }
}
