package com.pulse.bluetoothdisable.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.pulse.bluetoothdisable.MainActivity
import com.pulse.bluetoothdisable.cover.CoverModeManager
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.ui.tour.MainTourKind
import com.pulse.bluetoothdisable.ui.tour.MainTourStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainTourLifecycleInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private fun clear() { app.getSharedPreferences(MainTourStore.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit() }
    @Before fun before() {
        CoverModeManager(app).resetToDefault()
        LanguageManager.setSelectedLanguage(app, LanguageManager.ENGLISH)
        clear()
    }
    @After fun after() { MainTourStore(app).claim(100, 100) }

    @Test fun claimingPersistsAcrossStoresAndFutureVersionsForBothInstallPaths() {
        assertEquals(MainTourKind.UPDATES, MainTourStore(app).claim(100, 100))
        assertEquals(MainTourKind.NONE, MainTourStore(app).claim(100, 200))
        clear()
        assertEquals(MainTourKind.FULL, MainTourStore(app).claim(100, 200))
        assertEquals(MainTourKind.NONE, MainTourStore(app).claim(100, 999))
    }

    @Test fun backDismissesTheTipWithoutRepeatingItOnTheNextLaunch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithTag("main_tour_target_UPDATES").assertIsDisplayed()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("main_tour").assertDoesNotExist()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithTag("main_tour").assertDoesNotExist()
        }
    }

    @Test fun freshInstallTipSurvivesRotationButSkippingNeverRepeatsIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithTag("main_tour_target_UPDATES").assertIsDisplayed()
            compose.onNodeWithTag("main_tour_target_THEME").assertDoesNotExist()
            scenario.recreate()
            compose.onNodeWithTag("main_tour_target_UPDATES").assertIsDisplayed()
            compose.onNodeWithTag("main_tour_skip").performClick()
            compose.onNodeWithTag("main_tour").assertDoesNotExist()
            scenario.recreate()
            compose.onNodeWithTag("main_tour").assertDoesNotExist()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithTag("main_tour").assertDoesNotExist()
        }
    }
}
