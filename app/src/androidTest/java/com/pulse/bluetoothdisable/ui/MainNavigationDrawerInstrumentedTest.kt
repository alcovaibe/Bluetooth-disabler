package com.pulse.bluetoothdisable.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pulse.bluetoothdisable.domain.ProtectionState
import com.pulse.bluetoothdisable.launcher.LauncherStyle
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.theme.ThemeManager
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Regression tests for the drawer-to-five-tab migration. */
class MainNavigationDrawerInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun floatingNavigationShowsFiveItemsAndNoDrawerOrLauncherButton() {
        var updateChecks = 0
        compose.setContent {
            BluetoothDisableTheme {
                MainScreen(
                    uiState = ProtectionUiState(ProtectionState.READY, isDeviceOwner = true),
                    appVersion = "1.4.2",
                    launcherIconHidden = false,
                    selectedLauncherStyle = LauncherStyle.DEFAULT,
                    tileAdded = true,
                    canRequestTile = true,
                    selectedLanguage = LanguageManager.ENGLISH,
                    selectedTheme = ThemeManager.LIGHT,
                    showHideAction = false,
                    onHideToCover = {},
                    onLanguageSelected = {},
                    onThemeSelected = {},
                    onEnableProtection = {},
                    onDisableProtection = {},
                    onRefresh = {},
                    onLauncherStyleSelected = {},
                    onRequestAddTile = {},
                    onCheckUpdates = { updateChecks++ },
                )
            }
        }
        compose.onNodeWithTag("floating_bottom_navigation").assertIsDisplayed()
        for (item in listOf("bottom_nav_theme", "bottom_nav_language", "bottom_nav_about",
            "bottom_nav_cover_mode", "check_updates")) {
            compose.onNodeWithTag(item).assertIsDisplayed()
        }
        compose.onNodeWithTag("open_navigation_drawer").assertDoesNotExist()
        compose.onNodeWithTag("main_navigation_drawer").assertDoesNotExist()
        compose.onNodeWithTag("check_updates").performClick()
        compose.onNodeWithTag("check_updates").assertIsSelected()
        assertEquals(1, updateChecks)
        compose.onNodeWithTag("bottom_nav_cover_mode").performClick()
        compose.onNodeWithText("Change app icon").assertIsDisplayed()
        compose.onNodeWithText("CANCEL").performClick()
        compose.onNodeWithTag("bottom_nav_cover_mode").assertIsSelected()
        compose.onNodeWithTag("bottom_nav_theme").assertIsDisplayed()
    }

    @Test fun themeLanguageAndAboutAreReachedThroughBottomBar() {
        val theme = mutableStateOf(ThemeManager.LIGHT)
        val language = mutableStateOf(LanguageManager.ENGLISH)
        compose.setContent {
            BluetoothDisableTheme {
                MainScreen(
                    uiState = ProtectionUiState(ProtectionState.READY, isDeviceOwner = true),
                    appVersion = "1.4.2",
                    launcherIconHidden = false,
                    selectedLauncherStyle = LauncherStyle.DEFAULT,
                    tileAdded = true,
                    canRequestTile = true,
                    selectedLanguage = language.value,
                    selectedTheme = theme.value,
                    showHideAction = false,
                    onHideToCover = {},
                    onLanguageSelected = { language.value = it },
                    onThemeSelected = { theme.value = it },
                    onEnableProtection = {},
                    onDisableProtection = {},
                    onRefresh = {},
                    onLauncherStyleSelected = {},
                    onRequestAddTile = {},
                )
            }
        }
        compose.onNodeWithTag("bottom_nav_theme").performClick()
        compose.onNodeWithTag("theme_selection_sheet").assertIsDisplayed()
        compose.onNodeWithTag("theme_selection_sheet_option_dark").performClick()
        assertEquals(ThemeManager.DARK, theme.value)

        compose.onNodeWithTag("bottom_nav_language").performClick()
        compose.onNodeWithTag("language_selection_sheet").assertIsDisplayed()
        compose.onNodeWithTag("language_selection_sheet_option_ru").performClick()
        assertEquals(LanguageManager.RUSSIAN, language.value)

        compose.onNodeWithTag("bottom_nav_about").performClick()
        compose.onNodeWithTag("about_app_screen").assertIsDisplayed()
        compose.onNodeWithTag("about_version").assertIsDisplayed()
        compose.onNodeWithTag("about_back").performClick()
        compose.onNodeWithTag("bottom_nav_about").assertIsSelected()
    }
}
