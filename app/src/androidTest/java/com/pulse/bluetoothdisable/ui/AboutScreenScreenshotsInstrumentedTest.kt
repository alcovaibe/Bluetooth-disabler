package com.pulse.bluetoothdisable.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.pulse.bluetoothdisable.BuildConfig
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.domain.ProtectionState
import com.pulse.bluetoothdisable.launcher.LauncherStyle
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.theme.ThemeManager
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Captures screenshots from the real running Compose UI on an Android emulator.
 * These files are gathered by scripts/run_android_tests.sh and uploaded as CI artifacts.
 */
class AboutScreenScreenshotsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureFiveRealAppScreens() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(application.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag("ru")))
        }
        val localized = application.createConfigurationContext(configuration)
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides configuration,
                LocalResources provides localized.resources,
            ) {
                BluetoothDisableTheme(darkTheme = true) {
                    MainScreen(
                        uiState = ProtectionUiState(ProtectionState.READY, isDeviceOwner = true),
                        appVersion = BuildConfig.VERSION_NAME,
                        launcherIconHidden = false,
                        selectedLauncherStyle = LauncherStyle.DEFAULT,
                        tileAdded = true,
                        canRequestTile = true,
                        selectedLanguage = LanguageManager.RUSSIAN,
                        selectedTheme = ThemeManager.DARK,
                        showHideAction = false,
                        onHideToCover = {},
                        onLanguageSelected = {},
                        onThemeSelected = {},
                        onEnableProtection = {},
                        onDisableProtection = {},
                        onRefresh = {},
                        onLauncherStyleSelected = {},
                        onRequestAddTile = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("main_screen").assertIsDisplayed()
        capture("01-main-screen.png")

        fun openMenu() {
            compose.onNodeWithContentDescription(localized.getString(R.string.navigation_menu_open))
                .performClick()
            compose.waitUntil(10_000) {
                runCatching { compose.onNodeWithTag("drawer_theme").assertIsDisplayed() }.isSuccess
            }
        }

        openMenu()
        capture("02-navigation-drawer.png")

        compose.onNodeWithTag("drawer_language").performClick()
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithTag("language_selection_sheet").assertIsDisplayed()
            }.isSuccess
        }
        capture("03-language-bottom-sheet.png")
        compose.onNodeWithTag("language_selection_sheet_option_ru").performClick()
        compose.waitForIdle()

        openMenu()
        compose.onNodeWithTag("drawer_theme").performClick()
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithTag("theme_selection_sheet").assertIsDisplayed() }.isSuccess
        }
        capture("04-theme-bottom-sheet.png")
        compose.onNodeWithTag("theme_selection_sheet_option_dark").performClick()
        compose.waitForIdle()

        openMenu()
        compose.onNodeWithTag("drawer_about").performClick()
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithTag("about_app_screen").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithTag("about_version").assertIsDisplayed()
        capture("05-about-app.png")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val dir = "/data/local/tmp/about-previews"
        fun shell(command: String) =
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        shell("mkdir -p $dir")
        shell("screencap -p $dir/$name")
        assertTrue("Missing emulator screenshot: $name", shell("test -s $dir/$name && echo ok").contains("ok"))
    }
}
