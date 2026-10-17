package com.pulse.bluetoothdisable.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.domain.ProtectionState
import com.pulse.bluetoothdisable.launcher.LauncherStyle
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.theme.ThemeManager
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme
import com.pulse.bluetoothdisable.ui.tour.MainTourKind
import com.pulse.bluetoothdisable.updates.UpdateIcon
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainTourInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Context>()

    private fun verifyFullTour(language: String, dark: Boolean, owner: Boolean = true, cover: Boolean = true, tileAdded: Boolean = false, fontScale: Float = 1f) {
        val kind = mutableStateOf(MainTourKind.FULL)
        var actions = 0
        val configuration = Configuration(app.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(language))); this.fontScale = fontScale }
        val localized = app.createConfigurationContext(configuration)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration,
                LocalResources provides localized.resources) {
                BluetoothDisableTheme(darkTheme = dark) {
                    MainScreen(
                        uiState = ProtectionUiState(if (owner) ProtectionState.READY else ProtectionState.NOT_PROVISIONED, isDeviceOwner = owner),
                        appVersion = "1.2.6-internal", launcherIconHidden = false,
                        selectedLauncherStyle = LauncherStyle.DEFAULT, tileAdded = tileAdded, canRequestTile = true,
                        selectedLanguage = language, selectedTheme = if (dark) ThemeManager.DARK else ThemeManager.LIGHT,
                        showHideAction = cover, onHideToCover = { actions++ },
                        onLanguageSelected = { actions++ }, onThemeSelected = { actions++ },
                        onEnableProtection = { actions++ }, onDisableProtection = { actions++ }, onRefresh = { actions++ },
                        onLauncherStyleSelected = { actions++ }, onRequestAddTile = { actions++ },
                        updateControls = {
                            IconButton(onClick = { actions++ }, modifier = Modifier.size(56.dp).testTag("check_updates")) {
                                Icon(UpdateIcon, localized.getString(R.string.updates_check))
                            }
                        },
                        tourKind = kind.value, onTourDismiss = { kind.value = MainTourKind.NONE },
                    )
                }
            }
        }
        val targets = buildList {
            addAll(listOf("THEME", "LANGUAGE", "PROTECTION"))
            if (cover) add("COVER")
            if (!tileAdded) add("TILE")
            if (owner) add("LAUNCHER")
            add("UPDATES")
        }
        for (target in targets) {
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("main_tour_target_$target").fetchSemanticsNodes().isNotEmpty() }
            val node = compose.onNodeWithTag("main_tour_target_$target")
            node.assertIsDisplayed()
            if (target == "THEME" || target == "UPDATES") capture("main-tour-${target.lowercase()}-$language-${if (owner) "configured" else "setup"}.png")
            node.performTouchInput { click() }
        }
        compose.onNodeWithTag("main_tour").assertDoesNotExist()
        assertEquals(0, actions)
        val button = compose.onNodeWithTag("check_updates").getUnclippedBoundsInRoot()
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue(button.left > screen.width / 2)
        assertTrue(button.top > screen.height / 2)
        compose.onNodeWithTag("check_updates").performClick()
        assertEquals(1, actions)
    }

    @Test fun englishLightTourOnlyAdvancesAndNeverExecutesTheHighlightedAction() = verifyFullTour("en", false)
    @Test fun russianDarkTourOnlyAdvancesAndNeverExecutesTheHighlightedAction() = verifyFullTour("ru", true)

    @Test fun unconfiguredDeviceWithLargeTextSkipsUnavailableControls() =
        verifyFullTour("ru", false, owner = false, cover = false, tileAdded = true, fontScale = 1.4f)

    private fun capture(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (command in listOf("mkdir -p /data/local/tmp/calendar-previews", "screencap -p /data/local/tmp/calendar-previews/$name")) {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
    }
}
