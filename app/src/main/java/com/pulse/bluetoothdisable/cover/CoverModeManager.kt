package com.pulse.bluetoothdisable.cover

import android.content.Context
import com.pulse.bluetoothdisable.cover.calculator.CalculatorAccessCodeManager
import com.pulse.bluetoothdisable.cover.calculator.CalculatorAccessCodePolicy
import com.pulse.bluetoothdisable.cover.calendar.CalendarAccessManager
import com.pulse.bluetoothdisable.cover.calendar.CalendarAccessPolicy
import com.pulse.bluetoothdisable.cover.calendar.CalendarDates
import com.pulse.bluetoothdisable.cover.calendar.LocalCalendarNotesRepository
import com.pulse.bluetoothdisable.cover.gallery.GalleryAccessManager
import com.pulse.bluetoothdisable.cover.notes.LocalNotesRepository
import com.pulse.bluetoothdisable.cover.notes.NotesAccessManager
import com.pulse.bluetoothdisable.launcher.LauncherIconController
import com.pulse.bluetoothdisable.launcher.LauncherStyle
import java.time.LocalDate
import org.json.JSONObject

class CoverModeManager(context: Context) {
    private val appContext = context.applicationContext
    private val store = CoverModeStore(appContext)
    private val launcher = LauncherIconController(appContext)
    private val calculatorAccess = CalculatorAccessCodeManager(appContext)
    private val calendarAccess = CalendarAccessManager(appContext)
    private val notesAccess = NotesAccessManager(appContext)
    private val galleryAccess = GalleryAccessManager(appContext)

    fun activeMode(): CoverMode = store.activeMode()

    fun isCalculatorReady(): Boolean =
        activeMode() == CoverMode.CALCULATOR &&
            launcher.selectedStyle() == LauncherStyle.CALCULATOR && calculatorAccess.hasCode()

    fun isCalendarReady(): Boolean =
        activeMode() == CoverMode.CALENDAR &&
            launcher.selectedStyle() == LauncherStyle.CALENDAR && calendarAccess.hasRule()

    /** Notes can deliberately remain active even after its hidden access note is edited/deleted. */
    fun isNotesReady(): Boolean =
        activeMode() == CoverMode.NOTES && launcher.selectedStyle() == LauncherStyle.NOTES

    /** Gallery is independent of photos and legacy hidden-access credentials. */
    fun isGalleryReady(): Boolean =
        activeMode() == CoverMode.GALLERY && launcher.selectedStyle() == LauncherStyle.GALLERY

    fun activateCalculator(code: String) {
        require(CalculatorAccessCodePolicy.isValid(code))
        transition(CoverMode.CALCULATOR, LauncherStyle.CALCULATOR) { calculatorAccess.setCode(code) }
    }

    fun activateCalendar(date: LocalDate, text: String) {
        require(CalendarAccessPolicy.isValid(text) && date.year in CalendarDates.YEAR_RANGE)
        transition(CoverMode.CALENDAR, LauncherStyle.CALENDAR) {
            calendarAccess.setAccessRule(date, text)
        }
    }

    fun activateNotes(noteId: String, start: Int, end: Int, body: String) {
        val stored = LocalNotesRepository(appContext).notes().firstOrNull { it.id == noteId }
        require(stored != null && stored.body == body) { "Selected note changed before activation" }
        transition(CoverMode.NOTES, LauncherStyle.NOTES) {
            notesAccess.setAccessRule(noteId, body, start, end)
        }
    }

    fun activateGallery() {
        transition(CoverMode.GALLERY, LauncherStyle.GALLERY) {}
    }

    fun deactivateToLauncher(style: LauncherStyle) {
        // Every non-default launcher style is now a full Cover Mode and requires its setup flow.
        require(style == LauncherStyle.DEFAULT)
        transition(CoverMode.DEFAULT, style) {}
    }

    /** Calculator-only recovery. History and all note/gallery stores are deliberately untouched. */
    fun resetCalculatorCover() {
        check(activeMode() == CoverMode.CALCULATOR)
        transition(CoverMode.DEFAULT, LauncherStyle.DEFAULT, verify = {
            check(launcher.isExclusivelyEnabled(LauncherStyle.DEFAULT)) {
                "Unable to restore default launcher"
            }
        }, cleanup = calculatorAccess::deleteKey) { calculatorAccess.clearVerifier() }
    }

    /** Disable Calendar disguise while retaining its notes and all other local data. */
    fun resetCalendarCover() {
        check(activeMode() == CoverMode.CALENDAR)
        transition(CoverMode.DEFAULT, LauncherStyle.DEFAULT, verify = {
            check(launcher.isExclusivelyEnabled(LauncherStyle.DEFAULT)) {
                "Unable to restore default launcher"
            }
        }, cleanup = calendarAccess::deleteKey) { calendarAccess.clearVerifier() }
    }

    /** Disable Notes disguise and its temporary hidden-access rule, but never delete user notes. */
    fun resetNotesCover() {
        check(activeMode() == CoverMode.NOTES)
        transition(CoverMode.DEFAULT, LauncherStyle.DEFAULT, verify = {
            check(launcher.isExclusivelyEnabled(LauncherStyle.DEFAULT)) {
                "Unable to restore default launcher"
            }
        }, cleanup = notesAccess::deleteKey) { notesAccess.clearVerifier() }
    }

    /** Disable Gallery disguise and remove legacy access credentials while retaining all imported photos and metadata. */
    fun resetGalleryCover() {
        check(activeMode() == CoverMode.GALLERY)
        transition(CoverMode.DEFAULT, LauncherStyle.DEFAULT, verify = {
            check(launcher.isExclusivelyEnabled(LauncherStyle.DEFAULT)) {
                "Unable to restore default launcher"
            }
        }, cleanup = galleryAccess::deleteKey) { galleryAccess.clearVerifier() }
    }

    /** The durable journal contains opaque access verifiers and offsets, never secret text/sequences.
     * Previous keys are retained until the commit point so process death can roll back.
     */
    private fun transition(
        mode: CoverMode,
        style: LauncherStyle,
        verify: () -> Unit = {},
        cleanup: () -> Unit = ::clearInactiveAccess,
        configure: () -> Unit,
    ) {
        val snapshot = JSONObject().apply {
            put("mode", activeMode().name)
            put("style", launcher.selectedStyle().name)
            put("hidden", launcher.isHidden())
            put("calculator", accessSnapshot(CalculatorAccessCodeManager.PREFERENCES_NAME))
            put("calendar", accessSnapshot(CalendarAccessManager.PREFERENCES_NAME))
            put("notes", accessSnapshot(NotesAccessManager.PREFERENCES_NAME))
            put("gallery", accessSnapshot(GalleryAccessManager.PREFERENCES_NAME))
        }
        store.beginTransition(mode, snapshot)
        try {
            configure()
            launcher.setStyle(style)
            verify()
            store.setActiveMode(mode)
            store.clearPending() // Commit point: everything needed by the new mode is durable.
        } catch (error: Exception) {
            restore(snapshot)
            throw error
        }
        cleanup()
    }

    fun resetToDefault() {
        calculatorAccess.clearCode()
        calendarAccess.clear()
        notesAccess.clear()
        galleryAccess.clear()
        LocalCalendarNotesRepository(appContext).clear()
        // Local Notes and Gallery photos are intentionally retained. They are user data, not Cover configuration.
        try {
            store.setActiveMode(CoverMode.DEFAULT)
            store.clearPending()
        } finally {
            launcher.setStyle(LauncherStyle.DEFAULT)
        }
    }

    fun recoverInterruptedSetup() {
        if (store.pendingMode() != null) {
            val snapshot = store.rollbackSnapshot()
            if (snapshot == null) resetToDefault() else restore(snapshot)
        }
        when (activeMode()) {
            CoverMode.CALCULATOR -> if (!isCalculatorReady()) resetToDefault()
            CoverMode.CALENDAR -> if (!isCalendarReady()) resetToDefault()
            CoverMode.NOTES -> if (!isNotesReady()) resetToDefault()
            CoverMode.GALLERY -> if (!isGalleryReady()) resetToDefault()
            CoverMode.DEFAULT -> {
                if (launcher.selectedStyle() in setOf(
                        LauncherStyle.CALCULATOR,
                        LauncherStyle.CALENDAR,
                        LauncherStyle.NOTES,
                        LauncherStyle.GALLERY,
                    )
                ) {
                    resetToDefault()
                }
            }
        }
        clearInactiveAccess()
    }

    private fun accessSnapshot(name: String): JSONObject = JSONObject().apply {
        appContext.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
            if (value is String) put(key, value)
        }
    }

    private fun restoreAccess(name: String, snapshot: JSONObject) {
        val editor = appContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        snapshot.keys().forEach { key -> editor.putString(key, snapshot.getString(key)) }
        check(editor.commit()) { "Unable to restore cover access configuration" }
    }

    private fun restore(snapshot: JSONObject) {
        restoreAccess(CalculatorAccessCodeManager.PREFERENCES_NAME, snapshot.getJSONObject("calculator"))
        restoreAccess(CalendarAccessManager.PREFERENCES_NAME, snapshot.getJSONObject("calendar"))
        restoreAccess(
            NotesAccessManager.PREFERENCES_NAME,
            snapshot.optJSONObject("notes") ?: JSONObject(),
        )
        restoreAccess(
            GalleryAccessManager.PREFERENCES_NAME,
            snapshot.optJSONObject("gallery") ?: JSONObject(),
        )
        launcher.setStyle(LauncherStyle.valueOf(snapshot.getString("style")))
        if (snapshot.getBoolean("hidden")) launcher.hide()
        store.setActiveMode(CoverMode.valueOf(snapshot.getString("mode")))
        store.clearPending()
        clearInactiveAccess()
    }

    private fun clearInactiveAccess() {
        if (activeMode() != CoverMode.CALCULATOR) calculatorAccess.clearCode()
        if (activeMode() != CoverMode.CALENDAR) calendarAccess.clear()
        if (activeMode() != CoverMode.NOTES) notesAccess.clear()
        galleryAccess.clear() // Gallery uses emergency recovery only; remove legacy credentials on upgrade.
    }
}
