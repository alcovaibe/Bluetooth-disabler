package com.pulse.bluetoothdisable.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.domain.ProtectionState
import com.pulse.bluetoothdisable.launcher.LauncherStyle
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.theme.ThemeManager
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class MainNavigationDrawerInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    // Material3 IconButton exposes the accessible description in merged semantics.
    // The same node also carries the onboarding target tag.
    private fun menuButton() = compose.onNodeWithContentDescription(
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.navigation_menu_open),
    )

    @Test
    fun drawerOpensAndThemeSelectionRunsAfterDrawerCloses() {
        val selectedTheme = mutableStateOf(ThemeManager.LIGHT)
        var themeChanges = 0
        compose.setContent {
            BluetoothDisableTheme(darkTheme = selectedTheme.value == ThemeManager.DARK) {
                MainScreen(
                    uiState = ProtectionUiState(ProtectionState.READY, isDeviceOwner = true),
                    appVersion = "1.4.2",
                    launcherIconHidden = false,
                    selectedLauncherStyle = LauncherStyle.DEFAULT,
                    tileAdded = true,
                    canRequestTile = true,
                    selectedLanguage = LanguageManager.ENGLISH,
                    selectedTheme = selectedTheme.value,
                    showHideAction = false,
                    onHideToCover = {},
                    onLanguageSelected = {},
                    onThemeSelected = {
                        themeChanges++
                        selectedTheme.value = it
                    },
                    onEnableProtection = {},
                    onDisableProtection = {},
                    onRefresh = {},
                    onLauncherStyleSelected = {},
                    onRequestAddTile = {},
                )
            }
        }

        menuButton().assertIsDisplayed().performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("drawer_theme").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithTag("drawer_theme").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("theme_selection_sheet").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithTag("theme_selection_sheet_option_light").assertIsSelected().performClick()
        assertEquals(0, themeChanges)

        menuButton().performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("drawer_theme").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithTag("drawer_theme").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("theme_selection_sheet_option_dark").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithTag("theme_selection_sheet_option_dark").performClick()
        compose.waitForIdle()
        assertEquals(1, themeChanges)
        assertEquals(ThemeManager.DARK, selectedTheme.value)
    }

    @Test
    fun drawerShowsAboutAndLanguageSelection() {
        var selectedLanguage = LanguageManager.ENGLISH
        compose.setContent {
            BluetoothDisableTheme {
                MainScreen(
                    uiState = ProtectionUiState(ProtectionState.NOT_PROVISIONED),
                    appVersion = "1.4.2",
                    launcherIconHidden = false,
                    selectedLauncherStyle = LauncherStyle.DEFAULT,
                    tileAdded = true,
                    canRequestTile = false,
                    selectedLanguage = selectedLanguage,
                    selectedTheme = ThemeManager.LIGHT,
                    showHideAction = false,
                    onHideToCover = {},
                    onLanguageSelected = { selectedLanguage = it },
                    onThemeSelected = {},
                    onEnableProtection = {},
                    onDisableProtection = {},
                    onRefresh = {},
                    onLauncherStyleSelected = {},
                    onRequestAddTile = {},
                )
            }
        }

        menuButton().assertIsDisplayed()
        menuButton().performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("drawer_language").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithTag("main_navigation_drawer").assertIsDisplayed()
        compose.onNodeWithText("Disable").assertIsDisplayed()
        compose.onNodeWithTag("drawer_theme").assertIsDisplayed()
        compose.onNodeWithTag("drawer_language").assertIsDisplayed()
        compose.onNodeWithTag("drawer_about").assertIsDisplayed()
        compose.onNodeWithTag("drawer_privacy").assertDoesNotExist()
        compose.onNodeWithTag("drawer_version").assertDoesNotExist()
        val themeY = compose.onNodeWithTag("drawer_theme").fetchSemanticsNode().boundsInRoot.top
        val languageY = compose.onNodeWithTag("drawer_language").fetchSemanticsNode().boundsInRoot.top
        val aboutY = compose.onNodeWithTag("drawer_about").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Drawer entries must be theme, language, about", themeY < languageY && languageY < aboutY)
        compose.onNodeWithTag("drawer_language").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("language_selection_sheet").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithTag("language_selection_sheet_option_en").assertIsSelected()
        compose.onNodeWithTag("language_selection_sheet_option_ru").performClick()
        compose.waitForIdle()
        assertEquals(LanguageManager.RUSSIAN, selectedLanguage)

        menuButton().performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("drawer_about").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithTag("drawer_about").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("about_app_version").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithTag("about_app_version").assertTextEquals("Version 1.4.2")
        compose.onNodeWithTag("about_licenses").assertIsDisplayed().performClick()
        compose.onNodeWithTag("license_app_item").assertIsDisplayed()
        compose.onNodeWithTag("license_components_item").assertIsDisplayed().performClick()
        compose.onNodeWithTag("about_component_0").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("about_license_full_text").assertExists() }.isSuccess
        }
        compose.onNodeWithTag("about_back").performClick()
        compose.onNodeWithTag("about_component_0").assertIsDisplayed()
    }
}
