package com.pulse.bluetoothdisable.cover.calendar

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.pulse.bluetoothdisable.MainActivity
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.cover.CoverMode
import com.pulse.bluetoothdisable.cover.CoverModeManager
import com.pulse.bluetoothdisable.cover.CoverModeNavigator
import com.pulse.bluetoothdisable.cover.CoverModeStore
import com.pulse.bluetoothdisable.cover.calculator.CalculatorAccessCodeManager
import com.pulse.bluetoothdisable.launcher.LauncherIconController
import com.pulse.bluetoothdisable.launcher.LauncherStyle
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.testing.DeviceOwnerFixture
import com.pulse.bluetoothdisable.testing.pressSystemBack
import java.time.LocalDate
import kotlin.math.abs
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarCoverInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = CoverModeManager(context)
    private val deviceOwner = DeviceOwnerFixture(context)
    private val date = LocalDate.of(2012, 12, 12)

    @Before fun before() {
        manager.resetToDefault()
        LanguageManager.setSelectedLanguage(context, LanguageManager.ENGLISH)
    }
    @After fun after() {
        try { manager.resetToDefault() } finally { deviceOwner.clear() }
    }

    @Test fun galleryChoiceStartsSetupWithoutChangingExistingCover() {
        deviceOwner.provision()
        val controller = LauncherIconController(context)
        manager.activateCalculator("58317")
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            waitForChangeIcon()
            compose.onNodeWithText("CHANGE ICON").performScrollTo().performClick()
            for (option in listOf("Default", "Calculator", "Notes", "Calendar", "Gallery")) {
                compose.onNodeWithText(option).performScrollTo().assertIsDisplayed()
            }
            capturePreview("restored-cover-options.png")
            compose.onNodeWithText("Gallery").performScrollTo().performClick()
            compose.onNodeWithText("Gallery mode").assertIsDisplayed()
            assertEquals(CoverMode.CALCULATOR, manager.activeMode())
            assertEquals(LauncherStyle.CALCULATOR, controller.selectedStyle())
            assertTrue(CalculatorAccessCodeManager(context).verify("58317"))
            compose.onNodeWithText("CANCEL").performClick()
            assertEquals(CoverMode.CALCULATOR, manager.activeMode())
        }
    }

    @Test fun noteActionsExpandVerticallyWithoutUnlockingAndKeepEditDeleteBehavior() {
        manager.activateCalendar(date, "access note")
        val repo = LocalCalendarNotesRepository(context)
        repo.save(date, "access note")
        ActivityScenario.launch<CalendarCoverActivity>(Intent(context, CalendarCoverActivity::class.java)).use { scenario ->
            scenario.onActivity { ViewModelProvider(it)[CalendarViewModel::class.java].select(date) }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("access note").fetchSemanticsNodes().isNotEmpty() }
            // Bring the entire card above the floating Add button before tapping
            // its upper-right control; scrolling only the icon can leave it covered.
            val firstNoteIndex = 2 + CalendarDates.monthCells(java.time.YearMonth.from(date)).size / 7
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(firstNoteIndex)
            compose.onNodeWithText("Edit note").assertDoesNotExist()
            compose.onNodeWithText("Delete").assertDoesNotExist()
            compose.onNodeWithContentDescription("Show note actions").performClick()
            scenario.onActivity {
                assertFalse("The chevron must not open the Add/Edit note dialog",
                    ViewModelProvider(it)[CalendarViewModel::class.java].uiState.editorOpen)
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Edit note").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("calendar_note_text").assertDoesNotExist()
            compose.onNodeWithText("Edit note").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Delete").performScrollTo().assertIsDisplayed()
            val edit = compose.onNodeWithText("Edit note").fetchSemanticsNode().boundsInRoot
            val delete = compose.onNodeWithText("Delete").fetchSemanticsNode().boundsInRoot
            assertTrue("Delete belongs below Edit", delete.top >= edit.bottom)
            assertFalse(isMainResumed())
            capturePreview("calendar-note-actions-expanded.png")
            compose.onNodeWithText("Edit note").performClick()
            assertEquals("access note", compose.onNodeWithTag("calendar_note_text")
                .fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            assertFalse(isMainResumed())
            compose.onNodeWithText("CANCEL").performClick()
            compose.onNodeWithContentDescription("Hide note actions").performClick()
            compose.onNodeWithText("Delete").assertDoesNotExist()
            compose.onNodeWithContentDescription("Show note actions").performClick()
            compose.onNodeWithText("Delete").performScrollTo().performClick()
            compose.onNodeWithText("CANCEL").performClick()
            assertEquals(1, repo.notes().size)
            compose.onNodeWithText("Delete").performClick()
            compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(5_000) { repo.notes().isEmpty() }
            assertFalse(isMainResumed())
            assertEquals(CoverMode.CALENDAR, manager.activeMode())
        }
    }

    @Test fun calendarWarningUsesBottomSheetAndDateButtonOpensPicker() {
        deviceOwner.provision()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            waitForChangeIcon()
            compose.onNodeWithText("CHANGE ICON").performScrollTo().performClick()
            compose.onNodeWithText("Calendar").performScrollTo().performClick()
            compose.onNodeWithText("Calendar mode").assertIsDisplayed()
            val dialog = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
            val sheet = compose.onNodeWithTag("cover_mode_bottom_sheet").fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Warning sheet must be attached to the bottom of the dialog",
                abs(sheet.bottom - dialog.bottom) <= 2f,
            )
            assertTrue("Warning sheet must not fill the whole dialog", sheet.top > dialog.top)
            capturePreview("calendar-warning-bottom-sheet.png")
            compose.onNodeWithText("CONTINUE").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("calendar_access_date_picker").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("calendar_access_date_picker").assertIsDisplayed()
            capturePreview("calendar-setup-date-chevron.png")
            compose.onNodeWithTag("calendar_access_date_picker").performClick()
            compose.onNode(isDialog()).assertExists()
            compose.onNode(hasText("CANCEL") and hasAnyAncestor(isDialog())).performClick()
            assertEquals(CoverMode.DEFAULT, manager.activeMode())
        }
    }

    private fun waitForChangeIcon() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("CHANGE ICON").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun saveDoesNotUnlockButNoteTapOpensMainAndHideClearsTask() {
        manager.activateCalendar(date, "открой меня")
        ActivityScenario.launch<CalendarCoverActivity>(Intent(context, CalendarCoverActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[CalendarViewModel::class.java].select(date)
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("No notes for this date.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Add note").performClick()
            compose.onNodeWithTag("calendar_note_text").performTextInput("открой меня")
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("calendar_note_text").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("открой меня").assertIsDisplayed()
            assertFalse(isMainResumed())
            capturePreview("calendar-saved-note.png")
            compose.onNodeWithText("открой меня").performClick()
            compose.waitUntil(5_000) { isMainResumed() }
            assertTrue(LocalCalendarNotesRepository(context).notes().any { it.text == "открой меня" })
            compose.onNodeWithText("HIDE").assertIsDisplayed().performClick()
            compose.waitUntil(5_000) { !isMainResumed() }
            compose.onNodeWithTag("calendar_day_${LocalDate.now()}").assertExists()
            capturePreview("calendar-after-hide.png")
            pressSystemBack()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertFalse(isMainResumed())
        }
    }

    @Test fun mismatchedDateAndCaseOpenReadOnlyViewer() {
        manager.activateCalendar(date, "открой меня")
        val repo = LocalCalendarNotesRepository(context)
        val wrongDate = repo.save(date.plusDays(1), "открой меня")
        val wrongText = repo.save(date, "Открой меня")
        ActivityScenario.launch<CalendarCoverActivity>(Intent(context, CalendarCoverActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
            for (note in listOf(wrongDate, wrongText)) {
                scenario.onActivity { activity ->
                    ViewModelProvider(activity)[CalendarViewModel::class.java].select(note.date)
                }
                compose.waitUntil(5_000) { compose.onAllNodesWithText(note.text).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(note.text).performClick()
                compose.waitUntil(5_000) { compose.onAllNodesWithTag("calendar_note_view").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("calendar_note_text").assertDoesNotExist()
                compose.onNodeWithTag("calendar_note_view").assertIsDisplayed()
                assertFalse(isMainResumed())
                compose.onNodeWithText("CLOSE").performClick()
            }
        }
    }

    @Test fun setupActivatesOnlyAfterFinalConfirmationAndCreatesNoAccessNote() {
        ActivityScenario.launch<CalendarCoverSetupActivity>(Intent(context, CalendarCoverSetupActivity::class.java)).use {
            compose.onNode(hasSetTextAction()).performTextInput("my calendar text")
            compose.onNodeWithText("CONTINUE").performClick()
            assertEquals(CoverMode.DEFAULT, manager.activeMode())
            assertEquals(LauncherStyle.DEFAULT, LauncherIconController(context).selectedStyle())
            assertFalse(CalendarAccessManager(context).hasRule())
            compose.onNodeWithText("FINISH SETUP").performClick()
            val failureText = context.getString(R.string.calendar_setup_failed)
            var activationFailureReported = false
            compose.waitUntil(15_000) {
                if (manager.activeMode() == CoverMode.CALENDAR) {
                    true
                } else {
                    activationFailureReported = runCatching {
                        compose.onAllNodesWithText(failureText).fetchSemanticsNodes().isNotEmpty()
                    }.getOrDefault(false)
                    activationFailureReported
                }
            }
            assertFalse("Calendar setup reported an activation failure", activationFailureReported)
            assertEquals(CoverMode.CALENDAR, manager.activeMode())
            assertTrue("Calendar activation did not complete successfully", manager.isCalendarReady())
            assertTrue(CalendarAccessManager(context).verify(LocalDate.now(), "my calendar text"))
            assertTrue(LocalCalendarNotesRepository(context).notes().isEmpty())
        }
    }

    @Test fun cancellationDoesNotChangeExistingModeOrLauncher() {
        manager.activateCalculator("58317")
        ActivityScenario.launch<CalendarCoverSetupActivity>(Intent(context, CalendarCoverSetupActivity::class.java)).use {
            compose.onNodeWithText("CANCEL").performClick()
        }
        assertEquals(CoverMode.CALCULATOR, manager.activeMode())
        assertEquals(LauncherStyle.CALCULATOR, LauncherIconController(context).selectedStyle())
        assertTrue(CalculatorAccessCodeManager(context).verify("58317"))
        assertFalse(CalendarAccessManager(context).hasRule())
    }

    @Test fun modeSwitchClearsOnlyPreviousAccessAndResetClearsNotes() {
        manager.activateCalculator("58317")
        manager.activateCalendar(date, "открой меня")
        assertEquals(CoverMode.CALENDAR, manager.activeMode())
        assertTrue(manager.isCalendarReady())
        assertFalse(CalculatorAccessCodeManager(context).hasCode())
        val repo = LocalCalendarNotesRepository(context)
        repo.save(date, "Заметка")
        manager.activateCalculator("91347")
        assertTrue(manager.isCalculatorReady())
        assertFalse(CalendarAccessManager(context).hasRule())
        assertNull(CalendarAccessManager(context).secretDate())
        assertEquals(1, repo.notes().size)
        manager.resetToDefault()
        assertEquals(CoverMode.DEFAULT, manager.activeMode())
        assertTrue(repo.notes().isEmpty())
        assertEquals(LauncherStyle.DEFAULT, LauncherIconController(context).selectedStyle())
    }

    @Test fun interruptedCalendarActivationRestoresPreviousCalculator() {
        manager.activateCalculator("58317")
        val snapshot = snapshot()
        CoverModeStore(context).beginTransition(CoverMode.CALENDAR, snapshot)
        CalendarAccessManager(context).setAccessRule(date, "открой меня")
        LauncherIconController(context).setStyle(LauncherStyle.CALENDAR)
        CoverModeStore(context).setActiveMode(CoverMode.CALENDAR)
        manager.recoverInterruptedSetup()
        assertTrue(manager.isCalculatorReady())
        assertTrue(CalculatorAccessCodeManager(context).verify("58317"))
        assertFalse(CalendarAccessManager(context).hasRule())
    }

    @Test fun interruptedSameModeSetupRestoresPreviousVerifier() {
        manager.activateCalendar(date, "открой меня")
        CoverModeStore(context).beginTransition(CoverMode.CALENDAR, snapshot())
        CalendarAccessManager(context).setAccessRule(date.plusDays(1), "другой текст")
        manager.recoverInterruptedSetup()
        assertTrue(manager.isCalendarReady())
        assertTrue(CalendarAccessManager(context).verify(date, "открой меня"))
        assertFalse(CalendarAccessManager(context).verify(date.plusDays(1), "другой текст"))
    }

    private fun snapshot(): JSONObject = JSONObject().apply {
        put("mode", manager.activeMode().name)
        put("style", LauncherIconController(context).selectedStyle().name)
        put("hidden", false)
        for ((name, key) in listOf(
            CalculatorAccessCodeManager.PREFERENCES_NAME to "calculator",
            CalendarAccessManager.PREFERENCES_NAME to "calendar",
        )) {
            put(key, JSONObject().apply {
                context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) -> put(key, value) }
            })
        }
    }

    private fun capturePreview(name: String) {
        compose.waitForIdle()
        // Use a shell-owned temporary directory: Gradle removes app data after tests.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (command in listOf("mkdir -p /data/local/tmp/calendar-previews",
            "screencap -p /data/local/tmp/calendar-previews/$name")) {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
                it.readBytes()
            }
        }
    }

    private fun isMainResumed(): Boolean {
        var main: MainActivity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            main = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().firstOrNull()
        }
        return main != null
    }
}
