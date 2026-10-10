package com.pulse.bluetoothdisable.cover

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.pulse.bluetoothdisable.MainActivity
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.cover.calculator.CalculatorCoverActivity
import com.pulse.bluetoothdisable.cover.calendar.CalendarCoverActivity
import com.pulse.bluetoothdisable.launcher.LauncherIconController
import com.pulse.bluetoothdisable.launcher.LauncherStyle
import com.pulse.bluetoothdisable.testing.pressSystemBack
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoverModeNavigationInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var context: Context
    private lateinit var manager: CoverModeManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = CoverModeManager(context)
        manager.resetToDefault()
    }

    @After
    fun tearDown() {
        manager.resetToDefault()
    }

    @Test
    fun launcherAliasesRouteToExpectedActivities() {
        assertEquals(
            MainActivity::class.java.name,
            aliasTarget(".LauncherAlias"),
        )
        assertEquals(
            CalculatorCoverActivity::class.java.name,
            aliasTarget(".LauncherAliasCalculator"),
        )
        assertEquals(
            CalendarCoverActivity::class.java.name,
            aliasTarget(".LauncherAliasCalendar"),
        )
    }

    @Test
    fun calculatorActivationSwitchesLauncherAtomically() {
        manager.activateCalculator("58317")

        assertEquals(CoverMode.CALCULATOR, manager.activeMode())
        assertEquals(
            LauncherStyle.CALCULATOR,
            LauncherIconController(context).selectedStyle(),
        )
        assertTrue(manager.isCalculatorReady())
    }

    @Test
    fun ordinaryMainLaunchHasNoCoverOrigin() {
        val ordinaryIntent = Intent(context, MainActivity::class.java)
        assertNull(CoverModeNavigator.coverOrigin(context, ordinaryIntent))
    }

    @Test
    fun coverUnlockMarksOriginAndHideDoesNotRevealMainOnBack() {
        manager.activateCalculator("58317")
        val coverIntent = Intent(context, CalculatorCoverActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<CalculatorCoverActivity>(coverIntent).use { scenario ->
            scenario.onActivity { activity ->
                CoverModeNavigator.openMainFromCover(activity, CoverMode.CALCULATOR)
            }
            waitForIdle()

            val mainActivity = awaitResumedActivity(MainActivity::class.java)
            assertEquals(
                CoverMode.CALCULATOR,
                CoverModeNavigator.coverOrigin(mainActivity, mainActivity.intent),
            )

            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                CoverModeNavigator.hideToCoverMode(mainActivity, CoverMode.CALCULATOR)
            }
            waitForIdle()

            awaitResumedActivity(CalculatorCoverActivity::class.java)
            assertTrue(resumedActivities().any { it is CalculatorCoverActivity })
            assertFalse(resumedActivities().any { it is MainActivity })

            pressSystemBack()
            waitForIdle()

            assertFalse(resumedActivities().any { it is MainActivity })
        }
    }

    @Test
    fun calculatorEmergencyResetStartsDefaultMainAndBackCannotReturnToCalculator() {
        manager.activateCalculator("58317")
        val coverIntent = Intent(context, CalculatorCoverActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ActivityScenario.launch<CalculatorCoverActivity>(coverIntent).use { scenario ->
            scenario.onActivity { activity ->
                val recovery = com.pulse.bluetoothdisable.cover.CoverRecoveryManager(
                    manager::activeMode, manager::resetCalculatorCover,
                )
                recovery.authenticationSucceeded(recovery.begin()!!)
                assertTrue(recovery.confirmReset())
                CoverModeNavigator.openDefaultMain(activity)
            }
            val main = awaitResumedActivity(MainActivity::class.java)
            assertNull(CoverModeNavigator.coverOrigin(main, main.intent))
            compose.onNodeWithText(main.getString(R.string.action_hide_to_cover)).assertDoesNotExist()
            assertTrue(LauncherIconController(context).isExclusivelyEnabled(LauncherStyle.DEFAULT))
            assertFalse(resumedActivities().any { it is CalculatorCoverActivity })
            pressSystemBack()
            waitForIdle()
            assertFalse(resumedActivities().any { it is CalculatorCoverActivity })
        }
    }

    @Test
    fun calendarEmergencyResetStartsDefaultMainAndBackCannotReturnToCalendar() {
        manager.activateCalendar(java.time.LocalDate.of(2024, 2, 29), "Calendar access")
        val notes = com.pulse.bluetoothdisable.cover.calendar.LocalCalendarNotesRepository(context)
        val note = notes.save(java.time.LocalDate.of(2024, 2, 29), "Regular calendar note")
        val coverIntent = Intent(context, CalendarCoverActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ActivityScenario.launch<CalendarCoverActivity>(coverIntent).use { scenario ->
            scenario.onActivity { activity ->
                val recovery = com.pulse.bluetoothdisable.cover.CoverRecoveryManager(
                    manager::activeMode, manager::resetCalendarCover, modeToRecover = CoverMode.CALENDAR,
                )
                recovery.authenticationSucceeded(recovery.begin()!!)
                assertTrue(recovery.confirmReset())
                CoverModeNavigator.openDefaultMain(activity)
            }
            val main = awaitResumedActivity(MainActivity::class.java)
            assertNull(CoverModeNavigator.coverOrigin(main, main.intent))
            compose.onNodeWithText(main.getString(R.string.action_hide_to_cover)).assertDoesNotExist()
            assertTrue(LauncherIconController(context).isExclusivelyEnabled(LauncherStyle.DEFAULT))
            assertEquals(note, notes.notes().single())
            assertFalse(resumedActivities().any { it is CalendarCoverActivity })
            pressSystemBack()
            waitForIdle()
            assertFalse(resumedActivities().any { it is CalendarCoverActivity })
        }
    }

    @Suppress("DEPRECATION")
    private fun aliasTarget(aliasSuffix: String): String? {
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, "${context.packageName}$aliasSuffix"),
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        return info.targetActivity
    }

    // Waiting for a drained main looper does not wait for ActivityTaskManager to launch
    // another activity. Observe the actual lifecycle state before checking navigation.
    private fun <T : Activity> awaitResumedActivity(type: Class<T>): T {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            val activity = resumedActivities().firstOrNull { type.isInstance(it) }
            if (activity != null) return type.cast(activity)!!
            SystemClock.sleep(50)
        }
        throw AssertionError("Activity did not resume: ${type.name}")
    }

    private fun waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun resumedActivities(): Collection<Activity> {
        val result = AtomicReference<Collection<Activity>>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result.set(
                ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .toList(),
            )
        }
        return result.get()
    }
}
