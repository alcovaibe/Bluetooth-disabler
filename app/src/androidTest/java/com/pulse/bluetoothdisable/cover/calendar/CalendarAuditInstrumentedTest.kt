package com.pulse.bluetoothdisable.cover.calendar

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pulse.bluetoothdisable.cover.CoverModeManager
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.R
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarAuditInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = CoverModeManager(context)
    private val date = LocalDate.of(2026, 10, 3)

    @Before fun before() {
        manager.resetToDefault()
        CalendarDraftStore(context).clearAll()
        LanguageManager.setSelectedLanguage(context, LanguageManager.ENGLISH)
    }

    @After fun after() {
        manager.resetToDefault()
        CalendarDraftStore(context).clearAll()
    }

    @Test fun monthNavigationAlwaysSelectsFirstDayOfDestinationMonth() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var viewModel: CalendarViewModel

        // CalendarViewModel owns Compose state and is driven from Main by the real UI.
        // Keep direct state mutations on Main as well. The old test mutated uiState from the
        // instrumentation thread while the initial refresh resumed on Main, which allowed two
        // unsynchronised read-copy-write operations to race.
        instrumentation.runOnMainSync {
            viewModel = CalendarViewModel(application)
            viewModel.select(LocalDate.of(2024, 1, 31))
            viewModel.moveMonth(1)
        }

        // The refresh launched from init is intentionally allowed to complete after navigation.
        // Verify that its notes/loading update preserves the navigation state selected on Main.
        waitForInitialRefresh(viewModel)

        instrumentation.runOnMainSync {
            assertEquals(LocalDate.of(2024, 2, 1), viewModel.uiState.selectedDate)
            viewModel.moveMonth(-1)
            assertEquals(LocalDate.of(2024, 1, 1), viewModel.uiState.selectedDate)
        }
    }

    @Test fun newNoteDraftSurvivesActivityRestartAndRemainsBoundToItsDate() {
        manager.activateCalendar(date, "secret text")

        ActivityScenario.launch<CalendarCoverActivity>(
            Intent(context, CalendarCoverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[CalendarViewModel::class.java].select(date)
            }
            compose.onNodeWithContentDescription("Add note").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("calendar_note_text").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("calendar_note_text").performTextInput("draft text")
            compose.onNodeWithText("CANCEL").performClick()
            compose.waitUntil(5_000) {
                CalendarDraftStore(context).text(date) == "draft text"
            }
        }

        ActivityScenario.launch<CalendarCoverActivity>(
            Intent(context, CalendarCoverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ).use { scenario ->
            val otherDate = date.plusDays(1)
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[CalendarViewModel::class.java].select(otherDate)
            }
            compose.onNodeWithContentDescription("Add note").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("calendar_note_text").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(
                "",
                compose.onNodeWithTag("calendar_note_text")
                    .fetchSemanticsNode().config[SemanticsProperties.EditableText].text,
            )
            compose.onNodeWithText("CANCEL").performClick()

            scenario.onActivity { activity ->
                ViewModelProvider(activity)[CalendarViewModel::class.java].select(date)
            }
            compose.onNodeWithContentDescription("Add note").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("calendar_note_text").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(
                "draft text",
                compose.onNodeWithTag("calendar_note_text")
                    .fetchSemanticsNode().config[SemanticsProperties.EditableText].text,
            )
        }
    }

    @Test fun twentySixthNoteIsBlockedBeforeEditorOpens() {
        manager.activateCalendar(date, "secret text")
        val repository = LocalCalendarNotesRepository(context)
        repeat(CalendarNotePolicy.MAX_NOTES_PER_DATE) { index ->
            repository.save(date, "note-$index")
        }

        ActivityScenario.launch<CalendarCoverActivity>(
            Intent(context, CalendarCoverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[CalendarViewModel::class.java].select(date)
            }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("note-24").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Add note").performClick()
            compose.onNodeWithTag("calendar_note_text").assertDoesNotExist()
            compose.onNodeWithText("You can store up to 25 notes on one date.").assertIsDisplayed()
        }
    }

    @Test fun dateSearchIsReadOnlyAndForgottenAfterClosePauseAndRecreation() {
        manager.activateCalendar(date, "secret text")
        val target = LocalDate.of(1980, 2, 29)
        val repository = LocalCalendarNotesRepository(context)
        repository.save(target, "read-only-search-note")
        val before = repository.notes()
        val drafts = CalendarDraftStore(context)
        drafts.put(date, text = "existing draft")

        ActivityScenario.launch<CalendarCoverActivity>(Intent(context, CalendarCoverActivity::class.java)).use { scenario ->
            scenario.onActivity { ViewModelProvider(it)[CalendarViewModel::class.java].select(date) }
            compose.waitUntil(5_000) {
                var loaded = false
                scenario.onActivity { loaded = !ViewModelProvider(it)[CalendarViewModel::class.java].uiState.loading }
                loaded
            }
            compose.onNodeWithContentDescription(context.getString(R.string.calendar_search_date)).performClick()
            compose.onNodeWithTag("calendar_search_day").performTextInput("29")
            compose.onNodeWithTag("calendar_search_month").performTextInput("2")
            compose.onNodeWithTag("calendar_search_year").performTextInput("1981")
            compose.onNodeWithTag("calendar_search_submit").assertIsNotEnabled()
            compose.onNodeWithTag("calendar_search_year").performTextReplacement("1980")
            compose.onNodeWithTag("calendar_search_submit").performClick()
            compose.onNodeWithText("read-only-search-note").assertIsDisplayed()
            scenario.onActivity {
                val state = ViewModelProvider(it)[CalendarViewModel::class.java].uiState
                assertEquals(date, state.selectedDate)
                assertFalse(state.editorOpen)
            }
            compose.onNodeWithText(context.getString(R.string.calendar_close)).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.calendar_search_date)).performClick()
            assertEquals("", compose.onNodeWithTag("calendar_search_day")
                .fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            compose.onNodeWithTag("calendar_search_result").assertDoesNotExist()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("calendar_date_search").assertDoesNotExist()
            compose.onNodeWithContentDescription(context.getString(R.string.calendar_search_date)).performClick()
            compose.onNodeWithTag("calendar_search_year").performTextInput("1980")
            scenario.recreate()
            compose.onNodeWithTag("calendar_date_search").assertDoesNotExist()
        }
        assertEquals(before, repository.notes())
        assertEquals("existing draft", drafts.text(date))
        assertEquals(null, drafts.text(target))
    }

    private fun waitForInitialRefresh(viewModel: CalendarViewModel) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + INITIAL_REFRESH_TIMEOUT_MILLIS

        while (SystemClock.uptimeMillis() < deadline) {
            var loading = true
            instrumentation.runOnMainSync {
                loading = viewModel.uiState.loading
            }
            if (!loading) return
            SystemClock.sleep(20)
        }

        fail("CalendarViewModel initial refresh did not complete")
    }

    companion object {
        private const val INITIAL_REFRESH_TIMEOUT_MILLIS = 5_000L
    }
}
