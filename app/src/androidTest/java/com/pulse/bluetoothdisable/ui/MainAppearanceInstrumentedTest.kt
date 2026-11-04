package com.pulse.bluetoothdisable.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.pulse.bluetoothdisable.domain.ProtectionState
import com.pulse.bluetoothdisable.launcher.LauncherStyle
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.theme.ThemeManager
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainAppearanceInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun themeAndLanguageSheetsUseExistingSelectionCallbacks() {
        val theme = mutableStateOf(ThemeManager.LIGHT)
        val language = mutableStateOf(LanguageManager.ENGLISH)
        compose.setContent {
            BluetoothDisableTheme(darkTheme = theme.value == ThemeManager.DARK) {
                MainScreen(
                    uiState = ProtectionUiState(
                        state = ProtectionState.READY,
                        isDeviceOwner = false,
                    ),
                    appVersion = "1.4.1",
                    launcherIconHidden = false,
                    selectedLauncherStyle = LauncherStyle.DEFAULT,
                    tileAdded = true,
                    canRequestTile = false,
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

        compose.onNodeWithTag("main_theme_row").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("main_theme_dark_option").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(ThemeManager.DARK, theme.value) }
        compose.onNodeWithText("Dark").assertIsDisplayed()

        compose.onNodeWithTag("main_language_row").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("main_language_ru_option").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(LanguageManager.RUSSIAN, language.value) }
        compose.onNodeWithText("Русский").assertIsDisplayed()

        // Both old top-bar selectors must be absent after the redesign.
        compose.onNodeWithText("RU").assertDoesNotExist()
        compose.onNodeWithText("EN").assertDoesNotExist()
    }
}
