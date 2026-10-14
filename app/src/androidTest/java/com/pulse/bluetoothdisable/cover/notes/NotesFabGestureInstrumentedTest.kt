package com.pulse.bluetoothdisable.cover.notes

import android.app.Application
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesFabGestureInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val repository = LocalNotesRepository(app)
    private lateinit var viewModel: NotesViewModel

    @Before
    fun before() {
        repository.clear()
        compose.runOnUiThread { viewModel = NotesViewModel(app) }
    }

    @After
    fun after() {
        repository.clear()
    }

    private fun show(darkTheme: Boolean = false, onRecovery: () -> Unit = {}) {
        compose.setContent {
            BluetoothDisableTheme(darkTheme = darkTheme) {
                NotesScreen(
                    viewModel = viewModel,
                    recoveryEnabled = true,
                    onRecoveryHold = onRecovery,
                    onUnlock = {},
                )
            }
        }
        compose.waitUntil(3_000) { !viewModel.uiState.loading }
    }

    @Test
    fun shortTapOnNotesOpensNoteEditorDirectlyWithoutRecovery() {
        val recoveries = AtomicInteger()
        show { recoveries.incrementAndGet() }

        compose.onNodeWithContentDescription(app.getString(R.string.notes_add)).performClick()
        compose.waitForIdle()

        assertEquals(0, recoveries.get())
        compose.onNodeWithContentDescription(app.getString(R.string.notes_back)).assertExists()
        assertEquals(true, viewModel.uiState.editorOpen)
        assertEquals(true, viewModel.uiState.draftIsNew)
        assertEquals(NoteType.TEXT, viewModel.uiState.draft!!.type)
        compose.onNodeWithTag("notes_create_dialog").assertDoesNotExist()
    }

    @Test
    fun threeSecondHoldRunsRecoveryOnceWithoutOpeningEditor() {
        val recoveries = AtomicInteger()
        show { recoveries.incrementAndGet() }
        compose.onNodeWithTag("notes_section_CHECKLIST").performClick()
        compose.mainClock.autoAdvance = false
        val fab = compose.onNodeWithContentDescription(app.getString(R.string.notes_add_task))

        fab.performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
        SystemClock.sleep(3_150)
        compose.mainClock.advanceTimeBy(3_150)
        compose.waitUntil(2_000) { recoveries.get() == 1 }
        fab.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()

        assertEquals(1, recoveries.get())
        assertEquals(false, viewModel.uiState.editorOpen)
        compose.onNodeWithContentDescription(app.getString(R.string.notes_add_task)).assertExists()
        compose.onNodeWithTag("notes_create_dialog").assertDoesNotExist()
    }

    @Test
    fun earlyReleaseRemainsOrdinaryTap() {
        val recoveries = AtomicInteger()
        show { recoveries.incrementAndGet() }
        compose.mainClock.autoAdvance = false
        val fab = compose.onNodeWithContentDescription(app.getString(R.string.notes_add))

        fab.performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
        SystemClock.sleep(500)
        compose.mainClock.advanceTimeBy(500)
        fab.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()

        assertEquals(0, recoveries.get())
        assertEquals(true, viewModel.uiState.editorOpen)
        assertEquals(NoteType.TEXT, viewModel.uiState.draft!!.type)
        compose.onNodeWithTag("notes_create_dialog").assertDoesNotExist()
    }

    @Test
    fun plusOnTasksOpensChecklistDirectlyAndEmptyDraftIsNotSaved() {
        show(darkTheme = true)
        compose.onNodeWithTag("notes_section_CHECKLIST").performClick()
        compose.onNodeWithContentDescription(app.getString(R.string.notes_add_task)).performClick()
        assertEquals(NoteType.CHECKLIST, viewModel.uiState.draft!!.type)
        compose.onNodeWithText(app.getString(R.string.notes_task)).assertExists()
        assertEquals(1, viewModel.uiState.draft!!.checklist.size)
        compose.onNodeWithContentDescription(app.getString(R.string.notes_back)).performClick()
        compose.onNodeWithTag("notes_section_CHECKLIST").assertIsSelected()
        assertEquals(0, repository.notes().size)
        compose.onNodeWithTag("notes_section_TEXT").performClick()
        compose.onNodeWithContentDescription(app.getString(R.string.notes_add)).performClick()
        assertEquals(NoteType.TEXT, viewModel.uiState.draft!!.type)
    }

    @Test
    fun bottomSectionsFilterExistingNotesWithoutChangingStorage() {
        val note = repository.save("ordinary-note", "note body")
        repository.upsert(note.copy(id = "task-id", title = "ordinary-task", body = "", type = NoteType.CHECKLIST,
            checklist = listOf(ChecklistItem("row", "task body", false))))
        compose.runOnUiThread { viewModel.refresh() }
        show()
        compose.waitUntil(3_000) { viewModel.uiState.notes.size == 2 }
        compose.onNodeWithText("ordinary-note").assertExists()
        compose.onNodeWithText("ordinary-task").assertDoesNotExist()
        compose.onNodeWithTag("notes_section_CHECKLIST").performClick()
        compose.onNodeWithText("ordinary-task").assertExists()
        compose.onNodeWithText("ordinary-note").assertDoesNotExist()
        compose.onNodeWithTag("notes_section_TEXT").performClick()
        compose.onNodeWithText("ordinary-note").assertExists()
        assertEquals(2, repository.notes().size)
    }

    @Test
    fun compactCardEllipsizesTitleAndFourthLineAndHasNoFooter() {
        val title = "A long note title ".repeat(8).take(120)
        val body = "First\nSecond\nThird\n" + "A long fourth line ".repeat(50) + "\nFifth line"
        val note = repository.save(title, body)
        compose.runOnUiThread { viewModel = NotesViewModel(app) }
        show()
        compose.waitUntil(3_000) { viewModel.uiState.notes.size == 1 }
        val titleNode = compose.onNodeWithTag("notes_card_title_${note.id}", useUnmergedTree = true)
        val previewNode = compose.onNodeWithTag("notes_card_preview_${note.id}", useUnmergedTree = true)
        val titleLayouts = mutableListOf<TextLayoutResult>()
        val previewLayouts = mutableListOf<TextLayoutResult>()
        titleNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(titleLayouts) }
        previewNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(previewLayouts) }
        assertEquals(1, titleLayouts.single().lineCount)
        assertTrue(titleLayouts.single().isLineEllipsized(0))
        assertEquals(4, previewLayouts.single().lineCount)
        assertTrue(previewLayouts.single().isLineEllipsized(3))
        val textHeight = titleNode.fetchSemanticsNode().boundsInRoot.height +
            previewNode.fetchSemanticsNode().boundsInRoot.height
        val cardHeight = compose.onNodeWithTag("notes_card_${note.id}").fetchSemanticsNode().boundsInRoot.height
        val insets = with(compose.density) { 16.dp.toPx() }
        assertTrue("Only title, preview and small content insets belong in the card",
            kotlin.math.abs(cardHeight - textHeight - insets) <= 2f)
        assertEquals(body, repository.notes().single().body)
    }
}
