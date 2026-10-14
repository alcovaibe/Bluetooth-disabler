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
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.cover.CoverModeManager
import com.pulse.bluetoothdisable.localization.LanguageManager
import java.io.File
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

    @Before fun before() {
        manager.resetToDefault()
        repository.images().forEach { repository.delete(it.id) }
        LanguageManager.setSelectedLanguage(app, LanguageManager.ENGLISH)
        requireNotNull(app.contentResolver.openOutputStream(picked)).use { writeJpeg(it, Color.BLUE) }
        requireNotNull(app.contentResolver.openOutputStream(invalid)).use { it.write("not an image".toByteArray()) }
        for (uri in listOf(picked, invalid)) {
            instrumentation.context.grantUriPermission(app.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val seed = File(app.cacheDir, "gallery-picker-seed.jpg")
        writeJpeg(seed, Color.RED)
        val secret = repository.importUris(app.contentResolver, listOf(Uri.fromFile(seed))).single()
        manager.activateGallery(secret.id, listOf(GalleryTapZone.TOP_LEFT, GalleryTapZone.CENTER, GalleryTapZone.BOTTOM_RIGHT))
    }

    @After fun after() {
        manager.resetToDefault()
        repository.images().forEach { repository.delete(it.id) }
        for (uri in listOf(picked, invalid)) instrumentation.context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        app.contentResolver.delete(picked, null, null)
        app.contentResolver.delete(invalid, null, null)
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
