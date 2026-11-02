package com.pulse.bluetoothdisable.cover.notes

import android.app.Application
import android.content.res.Configuration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesSelectionInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val repository = LocalNotesRepository(app)
    private val access = NotesAccessManager(app)
    private val store = ViewModelStore()
    private lateinit var viewModel: NotesViewModel

    @Before fun before() {
        repository.clear()
        access.clear()
    }

    @After fun after() {
        compose.runOnUiThread { store.clear() }
        repository.clear()
        access.clear()
    }

    private fun show(onUnlock: () -> Unit = {}) {
        compose.runOnUiThread {
            val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
            viewModel = ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory(app))[NotesViewModel::class.java]
        }
        compose.setContent {
            BluetoothDisableTheme {
                NotesScreen(viewModel = viewModel, onUnlock = onUnlock)
            }
        }
        compose.waitUntil(5_000) { !viewModel.uiState.loading }
    }

    @Test fun selectedNotesRequireConfirmationAndCancelPreservesSelection() {
        val first = repository.save("First", "first body")
        val second = repository.save("Second", "second body")
        val kept = repository.save("Keep", "keep body")
        show()
        compose.onNodeWithTag("notes_card_${first.id}").performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithTag("notes_card_${second.id}").performScrollTo().performClick()
        assertEquals(setOf(first.id, second.id), viewModel.uiState.selectedNoteIds)
        compose.onNodeWithTag("notes_delete_selected").performClick()
        compose.onNodeWithText(app.resources.getQuantityString(R.plurals.notes_delete_selected_title, 2, 2)).assertExists()
        compose.onNodeWithText(app.getString(R.string.cancel)).performClick()
        assertEquals(3, repository.notes().size)
        assertEquals(2, viewModel.uiState.selectedNoteIds.size)
        compose.onNodeWithTag("notes_delete_selected").performClick()
        compose.onNodeWithTag("notes_delete_confirm").performClick()
        compose.waitUntil(5_000) { !viewModel.uiState.busy && viewModel.uiState.notes.size == 1 }
        assertEquals(kept.id, repository.notes().single().id)
        assertTrue(viewModel.uiState.selectedNoteIds.isEmpty())
    }

    @Test fun secretNoteUsesOrdinaryDeletionAndOnlyItsVerifierIsCleared() {
        val secret = repository.save("Access", "secret fragment")
        val kept = repository.save("Keep", "other body")
        access.setAccessRule(secret.id, secret.body, 0, 6)
        show()
        compose.onNodeWithTag("notes_card_${secret.id}").performClick()
        compose.onNodeWithContentDescription(app.getString(R.string.notes_delete)).performClick()
        compose.onNodeWithTag("notes_delete_dialog").assertExists()
        compose.onNodeWithText(app.getString(R.string.notes_hidden_access_warning_title)).assertDoesNotExist()
        compose.onNodeWithTag("notes_delete_confirm").performClick()
        compose.waitUntil(5_000) { !viewModel.uiState.busy && viewModel.uiState.notes.size == 1 }
        assertFalse(access.hasRule())
        assertEquals(kept.id, repository.notes().single().id)
    }

    @Test fun detailTapsToggleDockOnTextAndEmptySpaceWithoutBreakingHiddenEntry() {
        val note = repository.save("Access", "secret fragment")
        access.setAccessRule(note.id, note.body, 0, note.body.length)
        val unlocks = AtomicInteger()
        show { unlocks.incrementAndGet() }
        compose.onNodeWithTag("notes_card_${note.id}").performClick()
        compose.onNodeWithTag("notes_action_dock").assertExists()
        compose.onNodeWithText(note.body).performTouchInput { click() }
        compose.onNodeWithTag("notes_action_dock").assertDoesNotExist()
        compose.waitUntil(5_000) { unlocks.get() == 1 }
        compose.onNodeWithTag("notes_detail_content").performTouchInput {
            click(Offset(center.x, height - 30f))
        }
        compose.onNodeWithTag("notes_action_dock").assertExists()
        compose.onNodeWithTag("notes_detail_content").performTouchInput {
            click(Offset(center.x, height - 30f))
        }
        compose.onNodeWithTag("notes_action_dock").assertDoesNotExist()
        assertEquals(1, unlocks.get())
    }

    @Test fun batchDeleteKeepsUnselectedAttachments() {
        val first = repository.save("First", "first")
        val second = repository.save("Second", "second")
        val kept = repository.save("Keep", "keep")
        val directory = java.io.File(app.filesDir, LocalNotesRepository.IMAGE_DIR_NAME)
        val removedImage = NoteImage("removed", "removed.img", 0)
        val keptImage = NoteImage("kept", "kept.img", 0)
        java.io.File(directory, removedImage.fileName).writeBytes(byteArrayOf(1))
        repository.upsert(first.copy(images = listOf(removedImage)))
        java.io.File(directory, keptImage.fileName).writeBytes(byteArrayOf(2))
        repository.upsert(kept.copy(images = listOf(keptImage)))
        repository.deleteAll(setOf(first.id, second.id))
        assertEquals(kept.id, repository.notes().single().id)
        assertEquals(null, repository.imageFile(removedImage))
        assertTrue(repository.imageFile(keptImage)!!.exists())
    }

    @Test fun deletionCountUsesRussianAndEnglishPluralRules() {
        fun resources(language: String) = app.createConfigurationContext(
            Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) },
        ).resources
        val russian = resources("ru")
        mapOf(1 to "заметку", 2 to "заметки", 5 to "заметок", 11 to "заметок",
            21 to "заметку", 22 to "заметки", 25 to "заметок", 111 to "заметок").forEach { (count, ending) ->
            assertEquals("Удалить $count $ending?", russian.getQuantityString(R.plurals.notes_delete_selected_title, count, count))
        }
        val english = resources("en")
        assertEquals("Delete 1 note?", english.getQuantityString(R.plurals.notes_delete_selected_title, 1, 1))
        assertEquals("Delete 2 notes?", english.getQuantityString(R.plurals.notes_delete_selected_title, 2, 2))
    }
}
